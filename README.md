# Process Engine Adapter CIB Seven

[![incubating](https://img.shields.io/badge/lifecycle-INCUBATING-orange.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/actions/workflows/development.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-cib-seven-bom)](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-adapters/process-engine-adapter-cib-seven-bom)

## Purpose of the library

This library provides an adapter implementation of [Process Engine API](https://github.com/bpm-crafters/process-engine-api) for [CIB Seven](https://github.com/cibseven/cibseven) process engine.

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

## Compatibility

| Adapter Version                                                                                       | CIB Seven Version | API Version |
|-------------------------------------------------------------------------------------------------------|-------------------|-------------|
| [2025.12.1](https://github.com/bpm-crafters/process-engine-adapters-cib-seven/releases/tag/2025.12.1) | 2.1.0             | 1.4         |
