package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.api.TaskVariableApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of user tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull (e.g. for event-based delivery)
 */
open class CibSevenRemoteInitialPullUserTasksDeliveryBinding(
  subscriptionRepository: SubscriptionRepository,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  taskApi: TaskApi,
  taskIdentityLinkApi: TaskIdentityLinkApi,
  taskVariableApi: TaskVariableApi,
  private val executorService: ExecutorService,
  valueMapper: ValueMapper,
  private val c7RemoteAdapterProperties: CibSevenRemoteAdapterProperties
) {

  private val pullUserTaskDelivery = PullUserTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    executorService = executorService,
    valueMapper = valueMapper,
    taskApi = taskApi,
    taskIdentityLinkApi = taskIdentityLinkApi,
    taskVariableApi = taskVariableApi,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    deserializeOnServer = c7RemoteAdapterProperties.userTasks.deserializeOnServer
  )

  open fun pullUserTasks() {
    if (c7RemoteAdapterProperties.userTasks.executeInitialPullAsync) {
      executorService.execute { deliverUserTasks() }
    } else {
      deliverUserTasks()
    }
  }

  private fun deliverUserTasks() {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-103: Delivering user tasks..." }
    pullUserTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-104: Delivered user tasks." }
  }
}
