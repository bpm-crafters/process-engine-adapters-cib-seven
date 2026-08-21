package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * A standalone CIB seven engine (the "run" distribution) exposing the REST API on `/engine-rest`.
 */
class Cib7RunTestContainer(tag: String) : GenericContainer<Cib7RunTestContainer>("cibseven/cibseven:$tag") {

  init {
    withExposedPorts(8080)
    waitingFor(
      Wait
        .forHttp("/engine-rest/engine/")
        .forPort(8080)
    )
  }

}
