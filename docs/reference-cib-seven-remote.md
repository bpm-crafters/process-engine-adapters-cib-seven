---
title: Process Engine Adapter CIB Seven Remote
---

# Decisions and supported features

The remote adapter implements the same `process-engine-api` ports as the embedded adapter, but talks
to a standalone CIB Seven engine over its REST API (`/engine-rest`) instead of the embedded Java API.
It mirrors the [Camunda 7 remote adapter](https://github.com/bpm-crafters/process-engine-adapters-camunda-7),
adapted to CIB Seven.

## REST client

The adapter does **not** depend on the community `cibseven-rest-client-java`. Instead it generates a
typed client at build time from CIB Seven's official OpenAPI document, bundled in
`org.cibseven.bpm:cibseven-engine-rest-openapi` and pinned to the engine version, so the client and
the engine never drift. The generated client is `apache-httpclient` based (package
`org.cibseven.community.rest.client.*`).

To override the client, provide your own `org.cibseven.community.rest.client.invoker.ApiClient` bean
(or individual `*ApiClient` beans) — the auto-configured defaults are annotated with
`@ConditionalOnMissingBean`, so your beans take precedence.

## Message Correlation

Correlation API implementation supports the following restrictions:

| Key                       | Value                | Description                                                                                                    |
|---------------------------|----------------------|---------------------------------------------------------------------------------------------------------------|
| `tenantId`                | The id of the tenant | Correlates messages for process instances with given tenant id.                                               |
| `withoutTenantId`         | none                 | If restriction is present, correlate only with process instances without tenant id.                           |
| `useGlobalCorrelationKey` | `true` or `false`    | If set to false (default if not set), correlate using local variables, use global process variable otherwise. |

## Variable serialization

Typed process variables are (de)serialized via a value mapper backed by
[Spin](https://docs.cibseven.org). JSON is the default serialization format for object values. Add
`org.cibseven.bpm:cibseven-engine-plugin-spin` (and the matching Spin data formats) to the classpath
for object/JSON/XML variable support; the Spin-based serializers activate conditionally when Spin is present.

## Deployment

The generated `DeploymentApiClient.createDeployment` only accepts a single file, which cannot represent
a multi-resource bundle deployment. The adapter therefore builds the `multipart/form-data` deployment
request itself — one `data` part per resource — and executes it with the configured `ApiClient`'s HTTP
client, so base URL, authentication and interceptors stay consistent.

## Task delivery

Two service-task delivery strategies are supported, selectable via
`…adapter.cib-seven-remote.service-tasks.delivery-strategy`:

- **`remote_scheduled`** (pull): the adapter fetch-and-locks external tasks over REST on its own scheduler.
- **`remote_subscribed`** (subscribe): the official CIB Seven external task client long-polls external tasks.
  Requires `org.cibseven.bpm:cibseven-external-task-client` on the classpath.

User tasks are delivered via `remote_scheduled` (pull) by querying the task list over REST.

## Task client operations

In CIB Seven's REST API the task identity-link and (local) variable operations live on their own OpenAPI
tags, so the generated client splits them into dedicated clients (`TaskApiClient`,
`TaskIdentityLinkApiClient`, `TaskLocalVariableApiClient`, `TaskVariableApiClient`). The adapter wires and
uses each of them accordingly.
