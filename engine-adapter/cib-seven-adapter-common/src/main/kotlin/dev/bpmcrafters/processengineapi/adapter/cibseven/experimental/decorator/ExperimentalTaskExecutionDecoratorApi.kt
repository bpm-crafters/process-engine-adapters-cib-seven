package dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator

/**
 * Marks the **experimental** task execution decorator API of the CIB seven adapter.
 *
 * It mirrors the `TaskExecutionDecorator` proposed in
 * [process-engine-api#313](https://github.com/bpm-crafters/process-engine-api/pull/313).
 * Will move to process-engine-api and may change or disappear without notice.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "Experimental CIB seven adapter API. It will move to process-engine-api and may change without notice."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@MustBeDocumented
annotation class ExperimentalTaskExecutionDecoratorApi
