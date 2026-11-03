package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.RefreshableDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.ServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.DropReason.EXPIRED_WHILE_IN_QUEUE
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.DropReason.NO_MATCHING_SUBSCRIPTIONS
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.NO_SUBSCRIPTIONS
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.QUEUE_FULL
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation.Companion.CREATE
import dev.bpmcrafters.processengineapi.task.TaskType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.dto.*
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

private const val EXISTENCE_QUERY_CHUNK_SIZE = 500

/**
 * Delivers external tasks to subscriptions.
 * This implementation uses internal Java API and pulls tasks for delivery.
 */
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class PullServiceTaskDelivery(
  private val externalTaskApi: ExternalTaskApi,
  private val processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  private val workerId: String,
  private val subscriptionRepository: SubscriptionRepository,
  private val lockDurationInSeconds: Long,
  private val retryTimeoutInSeconds: Long,
  private val retries: Int,
  private val workerPool: ServiceTaskWorkerPool,
  private val outcomeRecorder: TaskOutcomeRecorder,
  private val decoration: TaskExecutionDecoration,
  private val valueMapper: ValueMapper,
  private val deserializeOnServer: Boolean,
  private val metrics: PullServiceTaskDeliveryMetrics,
) : ServiceTaskDelivery, RefreshableDelivery {

  internal val stillLockedTasksGauge = AtomicInteger()

  init {
    metrics.registerExecutorThreadsUsedGauge { workerPool.runningTaskCount }
    metrics.registerExecutorQueueCapacityGauge { workerPool.freeSlotCount }
    metrics.registerStillLockedTasksGauge(stillLockedTasksGauge)
  }

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {
    cleanUpTerminatedTasks()
    deliverNewTasks()
  }

  internal fun deliverNewTasks() {
    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isEmpty()) {
      logger.trace { "PROCESS-ENGINE-C7-REMOTE-035: Pull external tasks disabled because of no active subscriptions" }
      metrics.incrementFetchAndLockTasksSkippedCounter(NO_SUBSCRIPTIONS)
      return
    }

    workerPool.reserve().use { reservation ->
      val hasNoFreeSlot = reservation.slots == 0
      if (hasNoFreeSlot) {
        logger.trace { "PROCESS-ENGINE-C7-REMOTE-041: No free worker capacity, skipping task fetch" }
        metrics.incrementFetchAndLockTasksSkippedCounter(QUEUE_FULL)
        return
      }
      fetchAndDeliver(subscriptions, reservation)
    }
  }

  private fun fetchAndDeliver(subscriptions: List<TaskSubscriptionHandle>, reservation: ServiceTaskWorkerPool.Reservation) {
    val lockedTasks = fetchAndLock(subscriptions, maxTasks = reservation.slots)
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-042: pulled ${lockedTasks.size} service tasks" }
    lockedTasks
      .groupBy { it.topicName }
      .forEach { (topic, tasks) -> metrics.incrementFetchedAndLockedTasksCounter(topic!!, tasks.size) }

    lockedTasks.forEach { lockedTask ->
      val matchingSubscription = subscriptions.firstOrNull { subscription -> matches(lockedTask, subscription) }
      if (matchingSubscription != null) {
        submitTask(reservation, lockedTask, matchingSubscription)
      } else {
        metrics.incrementDroppedTasksCounter(lockedTask.topicName!!, NO_MATCHING_SUBSCRIPTIONS)
      }
    }
  }

  private fun fetchAndLock(subscriptions: List<TaskSubscriptionHandle>, maxTasks: Int): List<LockedExternalTaskDto> {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-030: pulling $maxTasks service tasks for subscriptions: $subscriptions" }
    val oldestFirst = FetchExternalTasksDtoSortingInner()
      .sortBy(FetchExternalTasksDtoSortingInner.SortByEnum.CREATE_TIME)
      .sortOrder(FetchExternalTasksDtoSortingInner.SortOrderEnum.ASC)
    val fetchRequest = FetchExternalTasksDto().workerId(workerId).maxTasks(maxTasks)
      .forSubscriptions(subscriptions)
      .usePriority(true)
      .sorting(mutableListOf(oldestFirst))
    return externalTaskApi.fetchAndLock(fetchRequest)
  }

  private fun submitTask(reservation: ServiceTaskWorkerPool.Reservation, lockedTask: LockedExternalTaskDto, subscription: TaskSubscriptionHandle) {
    val isSubmitted = reservation.submit(lockedTask.id!!) { executeTask(lockedTask, subscription) }
    if (!isSubmitted) {
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-047: service task ${lockedTask.id} not started, it is already running or the executor rejected it." }
    }
  }

  internal fun executeTask(lockedTask: LockedExternalTaskDto, activeSubscription: TaskSubscriptionHandle) {
    // make sure the task has not expired waiting in the queue for the execution
    val start = OffsetDateTime.now()
    val timePassedSinceLockAcquisition = Duration.between(lockedTask.lockExpirationTime!!.minusSeconds(lockDurationInSeconds), start)
    metrics.recordTaskQueueTime(lockedTask.topicName!!, timePassedSinceLockAcquisition)
    if (start.isBefore(lockedTask.lockExpirationTime)) {
      try {
        deliverTask(lockedTask, activeSubscription)
      } finally {
        metrics.recordTaskExecutionTime(lockedTask.topicName!!, Duration.between(start, OffsetDateTime.now()))
      }
    } else {
      metrics.incrementDroppedTasksCounter(lockedTask.topicName!!, EXPIRED_WHILE_IN_QUEUE)
    }
  }

  private fun deliverTask(lockedTask: LockedExternalTaskDto, activeSubscription: TaskSubscriptionHandle) {
    val (taskInformation, variables) = try {
      subscriptionRepository.activateSubscriptionForTask(lockedTask.id!!, activeSubscription)
      val createdTaskInformation = toTaskInformation(lockedTask).withReason(CREATE)
      val subscribedVariables = valueMapper.mapDtos(lockedTask.variables!!).filterBySubscription(activeSubscription)
      createdTaskInformation to subscribedVariables
    } catch (e: Exception) {
      reportFailure(lockedTask, e)
      return
    }
    decoration.run(taskInformation, variables) {
      outcomeRecorder.track(lockedTask.id!!) { invokeHandlerOrReportFailure(lockedTask, activeSubscription, taskInformation, variables) }
    }
  }

  private fun invokeHandlerOrReportFailure(
    lockedTask: LockedExternalTaskDto,
    activeSubscription: TaskSubscriptionHandle,
    taskInformation: TaskInformation,
    variables: Map<String, Any?>,
  ) {
    try {
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-031: delivering service task ${lockedTask.id}." }
      withThreadContextClassLoader(activeSubscription.action) {
        activeSubscription.action.accept(taskInformation, variables)
      }
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-032: successfully delivered service task ${lockedTask.id}." }
      metrics.incrementCompletedTasksCounter(lockedTask.topicName!!)
    } catch (e: Exception) {
      reportFailure(lockedTask, e)
    }
  }

  private fun reportFailure(lockedTask: LockedExternalTaskDto, failure: Exception) {
    val taskId = lockedTask.id!!
    val isOutcomeAlreadyReported = outcomeRecorder.hasOutcome(taskId)
    if (isOutcomeAlreadyReported) {
      logger.error { "PROCESS-ENGINE-C7-REMOTE-048: handler of task $taskId failed after reporting an outcome, not reporting the failure: ${failure.message}" }
      return
    }
    logger.error { "PROCESS-ENGINE-C7-REMOTE-033: failing delivering task $taskId: ${failure.message}" }
    metrics.incrementFailedTasksCounter(lockedTask.topicName!!)
    val remainingRetries = lockedTask.retries?.minus(1) ?: retries
    val retryTimeoutInMillis = retryTimeoutInSeconds * 1000
    externalTaskApi.handleFailure(
      taskId,
      ExternalTaskFailureDto().apply {
        workerId = this@PullServiceTaskDelivery.workerId
        retries = remainingRetries
        retryTimeout = retryTimeoutInMillis
        errorDetails = failure.stackTraceToString()
        errorMessage = failure.message
      }
    )
    val failedOutcome = TaskOutcome.Failed(failure.message, remainingRetries, failure)
    outcomeRecorder.record(taskId, failedOutcome)
    cleanupFailedDelivery(taskId)
    logger.error { "PROCESS-ENGINE-C7-REMOTE-034: successfully failed delivering task $taskId: ${failure.message}" }
  }

  internal fun toTaskInformation(lockedTask: LockedExternalTaskDto) = lockedTask.toTaskInformation(processDefinitionMetaDataResolver)

  internal fun cleanUpTerminatedTasks() {
    val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL)
    val notRunningTaskIds = deliveredTaskIds.filterNot(workerPool::isRunning)
    val goneTaskIds = findTaskIdsMissingInEngine(notRunningTaskIds)
    val stillLockedTaskCount = deliveredTaskIds.size - goneTaskIds.size
    stillLockedTasksGauge.set(stillLockedTaskCount)
    if (goneTaskIds.isEmpty()) {
      return
    }
    submitTerminations(goneTaskIds)
  }

  private fun submitTerminations(goneTaskIds: List<String>) {
    workerPool.reserve(maxSlots = goneTaskIds.size).use { reservation ->
      goneTaskIds.take(reservation.slots).forEach { goneTaskId -> reservation.submit(goneTaskId) { terminateTask(goneTaskId) } }
    }
  }

  private fun findTaskIdsMissingInEngine(taskIds: List<String>): List<String> {
    val existingTaskIds = taskIds.chunked(EXISTENCE_QUERY_CHUNK_SIZE).flatMap(::queryExistingTaskIds).toSet()
    return taskIds.filterNot(existingTaskIds::contains)
  }

  private fun queryExistingTaskIds(taskIds: List<String>): List<String> {
    val existenceQuery = ExternalTaskQueryDto().externalTaskIdIn(taskIds)
    return externalTaskApi
      .queryExternalTasks(null, null, existenceQuery)
      .mapNotNull { it.id }
  }

  internal fun terminateTask(taskId: String) {
    // deactivate active subscription and handle termination
    val taskSubscriptionHandle = subscriptionRepository.deactivateSubscriptionForTask(taskId)
    if (taskSubscriptionHandle != null) {
      withThreadContextClassLoader(taskSubscriptionHandle.termination) {
        taskSubscriptionHandle.termination.accept(
          TaskInformation(
            taskId = taskId,
            meta = emptyMap()
          ).withReason(TaskInformation.DELETE)
        )
      }
      metrics.incrementTerminatedTasksCounter(taskSubscriptionHandle.taskDescriptionKey ?: "?")
    }
  }

  private fun cleanupFailedDelivery(taskId: String) {
    val taskSubscriptionHandle = subscriptionRepository.deactivateSubscriptionForTask(taskId)
    if (taskSubscriptionHandle != null) {
      try {
        withThreadContextClassLoader(taskSubscriptionHandle.termination) {
          taskSubscriptionHandle.termination.accept(
            TaskInformation(taskId = taskId, meta = emptyMap()).withReason(TaskInformation.DELETE)
          )
        }
      } catch (terminationError: Exception) {
        logger.error { "PROCESS-ENGINE-C7-REMOTE-046: failed cleaning up delivery state for task $taskId: ${terminationError.message}" }
      }
    }
  }

  private fun FetchExternalTasksDto.forSubscriptions(subscriptions: List<TaskSubscriptionHandle>): FetchExternalTasksDto {
    subscriptions
      .map { it.taskDescriptionKey to it.restrictions }
      .distinctBy { it.first }
      .forEach { (topic, restrictions) ->
        val lockDurationInMilliseconds = getLockDurationForSubscription(restrictions)
        this.addTopicsItem(
          FetchExternalTaskTopicDto().topicName(topic).lockDuration(lockDurationInMilliseconds).apply {
            this.deserializeValues = this@PullServiceTaskDelivery.deserializeOnServer

            /*
            TODO: decide should we do it here? or is matches functionality enough?

            if (restrictions.containsKey(CommonRestrictions.BUSINESS_KEY)) {
              this.businessKey = restrictions.getValue(CommonRestrictions.BUSINESS_KEY) // support business key
            }
            require( !(restrictions.containsKey(CommonRestrictions.TENANT_ID) && restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID) ))
              { "Illegal restriction. Either set required tenant id or without tenant but not both at the same time."}
            if (restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
              this.tenantIdIn = listOf(restrictions.getValue(CommonRestrictions.TENANT_ID))
              this.withoutTenantId = false
            }
            if (restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
              this.withoutTenantId = true
            }
            */
          }
        )
      }
    return this
  }

  private fun getLockDurationForSubscription(
    restrictions: Map<String, String>,
  ): Long {
    val customLockDuration = restrictions[CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS]
    return customLockDuration?.toLong() ?: (lockDurationInSeconds * 1000)
  }

  internal fun matches(task: LockedExternalTaskDto, subscription: TaskSubscriptionHandle): Boolean =
    (subscription.taskDescriptionKey == null
      || subscription.taskDescriptionKey == task.topicName)
      && subscription.restrictions
      .minus( // ignore some restrictions which are not relevant for external tasks
        CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS
      ).all {
        when (it.key) {
          CommonRestrictions.EXECUTION_ID -> it.value == task.executionId
          CommonRestrictions.ACTIVITY_ID -> it.value == task.activityId
          CommonRestrictions.BUSINESS_KEY -> it.value == task.businessKey
          CommonRestrictions.TENANT_ID -> it.value == task.tenantId
          CommonRestrictions.PROCESS_INSTANCE_ID -> it.value == task.processInstanceId
          CommonRestrictions.PROCESS_DEFINITION_KEY -> it.value == task.processDefinitionKey
          CommonRestrictions.PROCESS_DEFINITION_ID -> it.value == task.processDefinitionId
          CommonRestrictions.PROCESS_DEFINITION_VERSION_TAG -> it.value == task.processDefinitionVersionTag
          else -> {
            logger.debug { "PROCESS-ENGINE-C7-REMOTE-043: Unknown restriction key: ${it.key}" }
            false
          }
        }
      }

}
