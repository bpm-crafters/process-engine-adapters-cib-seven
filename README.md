# Process Engine Adapter CIB Seven

[![incubating](https://img.shields.io/badge/lifecycle-INCUBATING-orange.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/actions/workflows/development.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-cib-seven-bom)](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-cib-seven-bom)

## Purpose of the library

This library provides an adapter implementation of [Process Engine API](https://github.com/bpm-crafters/process-engine-api) for [CIB Seven](https://github.com/cibseven/cibseven) process engine.

## 📚 Documentation

The documentation can be found [here](https://bpm-crafters.github.io/process-engine-api-docs/stable/) or in its
respective [repository](https://github.com/bpm-crafters/process-engine-api-docs).

## Compatibility

| Adapter Version                                                                                        | CIB Seven Version | API Version | Spring Boot |
|--------------------------------------------------------------------------------------------------------|-------------------|-------------|-------------|
| next release                                                                                            | 2.2.0             | 1.6         | 3.5 + 4.0   |
| [2026.04.1](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/releases/tag/2026.04.1)  | 2.1.0             | 1.5         | 3.5         |
| [2026.02.1](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/releases/tag/2026.02.1)  | 2.1.0             | 1.5         | 3.5         |
| [2025.12.1](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/releases/tag/2025.12.1)  | 2.1.0             | 1.4         | 3.5         |

### Spring Boot 4 vs. Spring Boot 3

The adapter starter is built against Spring Boot 4 but remains compatible with Spring Boot 3.5.
This is verified in every CI build: the `examples/java-cib-seven-embedded-sb3` example boots the
full application (embedded engine, webapp and adapter auto-configuration) on Spring Boot 3.5 as
part of `mvn verify`. The CIB seven engine starters are `provided` dependencies of the adapter,
so you pick the line matching your application:

- **Spring Boot 4**: use the CIB seven starters with the `-4` suffix (e.g.
  `cibseven-bpm-spring-boot-starter-4`, `cibseven-bpm-spring-boot-starter-webapp-4`). Note that since
  the Spring Boot 4 module split, the DataSource / transaction manager auto-configurations live in
  `spring-boot-jdbc`, which the CIB seven starter-4 does not bring transitively — add
  `spring-boot-starter-jdbc` (or `-data-jpa`) to your application.
- **Spring Boot 3.5**: use the CIB seven starters without the suffix (e.g.
  `cibseven-bpm-spring-boot-starter`).



## Anatomy

- `process-engine-adapter-cib-seven-testing`: Test fixtures and utilities for testing CIB Seven adapters
- `process-engine-adapter-cib-seven-embedded-core`: CIB Seven Embedded Adapter implementation
- `process-engine-adapter-cib-seven-embedded-spring-boot-starter`: CIB Seven Embedded Adapter Spring Boot Starter
- `process-engine-adapter-cib-seven-bom`: Maven BOM containing dependency definitions.

## Usage

Add the BOM to your Maven project and add the corresponding adapter implementation:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
  <artifactId>process-engine-adapter-cib-seven-bom</artifactId>
  <version>${process-engine-adapter-cib-seven.version}</version>
  <scope>import</scope>
  <type>pom</type>
</dependency>
```

