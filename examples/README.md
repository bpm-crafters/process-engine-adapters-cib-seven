# CIB Seven Adapter Examples

Example applications demonstrating usage of the CIB Seven process engine adapter.

## 💡 Available Examples

### java-cib-seven-embedded ([README](./java-cib-seven-embedded/README.md))

**Full-featured Spring Boot application** showcasing the CIB Seven adapter with REST API, Swagger UI, and process testing.

**Features:**

- Process Engine API integration via Spring Boot starter
- REST endpoints for process operations (start, complete tasks, correlate messages)
- Interactive Swagger UI for API testing
- BDD-style integration tests with JGiven
- Synchronous task handling patterns
- In-memory user task pool

**Quick Start:**

```bash
# Build and run
mvn clean install -pl examples/java-cib-seven-embedded -am
cd examples/java-cib-seven-embedded
mvn spring-boot:run
```

**Access:**

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Actuator: http://localhost:8080/actuator
- Port: 8080

### java-common-fixture ([README](./java-common-fixture/README.md))

**Shared utilities and fixtures** used by Java examples.

**What's Included:**

- `AbstractSynchronousTaskHandler` - Base class for synchronous task completion
- In-memory user task pool
- Common REST controllers and adapters
- Swagger/OpenAPI configuration
- Spring Boot actuator setup

**Note:** This is not a runnable application - it's a shared library dependency.

## 🚀 Running the Examples

### Prerequisites

- **Java 17+**
- **Maven 3.9+**

### Build All Examples

From the project root:

```bash
mvn clean install
```

### Run Specific Example

Build and run the embedded example:

```bash
mvn spring-boot:run -pl examples/java-cib-seven-embedded
```

### Testing Examples

Run integration tests:

```bash
mvn test -pl examples/java-cib-seven-embedded
```

## 🔗 See Also

- **[Main README](../README.md)** - Project overview and module documentation
- **[Process Engine API](https://github.com/bpm-crafters/process-engine-api)** - API specification
- **[CIB Seven](https://github.com/cibseven/cibseven)** - Open-source process engine
