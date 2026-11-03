# Service task delivery

Both adapters, embedded and remote, deliver external service tasks with the same rules.

## Executor and limit

Service tasks run in a `ServiceTaskWorkerPool`. The pool decides both how the tasks run (its executor) and how many are locked and running
at the same time (its limit), so the two always fit together. By default it has 10 platform threads and runs at most 10 tasks.

To change it, declare your own pool bean named `cib-seven-embedded-service-task-worker-pool` or `cib-seven-remote-service-task-worker-pool`:

```kotlin
@Bean("cib-seven-embedded-service-task-worker-pool")
fun serviceTaskWorkerPool() = ServiceTaskWorkerPool.withPlatformThreads(20)
```

```kotlin
@Bean("cib-seven-embedded-service-task-worker-pool")
fun serviceTaskWorkerPool() = ServiceTaskWorkerPool(Executors.newVirtualThreadPerTaskExecutor(), maxConcurrentTasks = 200)
```

The pool shuts down its executor when the application stops.

- The delivery never waits for a handler, so a slow task does not hold up other tasks.
- A fetch never locks more tasks than the pool has free slots. The scheduled delivery, the initial pull and the clean-up of gone tasks share the pool.
- A task that is still running is not started a second time, and its termination handler is not called while it runs.
- The lock (`lock-time-in-seconds`, or `WORKER_LOCK_DURATION_IN_MILLISECONDS` per subscription) must exceed the runtime of the slowest handler.

`max-task-count`, and on remote `worker-thread-pool-size` and `worker-thread-pool-queue-capacity`, are removed. A custom
`…-service-task-worker-executor` bean is replaced by a custom pool bean.

## Task execution decorators (experimental)

A `TaskExecutionDecorator` bean runs around the execution of every service task, e.g. for tracing, MDC or metrics.
It calls `execution.proceed()` once and gets the outcome of the task back:

```kotlin
@Bean
fun tracingDecorator(tracer: Tracer) = TaskExecutionDecorator { task, _, execution ->
  val span = tracer.nextSpan().name(task.meta["topicName"]).start()
  try {
    when (val outcome = execution.proceed()) {
      is TaskOutcome.Failed -> span.error(outcome.cause ?: IllegalStateException(outcome.errorMessage))
      else -> Unit
    }
  } finally {
    span.end()
  }
}
```

| Outcome     | When                                                                                           |
|-------------|------------------------------------------------------------------------------------------------|
| `Completed` | The handler completed the task through the completion API.                                     |
| `BpmnError` | The handler completed the task with a BPMN error, a business outcome.                          |
| `Failed`    | The handler threw and the adapter reported the failure, or the handler failed the task itself, e.g. `@ProcessEngineWorker`. |
| `Pending`   | The handler returned without an outcome, e.g. because it completes the task asynchronously later. |

The outcome is already reported to the engine when `proceed()` returns, so a decorator observes it and cannot change it.
A failure is reported exactly once. A decorator that throws, skips or repeats `proceed()` is logged; the task still runs exactly once.
Decorators are ordered by `@Order` and run in the thread of the task, so thread-local context such as the current span or the MDC applies.
