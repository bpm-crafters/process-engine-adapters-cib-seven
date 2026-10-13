package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing.Cib7EmbeddedCustomerExampleTest.Actions
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing.Cib7EmbeddedCustomerExampleTest.Assertions
import dev.bpmcrafters.processengineapi.testing.config.UseProcessTestInitializer
import dev.bpmcrafters.processengineapi.testing.jgiven.*
import org.junit.jupiter.api.Test

@UseProcessTestInitializer(Cib7EmbeddedProcessTestInitializer.QUALIFIER)
class Cib7EmbeddedCustomerExampleTest : ProcessScenarioTest<Actions, Assertions>() {

  @Test
  fun `a customer test can deploy start complete and assert a process`() {

    given {
      processIsDeployed("bpmn/customer-test-example.bpmn")
      processIsStartedByDefinition("customer-test-example")
    }

    whenever {
      processWaitsInUserTask("user_approve")
      userTaskIsCompleted(emptyMap())
    }

    then {
      processHasPassed("user_approve", "end")
      processIsFinished()
    }
  }

  open class Actions : ActionStage<Actions>()
  open class Assertions : AssertStage<Assertions>()
}
