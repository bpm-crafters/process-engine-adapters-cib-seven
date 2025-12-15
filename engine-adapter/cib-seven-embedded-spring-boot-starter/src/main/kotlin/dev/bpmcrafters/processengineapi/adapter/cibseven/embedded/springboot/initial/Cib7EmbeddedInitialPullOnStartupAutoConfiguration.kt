package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.*
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.Cib7EmbeddedAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.Cib7EmbeddedAdapterUserTaskInitialPullEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.Cib7EmbeddedAdapterServiceTaskInitialPullEnabledCondition
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
@AutoConfigureAfter(Cib7EmbeddedAdapterAutoConfiguration::class)
@EnableAsync
@Conditional(Cib7EmbeddedAdapterEnabledCondition::class)
class Cib7EmbeddedInitialPullOnStartupAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-203: Configuration for initial pull applied." }
  }

  @Bean("cib7embedded-user-task-initial-pull")
  @Qualifier("cib7embedded-user-task-initial-pull")
  @Conditional(Cib7EmbeddedAdapterUserTaskInitialPullEnabledCondition::class)
  fun configureInitialPullForUserTaskDelivery(
    taskService: TaskService,
    @Qualifier("cib-seven-embedded-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    subscriptionRepository: SubscriptionRepository,
    @Qualifier("cib7embedded-user-task-worker-executor")
    executorService: ExecutorService
  ) = Cib7EmbeddedInitialPullUserTasksDeliveryBinding(
    taskService = taskService,
    subscriptionRepository = subscriptionRepository,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    executorService = executorService
  )

  @Bean("cib7embedded-service-task-initial-pull")
  @Qualifier("cib7embedded-service-task-initial-pull")
  @Conditional(Cib7EmbeddedAdapterServiceTaskInitialPullEnabledCondition::class)
  fun configureInitialPullForExternalServiceTaskDelivery(
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    adapterProperties: Cib7EmbeddedAdapterProperties,
    @Qualifier("cib-seven-embedded-service-task-worker-executor")
    executorService: ExecutorService
  ) = Cib7EmbeddedInitialPullServiceTasksDeliveryBinding(
    externalTaskService = externalTaskService,
    subscriptionRepository = subscriptionRepository,
    adapterProperties = adapterProperties,
    executorService = executorService
  )
}
