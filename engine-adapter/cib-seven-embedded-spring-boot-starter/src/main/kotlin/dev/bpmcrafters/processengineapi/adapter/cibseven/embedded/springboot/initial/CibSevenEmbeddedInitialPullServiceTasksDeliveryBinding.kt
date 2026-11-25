package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.ExternalTaskService
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of service tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull (e.g. for event-based delivery)
 */
open class CibSevenEmbeddedInitialPullServiceTasksDeliveryBinding(
  externalTaskService: ExternalTaskService,
  subscriptionRepository: SubscriptionRepository,
  private val adapterProperties: CibSevenEmbeddedAdapterProperties,
  private val executorService: ThreadPoolExecutor
) {

  private val pullDelivery = EmbeddedPullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskService = externalTaskService,
    workerId = adapterProperties.serviceTasks.workerId,
    maxTasks = adapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = adapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = adapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = adapterProperties.serviceTasks.retries,
    executorService = executorService,
  )

  open fun pullServiceTasks() {
    if (adapterProperties.serviceTasks.executeInitialPullAsync) {
      executorService.execute { deliverServiceTasks() }
    } else {
      deliverServiceTasks()
    }
  }

  private fun deliverServiceTasks() {
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-101: Delivering service tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-102: Delivered service tasks." }
  }

}
