# Task handler interceptors (prototype)

> **Status: prototype.** Builds against the interceptor spike of the Process Engine API
> (`bpm-crafters/process-engine-api`, branch `emaarco/system-instruction-phase-1`, PR #313),
> which is not released yet. Build it locally first (`mvn install -DskipTests` on that branch,
> version `1.8-SNAPSHOT`).

Both Spring Boot starters (embedded and remote) collect every `TaskHandlerInterceptor` bean and
pass them, sorted by `@Order` / `Ordered`, to the task subscription API. The interceptors then
run as a chain around every task handler:

```
interceptor(order 1) -> interceptor(order 2) -> ... -> task handler
```

The service task completion API beans are wrapped in `OutcomeRecordingServiceTaskCompletionApi`,
so that `chain.proceed()` returns `Completed`, `CompletedByError` or `Failed` when the handler
finishes the task through the `ServiceTaskCompletionApi` in the delivery thread. An exception
thrown by the handler passes through the chain and is handled by the delivery as before.

## Registering interceptors

```kotlin
@Configuration
class InterceptorConfiguration {

  @Bean
  @Order(1)
  fun loggingTaskHandlerInterceptor(): TaskHandlerInterceptor = LoggingTaskHandlerInterceptor()

  @Bean
  @Order(2)
  fun metricsTaskHandlerInterceptor(registry: MeterRegistry): TaskHandlerInterceptor =
    MetricsTaskHandlerInterceptor(registry)
}
```

## Examples

Two example interceptors live in the tests of the embedded starter
(`engine-adapter/cib-seven-embedded-spring-boot-starter/src/test/kotlin/.../springboot/interceptor`):

- `LoggingTaskHandlerInterceptor` logs before and after each handler, including the outcome.
- `MetricsTaskHandlerInterceptor` counts executions per outcome
  (`process.engine.task.handler.executions{taskType,taskDescriptionKey,outcome}`) and records a
  timer (`process.engine.task.handler.duration`) using Micrometer.

`Cib7EmbeddedSpringTaskHandlerInterceptorITest` runs both against an embedded engine and checks
the order of the chain (logging before -> metrics -> logging after) for a completed task, a BPMN
error and a handler exception.
