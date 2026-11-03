package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.client

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SUBSCRIBED
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.ConditionalOnServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.OfficialClientServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.subscribe.SubscribingServiceTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.cibseven.bpm.client.ExternalTaskClient
import org.cibseven.bpm.client.impl.ExternalTaskClientImpl
import org.cibseven.bpm.client.task.ExternalTaskService
import org.cibseven.bpm.client.task.impl.ExternalTaskServiceImpl
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean

private val logger = KotlinLogging.logger {}

/**
 * Auto-configuration for subscribed delivery using Camunda External Client.
 */
@AutoConfiguration
@AutoConfigureAfter(CibSevenRemoteAdapterAutoConfiguration::class)
@ConditionalOnServiceTaskDeliveryStrategy(
  strategy = REMOTE_SUBSCRIBED
)
class OfficialClientServiceTaskAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-201: Configuration applied." }
  }

  @Bean
  fun externalTaskClientExternalTaskService(externalTaskClient: ExternalTaskClient): ExternalTaskService {
    require(externalTaskClient is ExternalTaskClientImpl) { "External task client must be official Camunda External Task Client" }
    return ExternalTaskServiceImpl(externalTaskClient.topicSubscriptionManager.engineClient)
  }

  @Bean(name = ["cib-seven-remote-service-task-delivery"], initMethod = "subscribe", destroyMethod = "unsubscribe")
  fun subscribingClientExternalTaskDelivery(
    subscriptionRepository: SubscriptionRepository,
    externalTaskClient: ExternalTaskClient,
    c7AdapterProperties: CibSevenRemoteAdapterProperties
  ) = SubscribingServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    externalTaskClient = externalTaskClient,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries
  )

  @Bean("cib-seven-remote-service-task-completion-api")
  @Qualifier("cib-seven-remote-service-task-completion-api")
  fun externalTaskClientCompletionApi(
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    @Qualifier("cib-seven-remote-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier
  ): ServiceTaskCompletionApi =
    OfficialClientServiceTaskCompletionApiImpl(
      externalTaskService = externalTaskService,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier
    )

}
