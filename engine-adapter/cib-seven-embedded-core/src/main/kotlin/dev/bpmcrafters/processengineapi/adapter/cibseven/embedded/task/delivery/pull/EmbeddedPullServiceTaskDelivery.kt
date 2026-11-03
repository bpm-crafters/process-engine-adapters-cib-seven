package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.ExternalServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.externaltask.ExternalTaskQueryBuilder
import org.cibseven.bpm.engine.externaltask.LockedExternalTask

private val logger = KotlinLogging.logger {}

private const val EXISTENCE_QUERY_CHUNK_SIZE = 500

/**
 * Delivers external tasks to subscriptions.
 * This implementation uses internal Java API and pulls tasks for delivery.
 * It fetches at most as many tasks as the [workerPool] has free slots and never waits for their execution.
 */
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class EmbeddedPullServiceTaskDelivery(
  private val externalTaskService: ExternalTaskService,
  private val workerId: String,
  private val subscriptionRepository: SubscriptionRepository,
  private val lockDurationInSeconds: Long,
  private val retryTimeoutInSeconds: Long,
  private val retries: Int,
  private val workerPool: ServiceTaskWorkerPool,
  private val outcomeRecorder: TaskOutcomeRecorder,
  private val decoration: TaskExecutionDecoration,
) : ExternalServiceTaskDelivery, RefreshableDelivery {

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {
    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isEmpty()) {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-035: Pull external tasks disabled because of no active subscriptions" }
      return
    }
    terminateGoneTasks()
    deliverNewTasks(subscriptions)
  }

  private fun deliverNewTasks(subscriptions: List<TaskSubscriptionHandle>) {
    workerPool.reserve().use { reservation ->
      val reservedSlots = reservation.slots
      if (reservedSlots == 0) {
        logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-045: no free worker capacity, skipping pull of service tasks." }
        return
      }
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-030: pulling $reservedSlots service tasks for subscriptions: $subscriptions" }
      val lockedTasks = fetchAndLockTasks(reservedSlots, subscriptions)
      lockedTasks.forEach { lockedTask -> deliver(lockedTask, subscriptions, reservation) }
    }
  }

  private fun fetchAndLockTasks(maxTasks: Int, subscriptions: List<TaskSubscriptionHandle>): List<LockedExternalTask> =
    externalTaskService
      .fetchAndLock(maxTasks, workerId)
      .forSubscriptions(subscriptions)
      .execute()

  private fun deliver(lockedTask: LockedExternalTask, subscriptions: List<TaskSubscriptionHandle>, reservation: ServiceTaskWorkerPool.Reservation) {
    val matchingSubscription = subscriptions.firstOrNull { subscription -> subscription.matches(lockedTask) }
    if (matchingSubscription == null) {
      logger.warn { "PROCESS-ENGINE-CIB7-EMBEDDED-044: no subscription found for fetched task ${lockedTask.id} with topic '${lockedTask.topicName}'." }
      return
    }
    val isAlreadyDelivered = subscriptionRepository.getActiveSubscriptionForTask(lockedTask.id) == matchingSubscription
    if (isAlreadyDelivered) {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-041: skipping task ${lockedTask.id} since it is unchanged." }
      return
    }
    val isSubmitted = reservation.submit(lockedTask.id) { execute(lockedTask, matchingSubscription) }
    if (!isSubmitted) {
      logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-046: task ${lockedTask.id} was not submitted, it is still running or was rejected." }
    }
  }

  private fun execute(lockedTask: LockedExternalTask, subscription: TaskSubscriptionHandle) {
    val taskInformation = lockedTask.toTaskInformation().withReason(TaskInformation.CREATE)
    val variables = lockedTask.variables.filterBySubscription(subscription)
    decoration.run(taskInformation, variables) {
      outcomeRecorder.track(lockedTask.id) { invokeHandlerAndHandleFailure(lockedTask, subscription, taskInformation, variables) }
    }
  }

  private fun invokeHandlerAndHandleFailure(
    lockedTask: LockedExternalTask,
    subscription: TaskSubscriptionHandle,
    taskInformation: TaskInformation,
    variables: Map<String, Any?>,
  ) {
    try {
      invokeHandler(lockedTask.id, subscription, taskInformation, variables)
    } catch (e: Exception) {
      reportFailureUnlessOutcomeReported(lockedTask, e)
    }
  }

  private fun invokeHandler(
    taskId: String,
    subscription: TaskSubscriptionHandle,
    taskInformation: TaskInformation,
    variables: Map<String, Any?>,
  ) {
    subscriptionRepository.activateSubscriptionForTask(taskId, subscription)
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-031: delivering service task $taskId." }
    withThreadContextClassLoader(subscription.action) {
      subscription.action.accept(taskInformation, variables)
    }
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-032: successfully delivered service task $taskId." }
  }

  private fun reportFailureUnlessOutcomeReported(lockedTask: LockedExternalTask, handlerFailure: Exception) {
    val isOutcomeAlreadyReported = outcomeRecorder.hasOutcome(lockedTask.id)
    if (isOutcomeAlreadyReported) {
      logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-047: handler of task ${lockedTask.id} failed after reporting its outcome: ${handlerFailure.message}" }
    } else {
      reportFailure(lockedTask, handlerFailure)
    }
  }

  private fun reportFailure(lockedTask: LockedExternalTask, handlerFailure: Exception) {
    val remainingRetries = lockedTask.retries?.minus(1) ?: retries
    val retryTimeoutInMillis = retryTimeoutInSeconds * 1000
    logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-033: failing delivering task ${lockedTask.id}: ${handlerFailure.message}" }
    externalTaskService.handleFailure(lockedTask.id, workerId, handlerFailure.message, remainingRetries, retryTimeoutInMillis)
    subscriptionRepository.deactivateSubscriptionForTask(taskId = lockedTask.id)
    val failedOutcome = TaskOutcome.Failed(handlerFailure.message, remainingRetries, handlerFailure)
    outcomeRecorder.record(lockedTask.id, failedOutcome)
    logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-034: successfully failed delivering task ${lockedTask.id}: ${handlerFailure.message}" }
  }

  private fun terminateGoneTasks() {
    val goneTaskIds = findGoneTaskIds()
    if (goneTaskIds.isEmpty()) return
    workerPool.reserve(maxSlots = goneTaskIds.size).use { reservation ->
      val terminableTaskIds = goneTaskIds.take(reservation.slots)
      terminableTaskIds.forEach { taskId -> reservation.submit(taskId) { terminate(taskId) } }
    }
  }

  private fun findGoneTaskIds(): List<String> {
    val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL)
    val notRunningTaskIds = deliveredTaskIds.filterNot(workerPool::isRunning)
    val existingTaskIds = findExistingTaskIds(notRunningTaskIds)
    return notRunningTaskIds.filterNot(existingTaskIds::contains)
  }

  private fun findExistingTaskIds(taskIds: List<String>): Set<String> =
    taskIds
      .chunked(EXISTENCE_QUERY_CHUNK_SIZE)
      .flatMap { taskIdChunk -> queryExistingTaskIds(taskIdChunk) }
      .toSet()

  private fun queryExistingTaskIds(taskIdChunk: List<String>): List<String> =
    externalTaskService
      .createExternalTaskQuery()
      .externalTaskIdIn(taskIdChunk.toSet())
      .list()
      .map { externalTask -> externalTask.id }

  private fun terminate(taskId: String) {
    subscriptionRepository.deactivateSubscriptionForTask(taskId)?.let { subscription ->
      val deletedTask = TaskInformation(taskId, emptyMap()).withReason(TaskInformation.DELETE)
      withThreadContextClassLoader(subscription.termination) {
        subscription.termination.accept(deletedTask)
      }
    }
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-043: deactivating $taskId, task is gone." }
  }

  private fun ExternalTaskQueryBuilder.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): ExternalTaskQueryBuilder {
    subscriptions
      .filter { it.taskDescriptionKey != null }
      .distinctBy { it.taskDescriptionKey }
      .forEach { subscription ->
        val lockDurationInMilliseconds = getLockDurationForSubscription(subscription)
        this
          .topic(subscription.taskDescriptionKey, lockDurationInMilliseconds)
          .enableCustomObjectDeserialization()
        // FIXME -> consider complex tenant filtering
      }
    return this
  }

  internal fun getLockDurationForSubscription(subscription: TaskSubscriptionHandle): Long {
    val customLockDuration = subscription.restrictions[CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS]
    return customLockDuration?.toLong() ?: (lockDurationInSeconds * 1000)
  }
}

internal fun TaskSubscriptionHandle.matches(task: LockedExternalTask): Boolean =
  this.taskType == TaskType.EXTERNAL
    && (this.taskDescriptionKey == null || this.taskDescriptionKey == task.topicName)
    && this.restrictions
      .minus(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS)
      .all { (key, value) ->
        when (key) {
          CommonRestrictions.EXECUTION_ID -> value == task.executionId
          CommonRestrictions.ACTIVITY_ID -> value == task.activityId
          CommonRestrictions.BUSINESS_KEY -> value == task.businessKey
          CommonRestrictions.TENANT_ID -> value == task.tenantId
          CommonRestrictions.PROCESS_INSTANCE_ID -> value == task.processInstanceId
          CommonRestrictions.PROCESS_DEFINITION_KEY -> value == task.processDefinitionKey
          CommonRestrictions.PROCESS_DEFINITION_ID -> value == task.processDefinitionId
          CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG -> value == task.processDefinitionVersionTag
          else -> {
            logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-041: Unknown restriction key: $key" }
            false
          }
        }
      }
