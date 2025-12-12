package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process

import dev.bpmcrafters.processengineapi.test.JGivenBaseIntegrationTest
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.cibseven.bpm.engine.test.mock.MockExpressionManager
import org.junit.jupiter.api.AfterEach

abstract class AbstractCib7EmbeddedApiITest(override val processTestHelper: ProcessTestHelper) : JGivenBaseIntegrationTest(processTestHelper) {

  companion object {
    const val KEY = "simple-process"
    const val START_MESSAGE = "startMessage"
    const val BPMN = "bpmn/$KEY.bpmn"

    const val USER_TASK = "user-perform-task"
    const val EXTERNAL_TASK = "execute-action-external"

    val processEngine: ProcessEngine = object : StandaloneInMemProcessEngineConfiguration() {
      init {
        history = HISTORY_AUDIT
        databaseSchemaUpdate = DB_SCHEMA_UPDATE_TRUE
        jobExecutorActivate = false
        expressionManager = MockExpressionManager()
      }
    }.buildProcessEngine()
  }

  @AfterEach
  fun tearDown() {
    processTestHelper.clearAllSubscriptions()
  }

}
