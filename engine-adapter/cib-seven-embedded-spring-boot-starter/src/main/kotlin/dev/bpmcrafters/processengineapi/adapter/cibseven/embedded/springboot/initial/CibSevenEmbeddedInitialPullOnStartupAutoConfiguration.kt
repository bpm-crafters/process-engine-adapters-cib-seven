package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.*
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterUserTaskInitialPullEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterServiceTaskInitialPullEnabledCondition
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycle
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationPhase
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.TaskService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * This configuration configures the initial pull bound to the process application running phase.
 * It is not relying on any delivery strategies but just configures the initial pull to happen
 * and deliver tasks to the task handlers.
 */
@Configuration
@AutoConfigureAfter(CibSevenEmbeddedAdapterAutoConfiguration::class)
@Conditional(CibSevenEmbeddedAdapterEnabledCondition::class)
class CibSevenEmbeddedInitialPullOnStartupAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-203: Configuration for initial pull applied." }
  }

  @Bean("cib-seven-embedded-user-task-initial-pull")
  @Qualifier("cib-seven-embedded-user-task-initial-pull")
  @Conditional(CibSevenEmbeddedAdapterUserTaskInitialPullEnabledCondition::class)
  fun configureInitialPullForUserTaskDelivery(
    lifecycle: ProcessApplicationLifecycle,
    taskService: TaskService,
    @Qualifier("cib-seven-embedded-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    subscriptionRepository: SubscriptionRepository,
    @Qualifier("cib-seven-embedded-user-task-worker-executor")
    executorService: ExecutorService,
    adapterProperties: CibSevenEmbeddedAdapterProperties
  ) = CibSevenEmbeddedInitialPullUserTasksDeliveryBinding(
    taskService = taskService,
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = executorService,
    adapterProperties = adapterProperties
  ).also { binding ->
    lifecycle.registerParticipant(ProcessApplicationPhase.RUNNING - 10) { binding.pullUserTasks() }
  }

  @Bean("cib-seven-embedded-service-task-initial-pull")
  @Qualifier("cib-seven-embedded-service-task-initial-pull")
  @Conditional(CibSevenEmbeddedAdapterServiceTaskInitialPullEnabledCondition::class)
  fun configureInitialPullForExternalServiceTaskDelivery(
    lifecycle: ProcessApplicationLifecycle,
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    adapterProperties: CibSevenEmbeddedAdapterProperties,
    @Qualifier("cib-seven-embedded-service-task-worker-executor")
    executorService: ThreadPoolExecutor
  ) = CibSevenEmbeddedInitialPullServiceTasksDeliveryBinding(
    externalTaskService = externalTaskService,
    subscriptionRepository = subscriptionRepository,
    adapterProperties = adapterProperties,
    executorService = executorService
  ).also { binding ->
    lifecycle.registerParticipant(ProcessApplicationPhase.RUNNING - 20) { binding.pullServiceTasks() }
  }
}
