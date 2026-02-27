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
            schedule-delivery-fixed-rate-in-seconds: 60
          user-tasks:
            delivery-strategy: embedded_scheduled
            execute-initial-pull-on-startup: true
            schedule-delivery-fixed-rate-in-seconds: 60
```
