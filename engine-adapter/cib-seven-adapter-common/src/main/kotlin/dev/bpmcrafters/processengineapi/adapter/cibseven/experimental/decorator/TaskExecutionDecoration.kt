package dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator

import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Collections

private val logger = KotlinLogging.logger {}

/**
 * **Experimental**: runs the execution of one task with the [TaskExecutionDecorator]s around it, the first one outermost.
 * Will move to process-engine-api.
 *
 * The execution always runs exactly once; a decorator that fails, skips or repeats it is only logged.
 * @param decorators decorators in execution order.
 */
@ExperimentalTaskExecutionDecoratorApi
class TaskExecutionDecoration(private val decorators: List<TaskExecutionDecorator>) {

  /**
   * @return the outcome of [execution]. Exceptions thrown by the execution pass through unchanged.
   */
  fun run(task: TaskInformation, payload: Map<String, Any?>, execution: TaskExecution): TaskOutcome {
    val singleExecution = SingleTaskExecution(execution) { warnRepeatedProceed(task) }
    val readOnlyTask = task.copy(meta = Collections.unmodifiableMap(task.meta))
    val readOnlyPayload = Collections.unmodifiableMap(payload)
    val decoratedExecution = decorators.foldRight<TaskExecutionDecorator, TaskExecution>(singleExecution) { decorator, innerExecution ->
      TaskExecution {
        try {
          decorator.decorate(readOnlyTask, readOnlyPayload, innerExecution)
        } catch (e: Exception) {
          if (singleExecution.hasFailed) throw e
          logDecoratorFailure(task, e)
        }
        singleExecution.outcome { warnSkippedProceed(task) }
      }
    }
    return decoratedExecution.proceed()
  }

  private fun warnRepeatedProceed(task: TaskInformation) =
    logger.warn { "PROCESS-ENGINE-CIB7-DECORATOR-003: Decorator proceeded task ${task.taskId} again, ignoring the repeated execution." }

  private fun warnSkippedProceed(task: TaskInformation) =
    logger.warn { "PROCESS-ENGINE-CIB7-DECORATOR-002: Decorator did not proceed task ${task.taskId}, running it without the decorator." }

  private fun logDecoratorFailure(task: TaskInformation, e: Exception) =
    logger.error(e) { "PROCESS-ENGINE-CIB7-DECORATOR-001: Task execution decorator failed for task ${task.taskId}." }

  companion object {
    /**
     * Decoration without decorators, which just runs the execution.
     */
    @JvmField
    val NONE = TaskExecutionDecoration(emptyList())
  }
}

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
private class SingleTaskExecution(
  private val execution: TaskExecution,
  private val onRepeatedProceed: () -> Unit,
) : TaskExecution {

  private val result = lazy { runCatching { execution.proceed() } }

  val hasFailed: Boolean
    get() = result.isInitialized() && result.value.isFailure

  override fun proceed(): TaskOutcome {
    if (result.isInitialized()) onRepeatedProceed()
    return result.value.getOrThrow()
  }

  fun outcome(onSkippedProceed: () -> Unit): TaskOutcome {
    if (!result.isInitialized()) onSkippedProceed()
    return result.value.getOrThrow()
  }
}
