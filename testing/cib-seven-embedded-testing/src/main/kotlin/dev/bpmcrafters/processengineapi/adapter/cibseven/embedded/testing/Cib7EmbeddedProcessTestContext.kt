package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestAssertApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestContext
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestDiagnostics
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestPayloadApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.impl.util.ClockUtil
import java.time.Duration

class Cib7EmbeddedProcessTestContext(
  private val processEngine: ProcessEngine,
  private val runtimeApis: Cib7EmbeddedProcessTestRuntimeApis,
  metadata: ProcessDefinitionMetaDataResolver,
  workerId: String,
) : ProcessTestContext {

  private val polling = ProcessTestPollingConfiguration(Duration.ofMillis(25), Duration.ofSeconds(5))
  private val queryApi = Cib7EmbeddedProcessTestQueryApi(processEngine, metadata, workerId)
  private val assertApi = Cib7EmbeddedProcessTestAssertApi(processEngine, queryApi, polling)
  private val payloadApi = Cib7EmbeddedProcessTestPayloadApi(processEngine)
  private val capabilities = Cib7EmbeddedProcessTestCapabilities(processEngine)

  override fun qualifier() = Cib7EmbeddedProcessTestInitializer.QUALIFIER
  override fun runtime() = runtimeApis
  override fun query(): ProcessTestQueryApi = queryApi
  override fun assertions(): ProcessTestAssertApi = assertApi
  override fun payloads(): ProcessTestPayloadApi = payloadApi
  override fun capabilities(): ProcessTestCapabilities = capabilities
  override fun diagnostics() = ProcessTestDiagnostics(polling)

  override fun close() {
    ClockUtil.reset()
    processEngine.close()
  }
}
