package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.Cib7EmbeddedAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.Cib7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.Cib7UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.modification.Cib7UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.subscription.Cib7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskModificationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.RepositoryService
import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.TaskService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

@Configuration
@EnableConfigurationProperties(Cib7EmbeddedAdapterProperties::class)
@Conditional(Cib7EmbeddedAdapterEnabledCondition::class)
class Cib7EmbeddedAdapterAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-200: Configuration of services applied." }
  }

  @Bean
  @ConditionalOnMissingBean
  fun engineCommandExecutor(): EngineCommandExecutor = EngineCommandExecutor()

  @Bean("cib7embedded-start-process-api")
  @Qualifier("cib7embedded-start-process-api")
  fun startProcessApi(
    runtimeService: RuntimeService,
    repositoryService: RepositoryService,
    commandExecutor: EngineCommandExecutor
  ): StartProcessApi = StartProcessApiImpl(
    runtimeService = runtimeService,
    repositoryService = repositoryService,
    commandExecutor = commandExecutor
  )

  @Bean("cib7embedded-task-subscription-api")
  @Qualifier("cib7embedded-task-subscription-api")
  fun taskSubscriptionApi(subscriptionRepository: SubscriptionRepository): TaskSubscriptionApi = Cib7TaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  @Bean("cib7embedded-correlation-api")
  @Qualifier("cib7embedded-correlation-api")
  fun correlationApi(
    runtimeService: RuntimeService,
    commandExecutor: EngineCommandExecutor
  ): CorrelationApi = CorrelationApiImpl(
    runtimeService = runtimeService,
    commandExecutor = commandExecutor
  )

  @Bean("cib7embedded-signal-api")
  @Qualifier("cib7embedded-signal-api")
  fun signalApi(
    runtimeService: RuntimeService,
    commandExecutor: EngineCommandExecutor
  ): SignalApi = SignalApiImpl(
    runtimeService = runtimeService,
    commandExecutor = commandExecutor
  )

  @Bean("cib7embedded-deployment-api")
  @Qualifier("cib7embedded-deployment-api")
  fun deploymentApi(
    repositoryService: RepositoryService,
    commandExecutor: EngineCommandExecutor,
    properties: Cib7EmbeddedAdapterProperties
  ): DeploymentApi = DeploymentApiImpl(
    repositoryService = repositoryService,
    commandExecutor = commandExecutor,
    deployOnlyOnChange = properties.deployment.deployOnlyOnChange
  )

  @Bean("cib7embedded-user-task-modification-api")
  @Qualifier("cib7embedded-user-task-modification-api")
  fun userTaskModificationApi(taskService: TaskService): UserTaskModificationApi = Cib7UserTaskModificationApiImpl(
    taskService = taskService
  )

  @Bean
  @ConditionalOnMissingBean
  fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

  @Bean("cib7embedded-failure-retry-supplier")
  @Qualifier("cib7embedded-failure-retry-supplier")
  @ConditionalOnMissingBean
  fun defaultFailureRetrySupplier(
    adapterProperties: Cib7EmbeddedAdapterProperties
  ): FailureRetrySupplier {
    return LinearMemoryFailureRetrySupplier(
      retry = adapterProperties.serviceTasks.retries,
      retryTimeout = adapterProperties.serviceTasks.retryTimeoutInSeconds
    )
  }

  @Bean("cib7embedded-service-task-completion-api")
  @Qualifier("cib7embedded-service-task-completion-api")
  fun serviceTaskCompletionApi(
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    adapterProperties: Cib7EmbeddedAdapterProperties,
    @Qualifier("cib7embedded-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier
  ): ServiceTaskCompletionApi =
    Cib7ServiceTaskCompletionApiImpl(
      workerId = adapterProperties.serviceTasks.workerId,
      externalTaskService = externalTaskService,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier
    )

  @Bean("cib7embedded-user-task-completion-api")
  @Qualifier("cib7embedded-user-task-completion-api")
  fun userTaskCompletionApi(
    taskService: TaskService,
    subscriptionRepository: SubscriptionRepository
  ): UserTaskCompletionApi =
    Cib7UserTaskCompletionApiImpl(
      taskService = taskService,
      subscriptionRepository = subscriptionRepository
    )

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies only.
   */
  @Bean("cib-seven-embedded-service-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "cib-seven-embedded-service-task-worker-executor")
  @Qualifier("cib-seven-embedded-service-task-worker-executor")
  fun serviceTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies and async event listener execution.
   */
  @Bean("cib7embedded-user-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "cib7embedded-user-task-worker-executor")
  @Qualifier("cib7embedded-user-task-worker-executor")
  fun userTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

}
