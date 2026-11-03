package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.threading.withThreadContextClassLoader
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.ExternalTaskService
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Strategy for completing external tasks using Camunda externalTaskService Java API.
 */
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class CibSevenServiceTaskCompletionApiImpl(
  private val workerId: String,
  private val externalTaskService: ExternalTaskService,
  private val subscriptionRepository: SubscriptionRepository,
  private val failureRetrySupplier: FailureRetrySupplier,
  private val outcomeRecorder: TaskOutcomeRecorder,
) : ServiceTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {

    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-006: completing service task ${cmd.taskId}." }
    externalTaskService.complete(
      cmd.taskId,
      workerId,
      cmd.get()
    )
    outcomeRecorder.record(cmd.taskId, TaskOutcome.Completed)
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      withThreadContextClassLoader(termination) {
        termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      }
      logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-007: successfully completed service task ${cmd.taskId}." }
    }
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-008: throwing error ${cmd.errorCode} in service task ${cmd.taskId}." }
    externalTaskService.handleBpmnError(
      cmd.taskId,
      workerId,
      cmd.errorCode,
      cmd.errorMessage,
      cmd.get()
    )
    outcomeRecorder.record(cmd.taskId, TaskOutcome.BpmnError(cmd.errorCode))
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      withThreadContextClassLoader(termination) {
        termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      }
      logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-009: successfully thrown error in service task ${cmd.taskId}." }
    }
    return CompletableFuture.completedFuture(Empty)
  }

  override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-010: failing service task ${cmd.taskId}." }
    val (defaultRetries, defaultRetryTimeoutInSeconds) = failureRetrySupplier.apply(cmd.taskId)
    val remainingRetries = cmd.retryCount ?: defaultRetries
    val retryTimeoutInMillis = cmd.retryBackoff?.toMillis() ?: (defaultRetryTimeoutInSeconds * 1000)
    externalTaskService.handleFailure(
      cmd.taskId,
      workerId,
      cmd.reason,
      cmd.errorDetails,
      remainingRetries,
      retryTimeoutInMillis
    )
    val failedOutcome = TaskOutcome.Failed(cmd.reason, remainingRetries)
    outcomeRecorder.record(cmd.taskId, failedOutcome)
    subscriptionRepository.deactivateSubscriptionForTask(cmd.taskId)?.apply {
      withThreadContextClassLoader(termination) {
        termination.accept(TaskInformation(cmd.taskId, emptyMap()).withReason(TaskInformation.COMPLETE))
      }
      logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-011: successfully failed service task ${cmd.taskId} handling." }
    }
    return CompletableFuture.completedFuture(Empty)
  }
}
