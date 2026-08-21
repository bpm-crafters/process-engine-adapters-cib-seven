package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemotePullServicesAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.ConditionalOnUserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullUserTaskDelivery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

private val logger = KotlinLogging.logger {}

/**
 * Dynamic / imperative scheduling configuration using own task scheduler for user tasks.
 */
@AutoConfiguration
@ConditionalOnUserTaskDeliveryStrategy(
  strategy = REMOTE_SCHEDULED
)
@AutoConfigureAfter(Cib7RemotePullServicesAutoConfiguration::class)
class PullUserTaskDeliveryAutoConfiguration(
  private val remotePullUserTaskDelivery: PullUserTaskDelivery
) {

  @Scheduled(
    fixedDelayString = "#{@'dev.bpm-crafters.process-api.adapter.cib-seven-remote-dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterProperties'.userTasks.scheduleDeliveryFixedRateInSeconds}",
    timeUnit = SECONDS,
    scheduler = "cib7remote-task-scheduler"
  )
  fun refresh() {
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-107: Delivering user tasks..." }
    remotePullUserTaskDelivery.refresh()
    logger.trace { "PROCESS-ENGINE-C7-REMOTE-108: Delivered user tasks." }
  }

}
