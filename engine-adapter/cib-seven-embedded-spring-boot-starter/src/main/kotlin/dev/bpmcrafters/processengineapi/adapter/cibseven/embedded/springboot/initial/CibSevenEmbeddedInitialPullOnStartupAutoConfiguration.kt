package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.*
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterUserTaskInitialPullEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterServiceTaskInitialPullEnabledCondition
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.TaskService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

/**
 * This configuration configures the initial pull bound to the application started event.
 * It is not relying on any delivery strategies but just configures the initial pull to happen
 * and deliver tasks to the task handlers.
 */
@Configuration
@AutoConfigureAfter(CibSevenEmbeddedAdapterAutoConfiguration::class)
@EnableAsync
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
    taskService: TaskService,
    @Qualifier("cib-seven-embedded-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    subscriptionRepository: SubscriptionRepository,
    @Qualifier("cib-seven-embedded-user-task-worker-executor")
    executorService: ExecutorService
  ) = CibSevenEmbeddedInitialPullUserTasksDeliveryBinding(
    taskService = taskService,
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = executorService
  )

  @Bean("cib-seven-embedded-service-task-initial-pull")
  @Qualifier("cib-seven-embedded-service-task-initial-pull")
  @Conditional(CibSevenEmbeddedAdapterServiceTaskInitialPullEnabledCondition::class)
  fun configureInitialPullForExternalServiceTaskDelivery(
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    adapterProperties: CibSevenEmbeddedAdapterProperties,
    @Qualifier("cib-seven-embedded-service-task-worker-executor")
    executorService: ExecutorService
  ) = CibSevenEmbeddedInitialPullServiceTasksDeliveryBinding(
    externalTaskService = externalTaskService,
    subscriptionRepository = subscriptionRepository,
    adapterProperties = adapterProperties,
    executorService = executorService
  )
}
