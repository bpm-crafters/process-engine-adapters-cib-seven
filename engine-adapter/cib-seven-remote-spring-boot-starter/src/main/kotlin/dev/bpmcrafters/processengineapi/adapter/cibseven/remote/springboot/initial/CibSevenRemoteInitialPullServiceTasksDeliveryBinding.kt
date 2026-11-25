package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.ExternalTaskApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of service tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull.
 */
open class CibSevenRemoteInitialPullServiceTasksDeliveryBinding(
  externalTaskApi: ExternalTaskApi,
  subscriptionRepository: SubscriptionRepository,
  private val c7AdapterProperties: CibSevenRemoteAdapterProperties,
  private val executor: ThreadPoolExecutor,
  valueMapper: ValueMapper,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  metrics: PullServiceTaskDeliveryMetrics
) {
  private val pullDelivery = PullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskApi = externalTaskApi,
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

  open fun pullServiceTasks() {
    if (c7AdapterProperties.serviceTasks.executeInitialPullAsync) {
      executor.execute { deliverServiceTasks() }
    } else {
      deliverServiceTasks()
    }
  }

  private fun deliverServiceTasks() {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-101: Delivering service tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-102: Delivered service tasks." }
  }

}
