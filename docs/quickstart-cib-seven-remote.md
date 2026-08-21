---
title: CIB Seven as remote engine
---

If you run CIB Seven as a **standalone (remote) engine** — for example the CIB Seven Run
distribution or a central engine deployment — and talk to it over the REST API (`/engine-rest`),
the following configuration is applicable for you.

First of all, add the corresponding adapter to your project's classpath:

```xml

<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-cib-seven-remote-spring-boot-starter</artifactId>
  <version>${process-engine-api.version}</version>
</dependency>
```

The REST client is generated at build time from CIB Seven's official OpenAPI document
(`org.cibseven.bpm:cibseven-engine-rest-openapi`), so client and engine never drift.

And finally, add the following configuration to your configuration properties. Here is a version for `application.yaml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        cib-seven-remote:
          enabled: true
          client:
            base-url: http://localhost:8080/engine-rest
            # username: demo          # optional, for HTTP basic authentication
            # password: demo
          service-tasks:
            delivery-strategy: remote_scheduled   # remote_scheduled | remote_subscribed | custom | disabled
            worker-id: remote-worker
            max-task-count: 100
            lock-time-in-seconds: 10
            execute-initial-pull-on-startup: true
            schedule-delivery-fixed-rate-in-seconds: 30
          user-tasks:
            delivery-strategy: remote_scheduled   # remote_scheduled | custom | disabled
            execute-initial-pull-on-startup: true
            schedule-delivery-fixed-rate-in-seconds: 30
```

## Connection

| Key                  | Value                | Description                                                                                   |
|----------------------|----------------------|-----------------------------------------------------------------------------------------------|
| `client.base-url`    | URL                  | Base URL of the CIB Seven engine REST API, e.g. `http://localhost:8080/engine-rest`.          |
| `client.username`    | String (optional)    | User name for HTTP basic authentication. Leave unset for an unauthenticated engine.           |
| `client.password`    | String (optional)    | Password for HTTP basic authentication.                                                       |

## Service task delivery strategies

| Strategy            | Description                                                                                                                        |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `remote_scheduled`  | The adapter periodically fetches-and-locks external tasks over REST (pull), using its own scheduler.                              |
| `remote_subscribed` | The official CIB Seven **external task client** long-polls external tasks (subscribe). Requires the external task client on the classpath. |
| `custom`            | You provide your own delivery beans.                                                                                              |
| `disabled`          | No service task delivery.                                                                                                         |

## User task delivery strategies

| Strategy            | Description                                                                          |
|---------------------|--------------------------------------------------------------------------------------|
| `remote_scheduled`  | The adapter periodically queries user tasks over REST (pull), using its own scheduler. |
| `custom`            | You provide your own delivery beans.                                                 |
| `disabled`          | No user task delivery.                                                               |
