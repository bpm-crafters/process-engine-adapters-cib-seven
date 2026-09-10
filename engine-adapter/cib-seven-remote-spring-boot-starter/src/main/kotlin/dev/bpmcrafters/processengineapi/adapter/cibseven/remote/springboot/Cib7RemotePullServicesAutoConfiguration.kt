package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.FeignServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullUserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.modification.UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.api.ProcessDefinitionApi
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.api.TaskLocalVariableApi
import org.cibseven.community.rest.client.api.TaskVariableApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor

private val logger = KotlinLogging.logger {}

/**
 * Autoconfiguration for scheduled delivery.
 */
@AutoConfiguration
@EnableAsync
@EnableScheduling
@AutoConfigureAfter(Cib7RemoteAdapterAutoConfiguration::class)
@Conditional(Cib7RemoteAdapterEnabledCondition::class)
class Cib7RemotePullServicesAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-202: Configuration applied." }
  }

  @Bean("cib7remote-task-scheduler")
  @Qualifier("cib7remote-task-scheduler")
  @Order(200)
  @ConditionalOnMissingQualifiedBean(beanClass = TaskScheduler::class, qualifier = "cib7remote-task-scheduler")
  fun taskScheduler(): TaskScheduler {
    val threadPoolTaskScheduler = ThreadPoolTaskScheduler()
    threadPoolTaskScheduler.poolSize = 2 // we have two schedulers, one for user tasks one for service tasks
    threadPoolTaskScheduler.setThreadNamePrefix("CIB7REMOTE-SCHEDULER-")
    return threadPoolTaskScheduler
  }

  @Bean("taskScheduler")
  @Order(100)
  @Conditional(VirtualThreadingCondition::class)
  fun taskSchedulerVirtualThreads(builder: SimpleAsyncTaskSchedulerBuilder): SimpleAsyncTaskScheduler {
    return builder.build()
  }

  @Bean("taskScheduler")
  @Order(100)
  @Conditional(PlatformThreadingCondition::class)
  fun taskSchedulerPlatformThreads(threadPoolTaskSchedulerBuilder: ThreadPoolTaskSchedulerBuilder): ThreadPoolTaskScheduler {
    return threadPoolTaskSchedulerBuilder.build()
  }

  @Bean("cib7remote-service-task-delivery")
  @Qualifier("cib7remote-service-task-delivery")
  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledServiceTaskDelivery(
    externalTaskApi: ExternalTaskApi,
    @Qualifier("cib7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: Cib7RemoteAdapterProperties,
    @Qualifier("cib7remote-service-task-worker-executor")
    executor: ThreadPoolExecutor,
    valueMapper: ValueMapper,
    metrics: PullServiceTaskDeliveryMetrics
  ) = PullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    workerId = c7AdapterProperties.serviceTasks.workerId,
    maxTasks = c7AdapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = c7AdapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = c7AdapterProperties.serviceTasks.retries,
    executor = executor,
    externalTaskApi = externalTaskApi,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    valueMapper = valueMapper,
    deserializeOnServer = c7AdapterProperties.serviceTasks.deserializeOnServer,
    metrics = metrics
  )

  @Bean("cib7remote-service-task-completion-api")
  @Qualifier("cib7remote-service-task-completion-api")
  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledServiceTaskCompletionApi(
    externalTaskApi: ExternalTaskApi,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: Cib7RemoteAdapterProperties,
    @Qualifier("cib7remote-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier,
    valueMapper: ValueMapper
  ): ServiceTaskCompletionApi =
    FeignServiceTaskCompletionApiImpl(
      workerId = c7AdapterProperties.serviceTasks.workerId,
      externalTaskApi = externalTaskApi,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier,
      valueMapper = valueMapper
    )

  @Bean("cib7remote-process-definition-meta-data-resolver")
  @Qualifier("cib7remote-process-definition-meta-data-resolver")
  @ConditionalOnMissingQualifiedBean(beanClass = ProcessDefinitionMetaDataResolver::class, qualifier = "cib7remote-process-definition-meta-data-resolver")
  fun cachingProcessDefinitionMetaDataResolver(processDefinitionApi: ProcessDefinitionApi): ProcessDefinitionMetaDataResolver {
    return CachingProcessDefinitionMetaDataResolver(processDefinitionApi)
  }

  @Bean("cib7remote-user-task-delivery")
  @Qualifier("cib7remote-user-task-delivery")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledUserTaskDelivery(
    @Qualifier("cib7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    taskApi: TaskApi,
    taskIdentityLinkApi: TaskIdentityLinkApi,
    taskVariableApi: TaskVariableApi,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: Cib7RemoteAdapterProperties,
    @Qualifier("cib7remote-user-task-worker-executor")
    executorService: ExecutorService,
    valueMapper: ValueMapper
  ): PullUserTaskDelivery {
    return PullUserTaskDelivery(
      subscriptionRepository = subscriptionRepository,
      executorService = executorService,
      valueMapper = valueMapper,
      processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
      taskApi = taskApi,
      taskIdentityLinkApi = taskIdentityLinkApi,
      taskVariableApi = taskVariableApi,
      deserializeOnServer = c7AdapterProperties.userTasks.deserializeOnServer
    )
  }

  /**
   * User task completion API.
   */
  @Bean("cib7remote-user-task-completion-api")
  @Qualifier("cib7remote-user-task-completion-api")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun userTaskCompletionApi(
    taskApi: TaskApi,
    subscriptionRepository: SubscriptionRepository,
    valueMapper: ValueMapper,
  ): UserTaskCompletionApi =
    UserTaskCompletionApiImpl(
      taskApi = taskApi,
      subscriptionRepository = subscriptionRepository,
      valueMapper = valueMapper
    )

  /**
   * User task modification api.
   */
  @Bean("cib7remote-user-task-modification-api")
  @Qualifier("cib7remote-user-task-modification-api")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun userTaskModificationApi(
    taskApi: TaskApi,
    taskIdentityLinkApi: TaskIdentityLinkApi,
    taskLocalVariableApi: TaskLocalVariableApi,
    valueMapper: ValueMapper
  ): UserTaskModificationApi =
    UserTaskModificationApiImpl(
      taskApi = taskApi,
      taskIdentityLinkApi = taskIdentityLinkApi,
      taskLocalVariableApi = taskLocalVariableApi,
      valueMapper = valueMapper
    )
}
