# CIB7 Embedded Core Adapter

Core implementation of the [Process Engine API](https://github.com/bpm-crafters/process-engine-api) for CIB Seven embedded process engine.

## 🎯 Purpose

This module is the **framework-agnostic implementation** of the Process Engine API for CIB Seven:

- **Direct adapter** - No Spring Boot or other framework dependencies
- **Foundation** - Base for framework-specific adapters (e.g., Spring Boot starter)
- **Maximum control** - Programmatic configuration and lifecycle management

Use this when you need direct integration with CIB Seven without framework overhead.

## 🏗️ Architecture

This module implements the Process Engine API interfaces for CIB Seven embedded engine:

```
Process Engine API (Interface)
       ↓ implements
CIB7 Embedded Core (this module)
       ↓ uses
CIB Seven Engine (v2.1.0)
```

### Key Components

**Process Management:**

- `StartProcessApiImpl` - Start process instances
- `DeploymentApiImpl` - Deploy process definitions

**Task Management:**

- `Cib7TaskSubscriptionApiImpl` - Subscribe to external/service tasks
- `Cib7ServiceTaskCompletionApiImpl` - Complete service tasks
- `Cib7UserTaskCompletionApiImpl` - Complete user tasks
- `Cib7UserTaskModificationApiImpl` - Modify user task properties

**Message & Signal:**

- `CorrelationApiImpl` - Correlate messages to process instances
- `SignalApiImpl` - Broadcast signals

## ⚙️ Configuration

### Maven Dependency

```xml

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.cibseven.bpm</groupId>
      <artifactId>cibseven-bom</artifactId>
      <version>2.1.0</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-cib-seven-embedded-core</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>org.cibseven.bpm</groupId>
  <artifactId>cibseven-engine</artifactId>
</dependency>
</dependencies>
```

### Programmatic Usage Example

```kotlin
// Obtain CIB Seven ProcessEngine instance
val processEngine: ProcessEngine = // ... your engine initialization

// Create API implementations
val startProcessApi = StartProcessApiImpl(processEngine)
val taskSubscriptionApi = Cib7TaskSubscriptionApiImpl(processEngine)
val taskCompletionApi = Cib7ServiceTaskCompletionApiImpl(processEngine)

// Use the APIs
val processInstance = startProcessApi.startProcessByDefinitionKey(
  definitionKey = "myProcess",
  variables = mapOf("var1" to "value1")
)
```

For complete examples, see the [Spring Boot starter](../cib-seven-embedded-spring-boot-starter/) which builds on this core.

## 🛠️ Development Setup

### Building

Build this module and its dependencies:

```bash
mvn clean install -pl engine-adapter/cib-seven-embedded-core -am
```

### Running Tests

Execute integration tests:

```bash
mvn test -pl engine-adapter/cib-seven-embedded-core
```

Tests use:

- **[adapter-testing](../adapter-testing/)** - BDD test infrastructure (JGiven stages)
- **CIB Seven test utilities** - In-memory H2 database, process assertions
- **Test JAR** - This module exports test utilities for downstream projects

### Test Structure

```
src/
├── main/kotlin/        # Core adapter implementations
│   └── dev/bpmcrafters/processengineapi/adapter/cib7/embedded/
│       ├── process/    # Process start, deployment
│       ├── task/       # Task subscription, completion, modification
│       └── correlation/ # Message correlation, signals
├── test/kotlin/        # Integration tests (BDD with JGiven)
    └── dev/bpmcrafters/processengineapi/adapter/cib7/embedded/
```

## 📚 Dependencies

**Core:**

- **Process Engine API** (v1.4) - Interface definitions
- **CIB Seven Engine** (v2.1.0) - Process engine (provided scope)

**Testing:**

- **adapter-testing** - Test fixtures and JGiven stages
- **CIB Seven test utilities** - JUnit5, assertions, mockito
