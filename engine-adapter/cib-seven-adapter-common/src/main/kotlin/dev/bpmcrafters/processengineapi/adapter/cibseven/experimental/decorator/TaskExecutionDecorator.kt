package dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator

import dev.bpmcrafters.processengineapi.task.TaskInformation

/**
 * **Experimental** decorator around the execution of an external service task, for cross-cutting concerns
 * such as tracing, MDC, logging or metrics. Will move to process-engine-api.
 *
 * A decorator calls [TaskExecution.proceed] exactly once, in its own thread, and gets the [TaskOutcome] back,
 * no matter whether the handler reported it through the completion API or the delivery reported a failure of the handler.
 * The outcome is already reported to the engine when `proceed` returns, so a decorator observes it and cannot change it.
 * A decorator that throws, skips or repeats `proceed` is only logged; the task runs exactly once anyway.
 *
 * This is not an engine listener such as a Camunda `ExecutionListener` or `TaskListener`.
 */
@ExperimentalTaskExecutionDecoratorApi
fun interface TaskExecutionDecorator {
  /**
   * @param task information about the delivered task, as an unmodifiable view.
   * @param payload payload delivered to the handler, as an unmodifiable view.
   * @param execution execution of the task. Proceed exactly once.
   */
  fun decorate(task: TaskInformation, payload: Map<String, Any?>, execution: TaskExecution)
}

/**
 * **Experimental**: execution of one external service task, passed to a [TaskExecutionDecorator].
 */
@ExperimentalTaskExecutionDecoratorApi
fun interface TaskExecution {
  fun proceed(): TaskOutcome
}
