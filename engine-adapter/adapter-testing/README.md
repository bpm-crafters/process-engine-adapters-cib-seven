# CIB7 Adapter Test Fixtures

Test infrastructure and utilities for testing CIB7 process engine adapters.

## 🎯 Purpose

This module provides reusable test fixtures and utilities to simplify testing of CIB7 adapters:

- **Reduces boilerplate** - Common test setup and utilities are centralized
- **Ensures consistency** - All adapter tests follow the same patterns
- **Improves readability** - BDD-style (Given-When-Then) test structure using JGiven

## 📦 What's Included

### BDD Test Infrastructure

- **JGiven integration** - Base classes for Given-When-Then style tests
  - `JGivenBaseIntegrationTest` - Base test class for Spring integration tests
  - `BaseGivenWhenStage` - Reusable Given/When stages
  - `BaseThenStage` - Reusable Then (assertion) stages
- **ProcessTestHelper** - Utilities for process testing scenarios

### Testing Libraries

- **JGiven** (v${jgiven.version}) - BDD testing framework with Kotlin support
- **Awaitility** - Async operation testing with fluent API
- **TestContainers** - Container-based integration testing
- **AssertJ** - Fluent assertions
- **JUnit 5** - Test execution framework
- **H2 Database** - In-memory database for testing

### Additional Utilities

- **Jackson** - JSON serialization/deserialization for test data
- **HttpClient5** - HTTP client for REST API testing
- **Spring Boot Test** - Spring context management in tests

## 🚀 Usage

### Maven Dependency

Add to your `pom.xml` test dependencies:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-cib-seven-testing</artifactId>
  <version>${project.version}</version>
  <scope>test</scope>
</dependency>
```

### Example: BDD-Style Integration Test

```kotlin
@SpringBootTest
class MyAdapterTest : JGivenBaseIntegrationTest<MyGivenStage, MyWhenStage, MyThenStage>() {

  @Test
  fun `should complete external task`() {
    given().aProcessIsDeployed()
      .and().aProcessInstanceIsStarted()

    `when`().anExternalTaskIsRetrieved()
      .and().theTaskIsCompleted()

    then().theProcessInstanceShouldBeCompleted()
  }
}
```

### See Also

- Integration tests in [cib-seven-embedded-core](../cib-seven-embedded-core/src/test/) use these fixtures
- [JGiven documentation](http://jgiven.org/) for BDD testing patterns

## 🤝 Contributing

### Adding New Test Fixtures

1. **Base stages** - Extend `BaseGivenWhenStage` or `BaseThenStage` for reusable steps
2. **Test helpers** - Add utility methods to `ProcessTestHelper` for common operations
3. **Documentation** - Add JavaDoc/KDoc explaining the fixture's purpose

### Running Tests

This module itself has minimal tests as it's a testing library:

```bash
mvn test -pl engine-adapter/adapter-testing
```

### Testing Guidelines

- **Keep fixtures generic** - They should be reusable across different adapter implementations
- **Follow BDD conventions** - Use clear Given-When-Then structure
- **Document examples** - Show how to use new fixtures in JavaDoc/KDoc
