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
import org.cibseven.community.rest.client.api.ExternalTaskApiClient
import org.cibseven.community.rest.client.api.ProcessDefinitionApiClient
import org.cibseven.community.rest.client.api.TaskApiClient
import org.cibseven.community.rest.client.api.TaskIdentityLinkApiClient
import org.cibseven.community.rest.client.api.TaskLocalVariableApiClient
import org.cibseven.community.rest.client.api.TaskVariableApiClient
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
    externalTaskApiClient: ExternalTaskApiClient,
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
    externalTaskApiClient = externalTaskApiClient,
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
    externalTaskApiClient: ExternalTaskApiClient,
    subscriptionRepository: SubscriptionRepository,
    c7AdapterProperties: Cib7RemoteAdapterProperties,
    @Qualifier("cib7remote-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier,
    valueMapper: ValueMapper
  ): ServiceTaskCompletionApi =
    FeignServiceTaskCompletionApiImpl(
      workerId = c7AdapterProperties.serviceTasks.workerId,
      externalTaskApiClient = externalTaskApiClient,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier,
      valueMapper = valueMapper
    )

  @Bean("cib7remote-process-definition-meta-data-resolver")
  @Qualifier("cib7remote-process-definition-meta-data-resolver")
  @ConditionalOnMissingQualifiedBean(beanClass = ProcessDefinitionMetaDataResolver::class, qualifier = "cib7remote-process-definition-meta-data-resolver")
  fun cachingProcessDefinitionMetaDataResolver(processDefinitionApiClient: ProcessDefinitionApiClient): ProcessDefinitionMetaDataResolver {
    return CachingProcessDefinitionMetaDataResolver(processDefinitionApiClient)
  }

  @Bean("cib7remote-user-task-delivery")
  @Qualifier("cib7remote-user-task-delivery")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.UserTaskDeliveryStrategy.REMOTE_SCHEDULED
  )
  fun scheduledUserTaskDelivery(
    @Qualifier("cib7remote-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    taskApiClient: TaskApiClient,
    taskIdentityLinkApiClient: TaskIdentityLinkApiClient,
    taskVariableApiClient: TaskVariableApiClient,
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
      taskApiClient = taskApiClient,
      taskIdentityLinkApiClient = taskIdentityLinkApiClient,
      taskVariableApiClient = taskVariableApiClient,
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
    taskApiClient: TaskApiClient,
    subscriptionRepository: SubscriptionRepository,
    valueMapper: ValueMapper,
  ): UserTaskCompletionApi =
    UserTaskCompletionApiImpl(
      taskApiClient = taskApiClient,
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
    taskApiClient: TaskApiClient,
    taskIdentityLinkApiClient: TaskIdentityLinkApiClient,
    taskLocalVariableApiClient: TaskLocalVariableApiClient,
    valueMapper: ValueMapper
  ): UserTaskModificationApi =
    UserTaskModificationApiImpl(
      taskApiClient = taskApiClient,
      taskIdentityLinkApiClient = taskIdentityLinkApiClient,
      taskLocalVariableApiClient = taskLocalVariableApiClient,
      valueMapper = valueMapper
    )
}
