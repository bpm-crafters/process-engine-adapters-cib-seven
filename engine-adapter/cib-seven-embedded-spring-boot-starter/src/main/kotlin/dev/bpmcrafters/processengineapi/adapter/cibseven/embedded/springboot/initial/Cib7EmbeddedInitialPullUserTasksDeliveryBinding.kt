package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial.Cib7EmbeddedInitialPullUserTasksDeliveryBinding.Companion.ORDER
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.TaskService
import org.cibseven.bpm.spring.boot.starter.event.ProcessApplicationStartedEvent
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
open class Cib7EmbeddedInitialPullUserTasksDeliveryBinding(
  subscriptionRepository: SubscriptionRepository,
  taskService: TaskService,
  processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  executorService: ExecutorService
) {

  companion object {
    const val ORDER = Ordered.HIGHEST_PRECEDENCE + 2000
  }

  private val pullDelivery = EmbeddedPullUserTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    taskService = taskService,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = executorService
  )

  @EventListener
  @Async
  open fun pullUserTasks(event: ProcessApplicationStartedEvent) {
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-103: Delivering user tasks..." }
    pullDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-104: Delivered user tasks." }
  }
}
