package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.subscription.CibSevenTaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.cibseven.bpm.engine.ProcessEngine
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors

const val WORKER_ID = "execute-action-external"

class CibSevenEmbeddedProcessTestHelper(
  private val processEngine: ProcessEngine
) : ProcessTestHelper {

  private var subscriptionRepository: InMemSubscriptionRepository = InMemSubscriptionRepository()

  private val processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(repositoryService = processEngine.repositoryService)

  private var embeddedPullUserTaskDelivery: EmbeddedPullUserTaskDelivery = EmbeddedPullUserTaskDelivery(
    taskService = processEngine.taskService,
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = Executors.newFixedThreadPool(3)
  )

  private var embeddedPullExternalTaskDelivery: EmbeddedPullServiceTaskDelivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = processEngine.externalTaskService,
    workerId = WORKER_ID,
    subscriptionRepository = subscriptionRepository,
    maxTasks = 100,
    lockDurationInSeconds = 10L,
    retryTimeoutInSeconds = 10L,
    retries = 3,
    executorService = ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
  )

  override fun getStartProcessApi(): StartProcessApi = StartProcessApiImpl(
    runtimeService = processEngine.runtimeService,
    repositoryService = processEngine.repositoryService,
    commandExecutor = EngineCommandExecutor { it.run() },
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
  )

  override fun getTaskSubscriptionApi(): TaskSubscriptionApi = CibSevenTaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  override fun getUserTaskCompletionApi(): UserTaskCompletionApi = CibSevenUserTaskCompletionApiImpl(
    taskService = processEngine.taskService,
    subscriptionRepository = subscriptionRepository
  )

  override fun getServiceTaskCompletionApi(): ServiceTaskCompletionApi = CibSevenServiceTaskCompletionApiImpl(
    workerId = WORKER_ID,
    externalTaskService = processEngine.externalTaskService,
    subscriptionRepository = subscriptionRepository,
    failureRetrySupplier = LinearMemoryFailureRetrySupplier(
      retry = 1,
      retryTimeout = 10
    )
  )

  override fun triggerPullingUserTaskDeliveryManually() = embeddedPullUserTaskDelivery.refresh()

  override fun subscribeForUserTasks() {
    TODO("Not yet implemented")
  }

  override fun triggerExternalTaskDeliveryManually() = embeddedPullExternalTaskDelivery.refresh()

  override fun getProcessInformation(instanceId: String): ProcessInformation =
    processEngine.runtimeService
      .createProcessInstanceQuery()
      .processInstanceId(instanceId)
      .singleResult()
      .toProcessInformation(processDefinitionMetaDataResolver)

  override fun getActiveElements(instanceId: String): Collection<String> =
    processEngine.runtimeService.getActiveActivityIds(instanceId)

  override fun clearAllSubscriptions() = subscriptionRepository.deleteAllTaskSubscriptions()


}
