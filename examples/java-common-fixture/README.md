# Java CIB Seven Common Fixture

Shared utilities and fixtures for CIB Seven Java examples.

## 🎯 Purpose

This module provides **reusable components** to reduce code duplication across Java example applications:

- **Common task handlers** - Synchronous task completion patterns
- **REST controllers** - API endpoints for process interaction
- **Data models** - Shared payload and domain objects
- **Spring configuration** - Swagger, actuator, and other common setup

This is **not a runnable application** - it's a shared library used by [java-cib-seven-embedded](../java-cib-seven-embedded/) and other examples.

## 📦 What's Included

### Task Handlers

#### AbstractSynchronousTaskHandler

Base class for synchronous external task handling:

- Automatically completes tasks after execution
- Handles exceptions and task failures
- Simplifies task handler implementation

**Usage Pattern:**

```java

@Component
public class MyTaskHandler extends AbstractSynchronousTaskHandler {

  public MyTaskHandler(ServiceTaskCompletionApi api) {
    super(api);
  }

  @Override
  public String getType() {
    return "my-task-type";
  }

  @Override
  protected void executeTask(ServiceTask task) {
    // Your business logic here
    // Task is automatically completed after execution
  }
}
```

**Concrete Implementations:**

- **SendingTaskHandler** - Sends data (demonstrates outbound task)
- **ReadTaskHandler** - Reads data (demonstrates inbound task)
- **ExecuteActionTaskHandler** - Executes actions (demonstrates side effects)

### Adapters

#### UserTaskAdapter

Manages an in-memory pool of user tasks:

- **Subscribe to user tasks** - Listens for new user tasks
- **Store in memory** - Maintains a list of open tasks
- **Query tasks** - Retrieve tasks via REST API
- **Support completion** - Integrates with Process Engine API

#### WorkflowAdapter

Process management adapter for common workflow operations:

- **Start processes** - Via REST endpoints
- **Correlate messages** - Message correlation to process instances
- **Handle signals** - Broadcast signals to processes

### REST Controllers

#### SimpleServiceTaskController

REST API for process operations:

- `POST /simple-service-task/process` - Start process
- `POST /simple-service-task/user-task/{id}/complete` - Complete user task
- `GET /simple-service-task/user-tasks` - List open user tasks
- `POST /simple-service-task/message` - Correlate message

### Data Models

**Process Payloads:**

- `ComplexPayload` - Example complex data structure
- `SomeComplexObject` - Nested object example
- `EntryValue` - Key-value entry model
- `SomeEnum` - Enumeration example

**Constants:**

- `SimpleProcessWorkflowConst` - Process definition keys, message names, task types

### Spring Configuration

#### CommonFixtureAutoconfiguration

Auto-configures common components:

- **Swagger/OpenAPI** - API documentation at `/swagger-ui/index.html`
- **Spring Actuator** - Health checks, metrics at `/actuator`
- **Prometheus** - Metrics export for monitoring
- **Logging** - Configured with SLF4J + Lombok's `@Slf4j`

## 🚀 Usage in Examples

### Maven Dependency

Add to your example application's `pom.xml`:

```xml

<dependency>
  <groupId>dev.bpm-crafters.process-engine-examples</groupId>
  <artifactId>process-engine-api-example-java-cib-seven-common-fixture</artifactId>
  <version>${project.version}</version>
</dependency>
```

### Enable Auto-Configuration

In your Spring Boot application, the fixture's auto-configuration is automatically enabled:

```java

@SpringBootApplication
public class MyExampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(MyExampleApplication.class, args);
  }
}
```

Components from this module are available as Spring beans.

### Example: Custom Task Handler

Extend `AbstractSynchronousTaskHandler` for your own task types:

```java

@Component
@Slf4j
public class MyCustomTaskHandler extends AbstractSynchronousTaskHandler {

  private final MyService myService;

  public MyCustomTaskHandler(
    ServiceTaskCompletionApi completionApi,
    MyService myService
  ) {
    super(completionApi);
    this.myService = myService;
  }

  @Override
  public String getType() {
    return "my-custom-task";  // Matches task type in BPMN
  }

  @Override
  protected void executeTask(ServiceTask task) {
    log.info("Executing custom task: {}", task.getId());
    myService.doWork(task.getPayload());
    // Task is automatically completed by AbstractSynchronousTaskHandler
  }
}
```

## 📂 Package Structure

```
dev.bpmcrafters.example.common/
├── adapter/
│   ├── in/                    # Inbound ports (handlers, controllers)
│   │   ├── process/          # Task handlers, payload models
│   │   └── rest/             # REST controllers
│   ├── out/                   # Outbound ports (adapters)
│   │   └── process/          # Process engine adapters
│   └── shared/                # Shared constants
└── CommonFixtureAutoconfiguration.java  # Spring Boot auto-config
```

**Hexagonal Architecture:**

- `in/` - Adapters that handle incoming requests (driving side)
- `out/` - Adapters that call external systems (driven side)
- `shared/` - Shared utilities and constants

## 🔗 See Also

- **[java-cib-seven-embedded](../java-cib-seven-embedded/)** - Complete Spring Boot example using these fixtures
- **[Process Engine API](https://github.com/bpm-crafters/process-engine-api)** - API interfaces implemented by adapters

## 🤝 Contributing

To add new shared components:

1. **Identify reusable patterns** - Look for code duplicated across examples
2. **Keep it generic** - Components should work for multiple use cases
3. **Document usage** - Add JavaDoc with examples
4. **Update this README** - List new components in [What's Included](#whats-included)

### Building

```bash
mvn clean install -pl examples/java-common-fixture
```

### Testing

This module is tested indirectly through example applications. Add integration tests to example projects that use these fixtures.
