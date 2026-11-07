package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.ExternalServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.externaltask.ExternalTaskQueryBuilder
import org.cibseven.bpm.engine.externaltask.LockedExternalTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * Delivers external tasks to subscriptions.
 * This implementation uses internal Java API and pulls tasks for delivery.
 */
class EmbeddedPullServiceTaskDelivery(
  private val externalTaskService: ExternalTaskService,
  private val workerId: String,
  private val subscriptionRepository: SubscriptionRepository,
  private val maxTasks: Int,
  private val lockDurationInSeconds: Long,
  private val retryTimeoutInSeconds: Long,
  private val retries: Int,
  private val executorService: ThreadPoolExecutor,
) : ExternalServiceTaskDelivery, RefreshableDelivery {

  private val runningTaskIds = ConcurrentHashMap.newKeySet<String>()

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   * Fetches only as many tasks as worker threads are free and never waits for a delivered task to finish.
   */
  override fun refresh() {

    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isEmpty()) {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-035: Pull external tasks disabled because of no active subscriptions" }
      return
    }

    terminateTasksGoneFromEngine()

    val freeWorkerThreads = executorService.maximumPoolSize - runningTaskIds.size
    val tasksToFetch = maxTasks.coerceAtMost(freeWorkerThreads)
    if (tasksToFetch <= 0) {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-045: No free worker thread, skipping task fetch" }
      return
    }

    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-030: pulling service tasks for subscriptions: $subscriptions" }
    externalTaskService
      .fetchAndLock(tasksToFetch, workerId)
      .forSubscriptions(subscriptions)
      .execute()
      .forEach { lockedTask ->
        val activeSubscription = subscriptions.firstOrNull { subscription -> subscription.matches(lockedTask) }
        if (activeSubscription == null) {
          logger.warn { "PROCESS-ENGINE-CIB7-EMBEDDED-044: no subscription found for fetched task ${lockedTask.id} with topic '${lockedTask.topicName}'." }
        } else {
          submitUnlessRunning(lockedTask, activeSubscription)
        }
      }
  }

  private fun submitUnlessRunning(lockedTask: LockedExternalTask, activeSubscription: TaskSubscriptionHandle) {
    val taskId = lockedTask.id
    if (!runningTaskIds.add(taskId)) {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-046: skipping task $taskId since it is still running." }
      return
    }
    try {
      executorService.submit {
        try {
          deliver(lockedTask, activeSubscription)
        } finally {
          runningTaskIds.remove(taskId)
        }
      }
    } catch (e: RejectedExecutionException) {
      runningTaskIds.remove(taskId)
      throw e
    }
  }

  private fun deliver(lockedTask: LockedExternalTask, activeSubscription: TaskSubscriptionHandle) {
    try {
      val isAlreadyDeliveredToSubscription = subscriptionRepository.getActiveSubscriptionForTask(lockedTask.id) == activeSubscription
      if (isAlreadyDeliveredToSubscription) {
        logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-041: skipping task ${lockedTask.id} since it is unchanged." }
        return
      }
      val taskInformation = lockedTask.toTaskInformation().withReason(TaskInformation.CREATE)
      subscriptionRepository.activateSubscriptionForTask(lockedTask.id, activeSubscription)
      val variables = lockedTask.variables.filterBySubscription(activeSubscription)
      logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-031: delivering service task ${lockedTask.id}." }
      withThreadContextClassLoader(activeSubscription.action) {
        activeSubscription.action.accept(taskInformation, variables)
      }
      logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-032: successfully delivered service task ${lockedTask.id}." }
    } catch (e: Exception) {
      val jobRetries: Int = lockedTask.retries ?: retries
      logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-033: failing delivering task ${lockedTask.id}: ${e.message}" }
      externalTaskService.handleFailure(lockedTask.id, workerId, e.message, jobRetries - 1, retryTimeoutInSeconds * 1000)
      subscriptionRepository.deactivateSubscriptionForTask(taskId = lockedTask.id)
      logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-034: successfully failed delivering task ${lockedTask.id}: ${e.message}" }
    }
  }

  private fun terminateTasksGoneFromEngine() {
    val idleDeliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL).toSet() - runningTaskIds
    if (idleDeliveredTaskIds.isEmpty()) {
      return
    }
    val taskIdsInEngine = externalTaskService
      .createExternalTaskQuery()
      .externalTaskIdIn(idleDeliveredTaskIds)
      .list()
      .map { it.id }
      .toSet()
    (idleDeliveredTaskIds - taskIdsInEngine).forEach { taskId ->
      executorService.submit { terminate(taskId) }
    }
  }

  private fun terminate(taskId: String) {
    subscriptionRepository.deactivateSubscriptionForTask(taskId)?.let { subscription ->
      withThreadContextClassLoader(subscription.termination) {
        subscription.termination.accept(
          TaskInformation(
            taskId,
            emptyMap()
          ).withReason(TaskInformation.DELETE)
        )
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
