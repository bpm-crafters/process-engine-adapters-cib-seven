# Task execution decorators (experimental)

> **Experimental.** This API mirrors the `TaskExecutionDecorator` proposed in
> [process-engine-api#313](https://github.com/bpm-crafters/process-engine-api/pull/313). It lives in the adapter package
> `dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator` until it is released in process-engine-api,
> and it will move there. It may change or disappear without notice.

A `TaskExecutionDecorator` wraps the execution of external service tasks for cross-cutting concerns such as tracing,
MDC, logging or metrics, in the spirit of Spring's `TaskDecorator`. It is not an engine listener such as a Camunda
`ExecutionListener` or `TaskListener`, and it is not declared in the BPMN model.

## Opt-in

Kotlin code that uses the API has to opt in, otherwise the compiler warns:

```kotlin
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
```

## Registration

Both starters (embedded and remote) collect every `TaskExecutionDecorator` bean, ordered by `@Order` / `Ordered`, into
one `TaskExecutionDecoration` bean and apply it in every service task delivery: scheduled pull, initial pull and, for remote, the official external task
client. Without decorator beans the deliveries behave exactly as before.

## Semantics

- The delivery passes its whole per-task work as `execution`: the call of the handler and the delivery's own failure
  handling and logging. A span or MDC entry opened around `execution.run()` also covers the failure logs.
- Decorators are nested in order, the first one outermost.
- A decorator gets read-only views of the task information and the payload.
- Call `execution.run()` exactly once, in the thread of the decorated runnable. If a decorator skips, repeats or fails
  around it, the adapter still runs the execution exactly once and only logs the misbehaviour.
- An exception thrown by a decorator is only logged, except a `VirtualMachineError`.
- A failing handler is handled by the delivery, so `execution.run()` returns normally. A decorator does not learn the
  outcome.
- Decorators run synchronously in the delivery thread. Keep them cheap and release what you open in a `finally` block.

## Example

```kotlin
@Component
@Order(0)
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class MdcTaskExecutionDecorator : TaskExecutionDecorator {

  override fun decorate(task: TaskInformation, payload: Map<String, Any?>, execution: Runnable) = Runnable {
    MDC.putCloseable("activityInstanceId", task.meta[TaskMetaKeys.ACTIVITY_INSTANCE_ID]).use {
      execution.run()
    }
  }
}
```

## Task meta

Service tasks carry the `activityInstanceId` in `TaskInformation.meta`, under the key
`TaskMetaKeys.ACTIVITY_INSTANCE_ID`, e.g. for log or trace correlation.
