package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

import dev.bpmcrafters.processengineapi.testing.api.ProcessTestCapabilities
import java.time.Duration

internal object CibSevenRemoteProcessTestCapabilities : ProcessTestCapabilities {

  override fun supportsTimeTravel() = false

  override fun timePasses(duration: Duration): Nothing =
    throw UnsupportedOperationException("Remote CIB seven engines do not support test time travel.")
}
