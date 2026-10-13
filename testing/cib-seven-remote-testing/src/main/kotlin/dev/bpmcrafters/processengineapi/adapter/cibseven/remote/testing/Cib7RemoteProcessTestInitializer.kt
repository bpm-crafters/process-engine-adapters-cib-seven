package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializationRequest
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializer
import org.springframework.beans.factory.getBean
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

private const val ENGINE_PORT = 8080
private const val ADAPTER_PROPERTIES = "dev.bpm-crafters.process-api.adapter.cib-seven-remote"

class Cib7RemoteProcessTestInitializer : ProcessTestInitializer {

  companion object {
    const val QUALIFIER = "cib7remote"
  }

  override fun qualifier() = QUALIFIER

  override fun initialize(request: ProcessTestInitializationRequest): Cib7RemoteProcessTestContext {
    val engineConfiguration = request.testClass.getAnnotation(Cib7RemoteProcessTestEngine::class.java)
    val configuredUrl = engineConfiguration?.url?.takeIf { it.isNotBlank() }
    val container = if (configuredUrl == null) startContainer(engineConfiguration?.image ?: Cib7RemoteProcessTestEngine.DEFAULT_IMAGE) else null
    val engineRestUrl = configuredUrl ?: "http://${container!!.host}:${container.firstMappedPort}/engine-rest"

    val workerId = "process-test-${request.testClass.simpleName}"
    val application = startApplication(engineRestUrl, workerId, request.configuration)

    return Cib7RemoteProcessTestContext(
      application = application,
      stopEngine = { container?.stop() },
      runtimeApis = Cib7RemoteProcessTestRuntimeApis(
        startProcessApi = application.getBean(),
        taskSubscriptionApi = application.getBean(),
        userTaskCompletionApi = application.getBean(),
        userTaskModificationApi = application.getBean(),
        serviceTaskCompletionApi = application.getBean(),
        correlationApi = application.getBean(),
        signalApi = application.getBean(),
        deploymentApi = application.getBean(),
      ),
      workerId = workerId,
    )
  }

  private fun startContainer(image: String): GenericContainer<*> =
    GenericContainer(image)
      .withExposedPorts(ENGINE_PORT)
      .waitingFor(Wait.forHttp("/engine-rest/engine/").forPort(ENGINE_PORT))
      .apply { start() }

  private fun startApplication(
    engineRestUrl: String,
    workerId: String,
    initializerConfiguration: Map<String, String>,
  ): ConfigurableApplicationContext =
    SpringApplicationBuilder(Cib7RemoteProcessTestApplication::class.java)
      .web(WebApplicationType.NONE)
      .properties(
        mapOf(
          "$ADAPTER_PROPERTIES.enabled" to "true",
          "$ADAPTER_PROPERTIES.client.base-url" to engineRestUrl,
          "$ADAPTER_PROPERTIES.service-tasks.worker-id" to workerId,
          "$ADAPTER_PROPERTIES.service-tasks.delivery-strategy" to "REMOTE_SCHEDULED",
          "$ADAPTER_PROPERTIES.user-tasks.delivery-strategy" to "REMOTE_SCHEDULED",
        ) + initializerConfiguration
      )
      .run()
}

@SpringBootApplication
private class Cib7RemoteProcessTestApplication
