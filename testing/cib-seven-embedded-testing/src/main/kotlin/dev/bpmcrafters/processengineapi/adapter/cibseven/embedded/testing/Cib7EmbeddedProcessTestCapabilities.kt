package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.impl.util.ClockUtil
import java.time.Duration
import java.util.Date

internal class Cib7EmbeddedProcessTestCapabilities(private val engine: ProcessEngine) : ProcessTestCapabilities {

  override fun supportsTimeTravel() = true

  override fun timePasses(duration: Duration) {
    ClockUtil.setCurrentTime(Date.from(ClockUtil.getCurrentTime().toInstant().plus(duration)))
    executeDueJobs()
  }

  private fun executeDueJobs() {
    val managementService = engine.managementService
    managementService.createJobQuery().executable().list().forEach { managementService.executeJob(it.id) }
  }
}
