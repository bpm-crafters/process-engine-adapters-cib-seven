package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.threading.ThreadContextClassLoaderThreadFactory
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.CibSevenEmbeddedAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.modification.CibSevenUserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.subscription.CibSevenTaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
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
import org.springframework.beans.factory.ObjectProvider
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
@EnableConfigurationProperties(CibSevenEmbeddedAdapterProperties::class)
@Conditional(CibSevenEmbeddedAdapterEnabledCondition::class)
class CibSevenEmbeddedAdapterAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-200: Configuration of services applied." }
  }

  @Bean
  @ConditionalOnMissingBean
  fun engineCommandExecutor(): EngineCommandExecutor = EngineCommandExecutor()

  @Bean("cib-seven-embedded-start-process-api")
  @Qualifier("cib-seven-embedded-start-process-api")
  fun startProcessApi(
    runtimeService: RuntimeService,
    repositoryService: RepositoryService,
    commandExecutor: EngineCommandExecutor
  ): StartProcessApi = StartProcessApiImpl(
    runtimeService = runtimeService,
    repositoryService = repositoryService,
    commandExecutor = commandExecutor,
    processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(repositoryService = repositoryService),
  )

  @Bean("cib-seven-embedded-task-subscription-api")
  @Qualifier("cib-seven-embedded-task-subscription-api")
  fun taskSubscriptionApi(subscriptionRepository: SubscriptionRepository): TaskSubscriptionApi = CibSevenTaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  @Bean("cib-seven-embedded-correlation-api")
  @Qualifier("cib-seven-embedded-correlation-api")
  fun correlationApi(
    runtimeService: RuntimeService,
    commandExecutor: EngineCommandExecutor
  ): CorrelationApi = CorrelationApiImpl(
    runtimeService = runtimeService,
    commandExecutor = commandExecutor
  )

  @Bean("cib-seven-embedded-signal-api")
  @Qualifier("cib-seven-embedded-signal-api")
  fun signalApi(
    runtimeService: RuntimeService,
    commandExecutor: EngineCommandExecutor
  ): SignalApi = SignalApiImpl(
    runtimeService = runtimeService,
    commandExecutor = commandExecutor
  )

  @Bean("cib-seven-embedded-deployment-api")
  @Qualifier("cib-seven-embedded-deployment-api")
  fun deploymentApi(
    repositoryService: RepositoryService,
    commandExecutor: EngineCommandExecutor,
    properties: CibSevenEmbeddedAdapterProperties
  ): DeploymentApi = DeploymentApiImpl(
    repositoryService = repositoryService,
    commandExecutor = commandExecutor,
    deployOnlyOnChange = properties.deployment.deployOnlyOnChange
  )

  @Bean("cib-seven-embedded-user-task-modification-api")
  @Qualifier("cib-seven-embedded-user-task-modification-api")
  fun userTaskModificationApi(taskService: TaskService): UserTaskModificationApi = CibSevenUserTaskModificationApiImpl(
    taskService = taskService
  )

  @Bean
  @ConditionalOnMissingBean
  fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

  @Bean("cib-seven-embedded-failure-retry-supplier")
  @Qualifier("cib-seven-embedded-failure-retry-supplier")
  @ConditionalOnMissingBean
  fun defaultFailureRetrySupplier(
    adapterProperties: CibSevenEmbeddedAdapterProperties
  ): FailureRetrySupplier {
    return LinearMemoryFailureRetrySupplier(
      retry = adapterProperties.serviceTasks.retries,
      retryTimeout = adapterProperties.serviceTasks.retryTimeoutInSeconds
    )
  }

  @Bean("cib-seven-embedded-service-task-completion-api")
  @Qualifier("cib-seven-embedded-service-task-completion-api")
  fun serviceTaskCompletionApi(
    externalTaskService: ExternalTaskService,
    subscriptionRepository: SubscriptionRepository,
    adapterProperties: CibSevenEmbeddedAdapterProperties,
    @Qualifier("cib-seven-embedded-failure-retry-supplier")
    failureRetrySupplier: FailureRetrySupplier,
    @Qualifier("cib-seven-embedded-task-outcome-recorder")
    outcomeRecorder: TaskOutcomeRecorder
  ): ServiceTaskCompletionApi =
    CibSevenServiceTaskCompletionApiImpl(
      workerId = adapterProperties.serviceTasks.workerId,
      externalTaskService = externalTaskService,
      subscriptionRepository = subscriptionRepository,
      failureRetrySupplier = failureRetrySupplier,
      outcomeRecorder = outcomeRecorder
    )

  @Bean("cib-seven-embedded-user-task-completion-api")
  @Qualifier("cib-seven-embedded-user-task-completion-api")
  fun userTaskCompletionApi(
    taskService: TaskService,
    subscriptionRepository: SubscriptionRepository
  ): UserTaskCompletionApi =
    CibSevenUserTaskCompletionApiImpl(
      taskService = taskService,
      subscriptionRepository = subscriptionRepository
    )

  /**
   * Creates the pool that decides how and how many service tasks run: ten platform threads, at most ten tasks.
   * This one is used for pull-strategies only.
   * Declare your own [ServiceTaskWorkerPool] bean with this name to change executor and limit, e.g.
   * `ServiceTaskWorkerPool(Executors.newVirtualThreadPerTaskExecutor(), maxConcurrentTasks = 200)`.
   */
  @Bean("cib-seven-embedded-service-task-worker-pool")
  @ConditionalOnMissingQualifiedBean(beanClass = ServiceTaskWorkerPool::class, qualifier = "cib-seven-embedded-service-task-worker-pool")
  @Qualifier("cib-seven-embedded-service-task-worker-pool")
  fun serviceTaskWorkerPool(): ServiceTaskWorkerPool = ServiceTaskWorkerPool.withPlatformThreads(
    threads = 10,
    threadFactory = ThreadContextClassLoaderThreadFactory(CibSevenEmbeddedAdapterAutoConfiguration::class.java.classLoader),
  )

  @Bean("cib-seven-embedded-task-outcome-recorder")
  @Qualifier("cib-seven-embedded-task-outcome-recorder")
  fun taskOutcomeRecorder(): TaskOutcomeRecorder = TaskOutcomeRecorder()

  @OptIn(ExperimentalTaskExecutionDecoratorApi::class)
  @Bean("cib-seven-embedded-task-execution-decoration")
  @Qualifier("cib-seven-embedded-task-execution-decoration")
  fun taskExecutionDecoration(decorators: ObjectProvider<TaskExecutionDecorator>): TaskExecutionDecoration =
    TaskExecutionDecoration(decorators.orderedStream().toList())

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies and async event listener execution.
   */
  @Bean("cib-seven-embedded-user-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "cib-seven-embedded-user-task-worker-executor")
  @Qualifier("cib-seven-embedded-user-task-worker-executor")
  fun userTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(
    10,
    ThreadContextClassLoaderThreadFactory(CibSevenEmbeddedAdapterAutoConfiguration::class.java.classLoader),
  )

}
