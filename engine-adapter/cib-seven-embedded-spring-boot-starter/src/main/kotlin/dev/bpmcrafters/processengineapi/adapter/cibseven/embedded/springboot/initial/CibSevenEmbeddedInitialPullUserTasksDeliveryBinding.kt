package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.TaskService
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * This class is responsible for the initial pull of user tasks.
 * We are not relying on the pull delivery strategy configured centrally, because for other deliveries we still want to
 * execute an initial pull (e.g. for event-based delivery)
 */
open class CibSevenEmbeddedInitialPullUserTasksDeliveryBinding(
  subscriptionRepository: SubscriptionRepository,
  taskService: TaskService,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  private val executorService: ExecutorService,
  private val adapterProperties: CibSevenEmbeddedAdapterProperties
) {

  private val pullDelivery = EmbeddedPullUserTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    taskService = taskService,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = executorService
  )

  open fun pullUserTasks() {
    if (adapterProperties.userTasks.executeInitialPullAsync) {
      executorService.execute { deliverUserTasks() }
    } else {
      deliverUserTasks()
    }
  }

  private fun deliverUserTasks() {
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-103: Delivering user tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-104: Delivered user tasks." }
  }
}
