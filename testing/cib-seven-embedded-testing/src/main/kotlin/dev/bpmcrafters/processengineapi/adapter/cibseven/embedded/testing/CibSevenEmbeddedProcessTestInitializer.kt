package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.modification.CibSevenUserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.subscription.CibSevenTaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializationRequest
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializer
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.ProcessEngineConfiguration
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.cibseven.bpm.engine.test.mock.MockExpressionManager

class CibSevenEmbeddedProcessTestInitializer : ProcessTestInitializer {

  companion object {
    const val QUALIFIER = "cib-seven-embedded"
  }

  override fun qualifier() = QUALIFIER

  override fun initialize(request: ProcessTestInitializationRequest): CibSevenEmbeddedProcessTestContext {
    val engine = buildInMemoryEngine()
    val commandExecutor = EngineCommandExecutor { it.run() }
    val subscriptions = InMemSubscriptionRepository()
    val metadata = CachingProcessDefinitionMetaDataResolver(engine.repositoryService)
    val workerId = "process-test-${request.testClass.simpleName}"

    return CibSevenEmbeddedProcessTestContext(
      processEngine = engine,
      runtimeApis = CibSevenEmbeddedProcessTestRuntimeApis(
        startProcessApi = StartProcessApiImpl(engine.runtimeService, engine.repositoryService, commandExecutor, metadata),
        taskSubscriptionApi = CibSevenTaskSubscriptionApiImpl(subscriptions),
        userTaskCompletionApi = CibSevenUserTaskCompletionApiImpl(engine.taskService, subscriptions),
        userTaskModificationApi = CibSevenUserTaskModificationApiImpl(engine.taskService),
        serviceTaskCompletionApi = CibSevenServiceTaskCompletionApiImpl(
          workerId, engine.externalTaskService, subscriptions, LinearMemoryFailureRetrySupplier(3, 1), TaskOutcomeRecorder()
        ),
        correlationApi = CorrelationApiImpl(engine.runtimeService, commandExecutor),
        signalApi = SignalApiImpl(engine.runtimeService, commandExecutor),
        deploymentApi = DeploymentApiImpl(engine.repositoryService, commandExecutor),
      ),
      metadata = metadata,
      workerId = workerId,
    )
  }

  private fun buildInMemoryEngine(): ProcessEngine =
    object : StandaloneInMemProcessEngineConfiguration() {
      init {
        history = ProcessEngineConfiguration.HISTORY_AUDIT
        databaseSchemaUpdate = ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE
        jobExecutorActivate = false
        expressionManager = MockExpressionManager()
      }
    }.buildProcessEngine()
}
