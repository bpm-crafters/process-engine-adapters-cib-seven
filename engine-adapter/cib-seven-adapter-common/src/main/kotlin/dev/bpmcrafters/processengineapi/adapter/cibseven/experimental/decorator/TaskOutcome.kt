package dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator

/**
 * **Experimental**: outcome of the execution of one external service task, as reported to the engine
 * during the execution, either by the handler through the completion API or by the delivery when the handler failed.
 */
@ExperimentalTaskExecutionDecoratorApi
sealed interface TaskOutcome {

  /**
   * The task was completed.
   */
  data object Completed : TaskOutcome

  /**
   * The task was completed with a BPMN error. A business outcome, not a technical failure.
   */
  data class BpmnError(val errorCode: String) : TaskOutcome

  /**
   * The task failed technically and was reported to the engine.
   * @param retriesLeft retries reported to the engine; `0` creates an incident. `null` if unknown.
   * @param cause exception thrown by the handler, `null` if the handler reported the failure itself.
   */
  data class Failed(
    val errorMessage: String?,
    val retriesLeft: Int?,
    val cause: Throwable? = null,
  ) : TaskOutcome

  /**
   * The handler returned without reporting an outcome, e.g. because it completes the task asynchronously later.
   */
  data object Pending : TaskOutcome
}
