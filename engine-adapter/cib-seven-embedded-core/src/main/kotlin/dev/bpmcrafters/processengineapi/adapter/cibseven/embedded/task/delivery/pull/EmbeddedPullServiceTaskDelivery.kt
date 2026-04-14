package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
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
import java.util.concurrent.ExecutorService

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
  private val executorService: ExecutorService,
) : ExternalServiceTaskDelivery, RefreshableDelivery {

  /**
   * Delivers all tasks found in the external service to corresponding subscriptions.
   */
  override fun refresh() {

    val subscriptions = subscriptionRepository.getTaskSubscriptions().filter { s -> s.taskType == TaskType.EXTERNAL }
    if (subscriptions.isNotEmpty()) {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-030: pulling service tasks for subscriptions: $subscriptions" }
      val deliveredTaskIds = subscriptionRepository.getDeliveredTaskIds(TaskType.EXTERNAL).toMutableList()
      externalTaskService
        .fetchAndLock(maxTasks, workerId)
        .forSubscriptions(subscriptions)
        .execute()
        .parallelStream()
        .map { lockedTask ->
          val activeSubscription = subscriptions.firstOrNull { subscription -> subscription.matches(lockedTask) }
          if (activeSubscription == null) {
            logger.warn { "PROCESS-ENGINE-CIB7-EMBEDDED-044: no subscription found for fetched task ${lockedTask.id} with topic '${lockedTask.topicName}'." }
            return@map null
          }
          executorService.submit {  // in another thread
            try {
              val taskInformation = if (deliveredTaskIds.contains(lockedTask.id)
                && subscriptionRepository.getActiveSubscriptionForTask(lockedTask.id) == activeSubscription
              ) {
                // task is already delivered to the current subscription, nothing to do
                null
              } else {
                // create task information and set up the reason
                lockedTask.toTaskInformation().withReason(TaskInformation.CREATE)
              }
              if (taskInformation != null) {
                subscriptionRepository.activateSubscriptionForTask(lockedTask.id, activeSubscription)
                val variables = lockedTask.variables.filterBySubscription(activeSubscription)
                logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-031: delivering service task ${lockedTask.id}." }
                activeSubscription.action.accept(taskInformation, variables)
                logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-032: successfully delivered service task ${lockedTask.id}." }
              } else {
                logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-041: skipping task ${lockedTask.id} since it is unchanged." }
              }
              // remove from already delivered
              deliveredTaskIds.remove(lockedTask.id)
            } catch (e: Exception) {
              val jobRetries: Int = lockedTask.retries ?: retries
              logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-033: failing delivering task ${lockedTask.id}: ${e.message}" }
              externalTaskService.handleFailure(lockedTask.id, workerId, e.message, jobRetries - 1, retryTimeoutInSeconds * 1000)
              subscriptionRepository.deactivateSubscriptionForTask(taskId = lockedTask.id)
              logger.error { "PROCESS-ENGINE-CIB7-EMBEDDED-034: successfully failed delivering task ${lockedTask.id}: ${e.message}" }
            }
          }
        }.forEach { taskExecutionFuture ->
          taskExecutionFuture?.get()
        }

      // now we removed all still existing task ids from the list of already delivered
      // the remaining tasks doesn't exist in the engine, lets handle them
      deliveredTaskIds.parallelStream().map { taskId ->
        executorService.submit {
          // deactivate active subscription and handle termination
          subscriptionRepository.deactivateSubscriptionForTask(taskId)?.termination?.accept(
            TaskInformation(
              taskId,
              emptyMap()
            ).withReason(TaskInformation.DELETE)
          )
          logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-043: deactivating $taskId, task is gone." }
        }
      }.forEach { taskTerminationFuture -> taskTerminationFuture.get() }

    } else {
      logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-035: Pull external tasks disabled because of no active subscriptions" }
    }
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
    val customLockDuration = subscription.restrictions["workerLockDurationInMilliseconds"]
    return customLockDuration?.toLong() ?: (lockDurationInSeconds * 1000)
  }
}

internal fun TaskSubscriptionHandle.matches(task: LockedExternalTask): Boolean =
  this.taskType == TaskType.EXTERNAL
    && (this.taskDescriptionKey == null || this.taskDescriptionKey == task.topicName)
    && this.restrictions
      .minus("workerLockDurationInMilliseconds")
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
