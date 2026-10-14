package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestAssertApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestContext
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestDiagnostics
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestPayloadApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

class Cib7RemoteProcessTestContext(
  private val application: ConfigurableApplicationContext,
  private val stopEngine: () -> Unit,
  private val runtimeApis: Cib7RemoteProcessTestRuntimeApis,
  workerId: String,
) : ProcessTestContext {

  private val polling = ProcessTestPollingConfiguration(Duration.ofMillis(100), Duration.ofSeconds(10))
  private val queryApi = Cib7RemoteProcessTestQueryApi(application, workerId)
  private val assertApi = Cib7RemoteProcessTestAssertApi(application, queryApi, polling)
  private val payloadApi = Cib7RemoteProcessTestPayloadApi(application)

  override fun qualifier() = Cib7RemoteProcessTestInitializer.QUALIFIER
  override fun runtime() = runtimeApis
  override fun query(): ProcessTestQueryApi = queryApi
  override fun assertions(): ProcessTestAssertApi = assertApi
  override fun payloads(): ProcessTestPayloadApi = payloadApi
  override fun capabilities(): ProcessTestCapabilities = Cib7RemoteProcessTestCapabilities
  override fun diagnostics() = ProcessTestDiagnostics(polling)

  override fun close() {
    application.close()
    stopEngine()
  }
}
