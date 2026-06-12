# Java Example to demonstrate usage of process API (Spring Boot 3)

This example is the Spring Boot 3 twin of [`java-cib-seven-embedded`](../java-cib-seven-embedded):
it runs the same application and tests on Spring Boot 3.5 with the CIB seven Spring Boot 3 starters
(no `-4` suffix), proving in every CI build that the adapter starter — although built against
Spring Boot 4 — remains compatible with Spring Boot 3.

## 🔄 Process

![Service Task Process](src/main/resources/simple-process.png)

## 🚀 How to run

This module exists primarily for the automated compatibility verification (see
`ApplicationStartsITest` and `SimpleProcessTest`). For manually exploring the application
(Swagger UI, IntelliJ HTTP client demo scripts), use the Spring Boot 4 example
[`java-cib-seven-embedded`](../java-cib-seven-embedded) — the application itself is identical.
