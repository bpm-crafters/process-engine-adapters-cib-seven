# CIB7 Embedded Spring Boot Starter

Spring Boot auto-configuration for CIB Seven embedded process engine adapter.

## 🎯 Purpose

This starter provides **zero-code configuration** for integrating the CIB Seven adapter into Spring Boot applications:

- **Auto-configuration** - Adapter beans are created automatically based on properties
- **Property-based setup** - Configure via `application.yml` instead of Java code
- **Spring-managed lifecycle** - Beans, scheduling, and task delivery handled by Spring
- **Production-ready** - Includes polling strategies, error handling, and monitoring

**Recommended** for most Spring Boot users - simpler than using [cib-seven-embedded-core](../cib-seven-embedded-core/) directly.

## 🤔 When to Use

Choose **cib-seven-embedded-spring-boot-starter** when:

- ✅ Building a Spring Boot application (most common case)
- ✅ You want auto-configuration and convention-over-configuration
- ✅ You need scheduled task polling (EMBEDDED_SCHEDULED strategy)
- ✅ You prefer YAML configuration over programmatic setup

Choose **[cib-seven-embedded-core](../cib-seven-embedded-core/)** when:

- Working in non-Spring applications
- Need custom framework integration
- Require direct control over adapter lifecycle

## ✨ Features

### Auto-Configuration

- **Adapter API beans** - All Process Engine API implementations available as Spring beans
- **Conditional configuration** - Beans created only when enabled and CIB Seven is on classpath
- **Delivery strategies** - Multiple task delivery modes (scheduled polling, custom)
- **Scheduler integration** - Automatic task polling using Spring's `@Scheduled`

### Configuration Properties

- **Centralized YAML config** - All settings under `dev.bpm-crafters.process-api.adapter.cib-seven-embedded`
- **Service tasks** - Worker ID, lock time, retry timeout, polling rate
- **User tasks** - Delivery strategy, polling rate, initial pull
- **Validation** - Spring Boot validation for configuration properties

### Task Delivery Strategies

**Service Tasks (External Tasks):**

- `EMBEDDED_SCHEDULED` - Poll for tasks on a fixed schedule

**User Tasks:**

- `EMBEDDED_SCHEDULED` - Poll for tasks on a fixed schedule
- _(Custom strategies can be implemented)_

## ⚙️ Configuration

### Maven Dependency

Add the starter to your Spring Boot project:

```xml

<dependencies>
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
    <artifactId>process-engine-adapter-cib-seven-embedded-spring-boot-starter</artifactId>
    <version>${project.version}</version>
  </dependency>

  <!-- CIB Seven Spring Boot Starter -->
  <dependency>
    <groupId>org.cibseven.bpm.springboot</groupId>
    <artifactId>cibseven-bpm-spring-boot-starter</artifactId>
    <version>2.1.0</version>
  </dependency>
</dependencies>
```

### Application Configuration (application.yml)

Complete configuration example:

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        cib-seven-embedded:
          enabled: true                    # Enable/disable adapter

          service-tasks:
            delivery-strategy: embedded_scheduled
            worker-id: my-worker-id        # Required: unique worker identifier
            lock-time-in-seconds: 10       # How long to lock fetched tasks
            max-task-count: 100            # Max tasks to fetch per poll
            retry-timeout-in-seconds: 30   # Retry timeout for failed tasks
            schedule-delivery-fixed-rate-in-seconds: 10  # Polling interval
            execute-initial-pull-on-startup: true        # Pull tasks on startup
            retries: 3                     # Default retry count for tasks

          user-tasks:
            delivery-strategy: embedded_scheduled
            schedule-delivery-fixed-rate-in-seconds: 5   # Polling interval
            execute-initial-pull-on-startup: true        # Pull tasks on startup
```

### Minimal Configuration

For basic usage, only `worker-id` is required:

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        cib-seven-embedded:
          enabled: true
          service-tasks:
            delivery-strategy: embedded_scheduled
            worker-id: my-worker
          user-tasks:
            delivery-strategy: embedded_scheduled
```

### Using Adapter APIs in Your Code

Once configured, inject the APIs as Spring beans:

```kotlin
@Service
class MyProcessService(
  private val startProcessApi: StartProcessApi,
  private val correlationApi: CorrelationApi,
  private val signalApi: SignalApi
) {

  fun startProcess(businessKey: String) {
    startProcessApi.startProcessByDefinitionKey(
      definitionKey = "myProcess",
      businessKey = businessKey,
      variables = mapOf("foo" to "bar")
    )
  }
}
```

## ⚡ How It Works

```
Application Startup
  ↓
Spring Boot Auto-Configuration
  ↓
Cib7EmbeddedAdapterAutoConfiguration
  ├── Creates StartProcessApi, DeploymentApi, etc. (from cib-seven-embedded-core)
  ├── Sets up Schedulers (if EMBEDDED_SCHEDULED)
  └── Configures Task Polling Bindings
  ↓
Application Ready
```

### Auto-Configuration Classes

- **`Cib7EmbeddedAdapterAutoConfiguration`** - Main configuration, creates API beans
- **`Cib7EmbeddedSchedulingAutoConfiguration`** - Scheduler setup for task polling
- **`Cib7EmbeddedServiceTaskPullStrategyAutoConfiguration`** - Service task polling
- **`Cib7EmbeddedUserTaskPullStrategyAutoConfiguration`** - User task polling
- **`Cib7EmbeddedInitialPullOnStartupAutoConfiguration`** - Initial task pull on startup

### Conditional Configuration

Beans are created only when:

- `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.enabled = true`
- CIB Seven `ProcessEngine` bean is available
- Delivery strategy matches (e.g., `EMBEDDED_SCHEDULED`)

## 🛠️ Development Setup

### Building

Build this module with dependencies:

```bash
mvn clean install -pl engine-adapter/cib-seven-embedded-spring-boot-starter -am
```

### Running Tests

Execute Spring Boot integration tests:

```bash
mvn test -pl engine-adapter/cib-seven-embedded-spring-boot-starter
```

Tests verify:

- Auto-configuration activation/deactivation based on properties
- Conditional bean creation
- Scheduler setup and task polling
- Integration with CIB Seven Spring Boot starter

### Test Application

See the [java-cib-seven-embedded example](../../examples/java-cib-seven-embedded/) for a complete working Spring Boot application using this starter.

## 📚 Dependencies

**Core:**

- **cib-seven-embedded-core** - Core adapter implementation (all APIs)
- **Spring Boot** - Auto-configuration, validation, scheduling
- **CIB Seven Spring Boot Starter** (provided) - CIB Seven process engine integration

**Build-time:**

- **spring-boot-configuration-processor** - Generates metadata for IDE autocomplete
- **kapt** (Kotlin Annotation Processing) - Processes configuration properties

**Testing:**

- **adapter-testing** - Test fixtures
- **CIB Seven test utilities** - In-memory engine for integration tests
