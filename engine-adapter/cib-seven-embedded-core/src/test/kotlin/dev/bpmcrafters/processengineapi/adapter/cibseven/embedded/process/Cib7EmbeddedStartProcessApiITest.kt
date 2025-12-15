package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.AbstractCib7EmbeddedApiITest.Companion.BPMN
import io.toolisticon.testing.jgiven.THEN
import io.toolisticon.testing.jgiven.WHEN
import org.cibseven.bpm.engine.test.Deployment
import org.cibseven.bpm.engine.test.junit5.ProcessEngineExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


@Deployment(resources = [BPMN])
class Cib7EmbeddedStartProcessApiITest : AbstractCib7EmbeddedApiITest(Cib7EmbeddedProcessTestHelper(cibSeven.processEngine)) {

  companion object {
    @RegisterExtension
    val cibSeven: ProcessEngineExtension = ProcessEngineExtension.builder().useProcessEngine(processEngine).build()
  }

  @Test
  fun `should start process by definition without payload`() {
    WHEN
      .`start process by definition`(KEY)

    THEN
      .`we should have a running process`()
  }

  @Test
  fun `should start process by definition with payload`() {
    WHEN
      .`start process by definition with payload`(KEY, "key" to "value")

    THEN
      .`we should have a running process`()
  }

  @Test
  fun `should start process via message without payload`() {
    WHEN
      .`start process by message`(START_MESSAGE)

    THEN
      .`we should have a running process`()
  }

  @Test
  fun `should start process via message with payload`() {
    WHEN
      .`start process by message with payload`(START_MESSAGE, "key" to "value")

    THEN
      .`we should have a running process`()
  }

}
