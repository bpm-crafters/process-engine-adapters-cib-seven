package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial.Cib7RemoteInitialPullServiceTasksDeliveryBinding.Companion.ORDER
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.ExternalTaskApiClient
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of user tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull.
 */
@Order(ORDER)
open class Cib7RemoteInitialPullServiceTasksDeliveryBinding(
  externalTaskApiClient: ExternalTaskApiClient,
  subscriptionRepository: SubscriptionRepository,
  c7AdapterProperties: Cib7RemoteAdapterProperties,
  executor: ThreadPoolExecutor,
  valueMapper: ValueMapper,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  metrics: PullServiceTaskDeliveryMetrics
) {
  companion object {
    const val ORDER = Ordered.HIGHEST_PRECEDENCE + 1000
  }

  private val pullDelivery = PullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskApiClient = externalTaskApiClient,
    workerId = c7AdapterProperties.serviceTasks.workerId,
    maxTasks = c7AdapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries,
    executor = executor,
    valueMapper = valueMapper,
    deserializeOnServer = c7AdapterProperties.serviceTasks.deserializeOnServer,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    metrics = metrics
  )

  @EventListener
  @Async
  open fun pullUserTasks(event: ApplicationStartedEvent) {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-101: Delivering service tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-102: Delivered service tasks." }
  }

}
