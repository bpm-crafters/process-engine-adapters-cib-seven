package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy.EMBEDDED_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.ConditionalOnServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for service tasks.
 */
@Configuration
@ConditionalOnServiceTaskDeliveryStrategy(
  strategy = EMBEDDED_SCHEDULED
)
@AutoConfigureAfter(Cib7EmbeddedSchedulingAutoConfiguration::class)
class Cib7EmbeddedServiceTaskPullStrategyAutoConfiguration(
  private val embeddedPullServiceTaskDelivery: EmbeddedPullServiceTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.cib-seven-embedded-dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties'.serviceTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "cib-seven-embedded-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-105: Delivering external tasks..." }
    embeddedPullServiceTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-CIB7-EMBEDDED-106: Delivered external tasks." }
  }

}
