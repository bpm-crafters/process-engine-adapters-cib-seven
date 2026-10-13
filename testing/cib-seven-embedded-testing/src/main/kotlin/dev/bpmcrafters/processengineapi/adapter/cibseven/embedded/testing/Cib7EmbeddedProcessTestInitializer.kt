package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.Cib7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.Cib7UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.modification.Cib7UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.subscription.Cib7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializationRequest
import dev.bpmcrafters.processengineapi.testing.bootstrap.ProcessTestInitializer
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.ProcessEngineConfiguration
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.cibseven.bpm.engine.test.mock.MockExpressionManager

class Cib7EmbeddedProcessTestInitializer : ProcessTestInitializer {

  companion object {
    const val QUALIFIER = "cib7embedded"
  }

  override fun qualifier() = QUALIFIER

  override fun initialize(request: ProcessTestInitializationRequest): Cib7EmbeddedProcessTestContext {
    val engine = buildInMemoryEngine()
    val commandExecutor = EngineCommandExecutor { it.run() }
    val subscriptions = InMemSubscriptionRepository()
    val metadata = CachingProcessDefinitionMetaDataResolver(engine.repositoryService)
    val workerId = "process-test-${request.testClass.simpleName}"

    return Cib7EmbeddedProcessTestContext(
      processEngine = engine,
      runtimeApis = Cib7EmbeddedProcessTestRuntimeApis(
        startProcessApi = StartProcessApiImpl(engine.runtimeService, engine.repositoryService, commandExecutor, metadata),
        taskSubscriptionApi = Cib7TaskSubscriptionApiImpl(subscriptions),
        userTaskCompletionApi = Cib7UserTaskCompletionApiImpl(engine.taskService, subscriptions),
        userTaskModificationApi = Cib7UserTaskModificationApiImpl(engine.taskService),
        serviceTaskCompletionApi = Cib7ServiceTaskCompletionApiImpl(
          workerId, engine.externalTaskService, subscriptions, LinearMemoryFailureRetrySupplier(3, 1)
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
