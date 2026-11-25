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

class CibSevenRemoteProcessTestContext(
  private val application: ConfigurableApplicationContext,
  private val stopEngine: () -> Unit,
  private val runtimeApis: CibSevenRemoteProcessTestRuntimeApis,
  workerId: String,
) : ProcessTestContext {

  private val polling = ProcessTestPollingConfiguration(Duration.ofMillis(100), Duration.ofSeconds(10))
  private val queryApi = CibSevenRemoteProcessTestQueryApi(application, workerId)
  private val assertApi = CibSevenRemoteProcessTestAssertApi(application, queryApi, polling)
  private val payloadApi = CibSevenRemoteProcessTestPayloadApi(application)

  override fun qualifier() = CibSevenRemoteProcessTestInitializer.QUALIFIER
  override fun runtime() = runtimeApis
  override fun query(): ProcessTestQueryApi = queryApi
  override fun assertions(): ProcessTestAssertApi = assertApi
  override fun payloads(): ProcessTestPayloadApi = payloadApi
  override fun capabilities(): ProcessTestCapabilities = CibSevenRemoteProcessTestCapabilities
  override fun diagnostics() = ProcessTestDiagnostics(polling)

  override fun close() {
    application.close()
    stopEngine()
  }
}
