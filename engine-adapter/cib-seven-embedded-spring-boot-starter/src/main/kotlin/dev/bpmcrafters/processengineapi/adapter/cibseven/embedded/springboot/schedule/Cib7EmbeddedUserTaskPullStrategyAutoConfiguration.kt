package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.UserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.ConditionalOnUserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for user tasks.
 */
@Configuration
@ConditionalOnUserTaskDeliveryStrategy(
  strategies = [UserTaskDeliveryStrategy.EMBEDDED_SCHEDULED]
)
@AutoConfigureAfter(Cib7EmbeddedSchedulingAutoConfiguration::class)
class Cib7EmbeddedUserTaskPullStrategyAutoConfiguration(
  private val embeddedPullUserTaskDelivery: EmbeddedPullUserTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.cib-seven-embedded-dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties'.userTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "cib-seven-embedded-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-107: Delivering user tasks..." }
    embeddedPullUserTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-108: Delivered user tasks." }
  }

}
