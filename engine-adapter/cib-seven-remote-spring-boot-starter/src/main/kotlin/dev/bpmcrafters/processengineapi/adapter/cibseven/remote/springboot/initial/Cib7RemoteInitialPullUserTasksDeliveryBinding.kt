package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial.Cib7RemoteInitialPullUserTasksDeliveryBinding.Companion.ORDER
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.TaskApiClient
import org.cibseven.community.rest.client.api.TaskIdentityLinkApiClient
import org.cibseven.community.rest.client.api.TaskVariableApiClient
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of user tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull (e.g. for event-based delivery)
 */
@Order(ORDER)
open class Cib7RemoteInitialPullUserTasksDeliveryBinding(
  subscriptionRepository: SubscriptionRepository,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  taskApiClient: TaskApiClient,
  taskIdentityLinkApiClient: TaskIdentityLinkApiClient,
  taskVariableApiClient: TaskVariableApiClient,
  executorService: ExecutorService,
  valueMapper: ValueMapper,
  c7RemoteAdapterProperties: Cib7RemoteAdapterProperties
) {

  companion object {
    const val ORDER = Ordered.HIGHEST_PRECEDENCE + 2000
  }

  private val pullUserTaskDelivery = PullUserTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    executorService = executorService,
    valueMapper = valueMapper,
    taskApiClient = taskApiClient,
    taskIdentityLinkApiClient = taskIdentityLinkApiClient,
    taskVariableApiClient = taskVariableApiClient,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    deserializeOnServer = c7RemoteAdapterProperties.userTasks.deserializeOnServer
  )

  @EventListener
  @Async
  open fun pullUserTasks(event: ApplicationStartedEvent) {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-103: Delivering user tasks..." }
    pullUserTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-104: Delivered user tasks." }
  }
}
