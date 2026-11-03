package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.initial

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.client.OfficialClientServiceTaskAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.api.ProcessDefinitionApi
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.api.TaskVariableApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * This configuration configures the initial pull bound to the application started event.
 * It is not relying on any delivery strategies but just configures the initial pull to happen
 * and deliver tasks to the task handlers.
 */
@AutoConfiguration
@AutoConfigureAfter(OfficialClientServiceTaskAutoConfiguration::class)
@EnableAsync
@Conditional(CibSevenRemoteAdapterEnabledCondition::class)
class CibSevenRemoteInitialPullOnStartupAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-203: Configuration applied." }
  }

  @Bean("cib-seven-remote-user-task-initial-pull")
  @Qualifier("cib-seven-remote-user-task-initial-pull")
  @ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["user-tasks.execute-initial-pull-on-startup"])
  fun configureInitialPullForUserTaskDelivery(
    taskApi: TaskApi,
    taskIdentityLinkApi: TaskIdentityLinkApi,
    taskVariableApi: TaskVariableApi,
    processDefinitionApi: ProcessDefinitionApi,
    subscriptionRepository: SubscriptionRepository,
    @Qualifier("cib-seven-remote-user-task-worker-executor")
    executorService: ExecutorService,
    valueMapper: ValueMapper,
    @Qualifier("cib-seven-remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    c7AdapterProperties: CibSevenRemoteAdapterProperties
  ) = CibSevenRemoteInitialPullUserTasksDeliveryBinding(
    taskApi = taskApi,
    taskIdentityLinkApi = taskIdentityLinkApi,
    taskVariableApi = taskVariableApi,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    subscriptionRepository = subscriptionRepository,
    executorService = executorService,
    valueMapper = valueMapper,
    c7RemoteAdapterProperties = c7AdapterProperties
  )

  @Bean("cib-seven-remote-service-task-initial-pull")
  @Qualifier("cib-seven-remote-service-task-initial-pull")
  @ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["service-tasks.execute-initial-pull-on-startup"])
  fun configureInitialPullForExternalServiceTaskDelivery(
    externalTaskApi: ExternalTaskApi,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: CibSevenRemoteAdapterProperties,
    @Qualifier("cib-seven-remote-service-task-worker-executor")
    executor: ThreadPoolExecutor,
    valueMapper: ValueMapper,
    @Qualifier("cib-seven-remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    metrics: PullServiceTaskDeliveryMetrics,
  ) = CibSevenRemoteInitialPullServiceTasksDeliveryBinding(
    externalTaskApi = externalTaskApi,
    subscriptionRepository = subscriptionRepository,
    c7AdapterProperties = c7AdapterProperties,
    executor = executor,
    valueMapper = valueMapper,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    metrics = metrics,
  )
}
