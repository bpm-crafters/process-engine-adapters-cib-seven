---
title: CIB Seven as embedded engine
---

If you start with CIB Seven, operated in an embedded engine mode, by for example using the CIB Seven Spring Boot Starter,
the following configuration is applicable for you.

First of all, add the corresponding adapter to your project's classpath:

```xml

<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-cib-seven-embedded-spring-boot-starter</artifactId>
  <version>${process-engine-api.version}</version>
</dependency>
```

And finally, add the following configuration to your configuration properties. Here is a version for `application.yaml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        cib-seven-embedded:
          enabled: true
          service-tasks:
            delivery-strategy: embedded_scheduled
            worker-id: embedded-worker
            lock-time-in-seconds: 10
            execute-initial-pull-on-startup: true
            execute-initial-pull-async: false
            schedule-delivery-fixed-rate: 60s
          user-tasks:
            delivery-strategy: embedded_scheduled
            execute-initial-pull-on-startup: true
            execute-initial-pull-async: false
            schedule-delivery-fixed-rate: 60s
          deployment:
            deploy-only-on-change: false
```

## Deployment

| Key                            | Value             | Description                                                                                                                                                                |
|--------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `deployment.deploy-only-on-change` | `true` or `false` | If `true`, a new deployment is only created when at least one resource actually changed; unchanged BPMN/DMN resources are not redeployed as new versions. Defaults to `false`. |

## Initial pull and lifecycle

The starter drives task delivery with the process application lifecycle of `process-engine-api-lifecycle-spring-boot`. Register your task subscriptions when the `TaskSubscriptionRegistrationEvent` is published, for example in an `@EventListener`. Initial pulls run when the process application enters the `RUNNING` phase, after all task subscriptions are registered. Scheduled delivery (`embedded_scheduled`) starts in the same phase and is cancelled at `STOPPING`; pulls that are already running finish.

| Key | Default | Description |
|-----|---------|-------------|
| `service-tasks.execute-initial-pull-on-startup` | `true` | Runs one service task pull when the process application enters `RUNNING`. |
| `service-tasks.execute-initial-pull-async` | `false` | Runs the initial service task pull on the service task worker executor. By default `RUNNING` is reached only after the initial pull has returned. |
| `user-tasks.execute-initial-pull-on-startup` | `true` | Runs one user task pull when the process application enters `RUNNING`. |
| `user-tasks.execute-initial-pull-async` | `false` | Runs the initial user task pull on the user task worker executor. By default `RUNNING` is reached only after the initial pull has returned. |

## Scheduled delivery interval

Applies to `service-tasks.*` and `user-tasks.*` with delivery strategy `embedded_scheduled`.

| Key | Default | Description |
|-----|---------|-------------|
| `service-tasks.schedule-delivery-fixed-rate` | not set | Polling delay as a `Duration`, e.g. `200ms`. Takes precedence over `schedule-delivery-fixed-rate-in-seconds`. Must be at least `1ms`. A value without unit is read as milliseconds. The delay starts after the previous pull finished, including its handlers. Every pull also runs the cleanup query for locked tasks, so short intervals add engine load. |
| `service-tasks.schedule-delivery-fixed-rate-in-seconds` | `13` | Deprecated, use `schedule-delivery-fixed-rate`. Polling delay in whole seconds. |
| `user-tasks.schedule-delivery-fixed-rate` | not set | Polling delay as a `Duration`, e.g. `200ms`. Takes precedence over `schedule-delivery-fixed-rate-in-seconds`. Must be at least `1ms`. A value without unit is read as milliseconds. The delay starts after the previous pull finished, including its handlers. Every pull queries the engine, so short intervals add engine load. |
| `user-tasks.schedule-delivery-fixed-rate-in-seconds` | `5` | Deprecated, use `schedule-delivery-fixed-rate`. Polling delay in whole seconds. |
