# Process Engine Adapter CIB seven

[![incubating](https://img.shields.io/badge/lifecycle-INCUBATING-orange.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/actions/workflows/development.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-cib-seven-bom)](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-cib-seven-bom)

## 🎯 Purpose of the library

This library provides an adapter implementation of [Process Engine API](https://github.com/bpm-crafters/process-engine-api)
for [CIB seven](https://github.com/cibseven/cibseven) process engine, enabling a consistent API across different process engines.

## 🏗️ Architecture

The adapter follows a **3-layer architecture**:

```
┌───────────────────────────────────────────────────┐
│   Spring Boot Applications                        │  ← Your application
│   (use Process Engine API)                        │
├───────────────────────────────────────────────────┤
│   cib-seven-embedded-spring-boot-starter          │  ← Spring Boot integration
│   (Auto-configuration)                            │
├───────────────────────────────────────────────────┤
│   cib-seven-embedded-core                         │  ← Core adapter implementation
│   (Process Engine API → CIB Seven)                │
├───────────────────────────────────────────────────┤
│   CIB Seven Engine (v2.1.0)                       │  ← Process engine
└───────────────────────────────────────────────────┘
                      ↑
         cib-seven-testing ← Test infrastructure (used across layers)
```

**Layer Responsibilities:**

- **cib-seven-testing** - Test fixtures, JGiven stages, and utilities for testing
- **cib-seven-embedded-core** - Framework-agnostic Process Engine API implementation
- **cib-seven-embedded-spring-boot-starter** - Spring Boot auto-configuration and delivery strategies

## 📦 Anatomy

The library consists of the following Maven modules:

- **[process-engine-adapter-cib-seven-testing](./engine-adapter/adapter-testing/README.md)**: Test fixtures and utilities for testing CIB7 adapters
  _Use when: Writing tests for adapter implementations or custom integrations_

- **[process-engine-adapter-cib-seven-embedded-core](./engine-adapter/cib-seven-embedded-core/README.md)**: Core implementation of the CIB7 embedded adapter
  _Use when: Building non-Spring applications or custom framework integrations_

- **[process-engine-adapter-cib-seven-embedded-spring-boot-starter](./engine-adapter/cib-seven-embedded-spring-boot-starter/README.md)**: Spring Boot
  auto-configuration for CIB7 embedded adapter
  _Use when: Building Spring Boot applications (recommended for most users)_

## 🚀 Usage

If you want to start usage, please add the BOM to your Maven project and add the corresponding adapter implementation:

```xml

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
      <artifactId>process-engine-adapter-cib-seven-bom</artifactId>
      <version>${process-engine-adapter-cib-seven.version}</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-cib-seven-embedded-spring-boot-starter</artifactId>
</dependency>
</dependencies>
```

### Configuration

Configure the adapter in your `application.yml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      adapter:
        cib-seven-embedded:
          enabled: true
          service-tasks:
            delivery-strategy: embedded_scheduled
            worker-id: your-worker-id
            lock-time-in-seconds: 10
          user-tasks:
            delivery-strategy: embedded_scheduled
```

## 🔗 Compatibility

| Adapter Version                                                                                       | CIB seven Version | API Version |
|-------------------------------------------------------------------------------------------------------|-------------------|-------------|
| [2025.12.1](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/releases/tag/2025.12.1) | 2.1.0             | 1.4         |

## 💡 Examples

See the [examples](./examples/) directory for complete working applications:

- **[java-cib-seven-embedded](./examples/java-cib-seven-embedded/)** - Spring Boot application with REST API, Swagger UI, and integration tests

## 🤝 Contribution

### Prerequisites

- **Java 17+**
- **Maven 3.9+**

### Build the Project

Full build with tests:

```bash
mvn clean install
```

Quick build without tests:

```bash
mvn clean install -DskipTests
```

### Running Tests

Execute all tests:

```bash
mvn test
```

Test specific module:

```bash
mvn test -pl engine-adapter/cib-seven-embedded-core
```

### Module-Specific Development

For detailed contribution guidelines per module, see the individual README files:

- [adapter-testing](./engine-adapter/adapter-testing/README.md#contributing)
- [cib-seven-embedded-core](./engine-adapter/cib-seven-embedded-core/README.md#contributing)
- [cib-seven-embedded-spring-boot-starter](./engine-adapter/cib-seven-embedded-spring-boot-starter/README.md#contributing)

### Code Style

This project follows Kotlin/Java conventions and uses `.editorconfig` for consistent formatting. Please ensure your IDE respects these settings.

## 📚 Module Documentation

| Module                                     | Description                        | Documentation                                                               |
|--------------------------------------------|------------------------------------|-----------------------------------------------------------------------------|
| **adapter-testing**                        | Test fixtures and JGiven utilities | [README](./engine-adapter/adapter-testing/README.md)                        |
| **cib-seven-embedded-core**                | Core adapter implementation        | [README](./engine-adapter/cib-seven-embedded-core/README.md)                |
| **cib-seven-embedded-spring-boot-starter** | Spring Boot auto-configuration     | [README](./engine-adapter/cib-seven-embedded-spring-boot-starter/README.md) |
| **examples**                               | Working example applications       | [README](./examples/README.md)                                              |

## 🔗 Links

- **[Process Engine API](https://github.com/bpm-crafters/process-engine-api)** - Unified process engine API specification
- **[CIB Seven](https://github.com/cibseven/cibseven)** - Open-source BPMN process engine
- **[Examples](./examples/)** - Complete working applications
