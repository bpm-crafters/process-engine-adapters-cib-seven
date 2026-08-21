# Example: Java CIB seven Remote

A worker application that drives a **standalone (remote) CIB seven engine** over its REST API using
the `process-engine-adapter-cib-seven-remote-spring-boot-starter`. It reuses the process-agnostic
worker logic from `java-common-fixture` (the same use cases, task handlers and REST controller as the
embedded example) — only the adapter and its configuration differ.

Unlike the embedded example, this application does **not** run an engine itself; it connects to one
over `/engine-rest`.

## Run it

1. Start a CIB seven engine (the "run" distribution exposes the REST API on `/engine-rest`):

   ```bash
   docker run --rm -p 8080:8080 cibseven/cibseven:run-latest
   ```

2. Start this application (it listens on port `8090`):

   ```bash
   ../../mvnw -pl examples/java-cib-seven-remote spring-boot:run
   ```

   Point it at a different engine by overriding
   `dev.bpm-crafters.process-api.adapter.cib-seven-remote.client.base-url` (and, for the
   `remote_subscribed` strategy, `cibseven.bpm.client.base-url`).

3. On startup the process is deployed to the remote engine and the worker begins pulling service and
   user tasks. Explore the REST endpoints via Swagger UI at <http://localhost:8090/swagger-ui.html>.

## Delivery strategies

- `remote_scheduled` (default here) — the adapter fetch-and-locks external tasks over REST on a schedule.
- `remote_subscribed` — the official CIB seven external task client long-polls external tasks. Switch
  `…adapter.cib-seven-remote.service-tasks.delivery-strategy` to `remote_subscribed` to use it.
