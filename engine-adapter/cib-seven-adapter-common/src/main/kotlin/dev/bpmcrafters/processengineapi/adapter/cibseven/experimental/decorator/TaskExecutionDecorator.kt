package dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator

import dev.bpmcrafters.processengineapi.task.TaskInformation

/**
 * **Experimental** decorator of the execution of an external service task, for cross-cutting concerns
 * such as tracing, MDC, logging or metrics, in the spirit of Spring's `TaskDecorator`.
 * Will move to process-engine-api.
 *
 * The delivery passes its whole per-task work as `execution`: the call of the task handler and the
 * delivery's own failure handling and logging. A decorator receives read-only views of the task
 * information and the payload, and must call `execution.run()` exactly once, in the thread the
 * decorated runnable runs in. Decorators run synchronously in the delivery thread.
 *
 * This is not an engine listener such as a Camunda `ExecutionListener` or `TaskListener`.
 */
@ExperimentalTaskExecutionDecoratorApi
fun interface TaskExecutionDecorator {
  /**
   * Decorates the execution of one task.
   * @param task information about the delivered task, as an unmodifiable view.
   * @param payload payload delivered to the handler, as an unmodifiable view.
   * @param execution per-task work of the delivery. Run it exactly once.
   * @return runnable that runs [execution] with the decoration around it.
   */
  fun decorate(task: TaskInformation, payload: Map<String, Any?>, execution: Runnable): Runnable
}
