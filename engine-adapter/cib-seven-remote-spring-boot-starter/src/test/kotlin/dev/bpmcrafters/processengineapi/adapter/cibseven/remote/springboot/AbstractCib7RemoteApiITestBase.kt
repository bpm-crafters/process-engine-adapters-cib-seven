package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.test.JGivenBaseIntegrationTest
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
  classes = [Cib7RemoteTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext
@Testcontainers
abstract class AbstractCib7RemoteApiITestBase(
  override val processTestHelper: ProcessTestHelper
) : JGivenBaseIntegrationTest(processTestHelper) {

  companion object {
    const val KEY = "simple-process"
    const val START_MESSAGE = "startMessage"
    const val BPMN = "bpmn/$KEY.bpmn"
    const val DMN = "decision/main_decision.dmn"
    const val USER_TASK = "user-perform-task"
    const val EXTERNAL_TASK = "execute-action-external"

    @JvmStatic
    @Container
    val cibSevenContainer = Cib7RunTestContainer("run-latest")

    @JvmStatic
    @DynamicPropertySource
    fun configure(registry: DynamicPropertyRegistry) {
      val engineRest = "http://localhost:${cibSevenContainer.firstMappedPort}/engine-rest"
      // REST client used by the adapter (pull delivery, completion, correlation, deployment, DMN)
      registry.add("dev.bpm-crafters.process-api.adapter.cib-seven-remote.client.base-url") { engineRest }
      // Official external task client (subscribe delivery)
      registry.add("cibseven.bpm.client.base-url") { "$engineRest/" }
      registry.add("camunda.bpm.client.base-url") { "$engineRest/" }
    }
  }

  @Autowired
  lateinit var deploymentApi: DeploymentApi

  @BeforeEach
  fun setUp() {
    deploymentApi
      .deploy(
        DeployBundleCommand(
          listOf(
            NamedResource.fromClasspath(BPMN),
            NamedResource.fromClasspath(DMN),
          )
        )
      ).get()
  }

  @AfterEach
  fun tearDown() {
    processTestHelper.clearAllSubscriptions()
  }
}
