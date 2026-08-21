package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.decision.EvaluateDecisionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.schedule.DefaultPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.schedule.NoOpPullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.decision.EvaluateDecisionApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.cibseven.community.rest.client.api.*
import org.cibseven.community.rest.client.invoker.ApiClient
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

@AutoConfiguration(afterName = ["org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"])
@EnableConfigurationProperties(value = [Cib7RemoteAdapterProperties::class])
@Conditional(Cib7RemoteAdapterEnabledCondition::class)
class Cib7RemoteAdapterAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-200: Configuration applied." }
  }

  @Bean("cib7remote-task-subscription-api")
  @Qualifier("cib7remote-task-subscription-api")
  fun taskSubscriptionApi(subscriptionRepository: SubscriptionRepository): TaskSubscriptionApi = TaskSubscriptionApiImpl(
    subscriptionRepository = subscriptionRepository
  )

  @Bean("cib7remote-start-process-api")
  @Qualifier("cib7remote-start-process-api")
  fun startProcessApi(
    processDefinitionApiClient: ProcessDefinitionApiClient,
    messageApiClient: MessageApiClient,
    processInstanceApiClient: ProcessInstanceApiClient,
    valueMapper: ValueMapper,
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
  ): StartProcessApi = StartProcessApiImpl(
    processDefinitionApiClient = processDefinitionApiClient,
    messageApiClient = messageApiClient,
    processInstanceApiClient = processInstanceApiClient,
    processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
    valueMapper = valueMapper
  )

  @Bean("cib7remote-correlation-api")
  @Qualifier("cib7remote-correlation-api")
  fun correlationApi(messageApiClient: MessageApiClient, valueMapper: ValueMapper): CorrelationApi = CorrelationApiImpl(
    messageApiClient = messageApiClient,
    valueMapper = valueMapper
  )

  @Bean("cib7remote-signal-api")
  @Qualifier("cib7remote-signal-api")
  fun signalApi(signalApiClient: SignalApiClient, valueMapper: ValueMapper): SignalApi = SignalApiImpl(
    signalApiClient = signalApiClient,
    valueMapper = valueMapper
  )

  @Bean("cib7remote-deploy-api")
  @Qualifier("cib7remote-deploy-api")
  fun deployApi(apiClient: ApiClient): DeploymentApi = DeploymentApiImpl(
    apiClient = apiClient
  )

  @Bean("cib7remote-evaluate-decision-api")
  @Qualifier("cib7remote-evaluate-decision-api")
  fun evaluateDecisionApi(decisionDefinitionApiClient: DecisionDefinitionApiClient, valueMapper: ValueMapper,
                          dataConverter: AdapterDataConverter): EvaluateDecisionApi = EvaluateDecisionApiImpl(
    decisionDefinitionApiClient = decisionDefinitionApiClient,
    valueMapper = valueMapper,
    dataConverter = dataConverter
  )

  /**
   * Subscription Repository.
   */
  @Bean
  @ConditionalOnMissingBean
  fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

  /**
   * Creates a default fixed thread pool used for external task worker executions.
   * This one is used for pull-strategies only.
   */
  @Bean("cib7remote-service-task-worker-executor")
  @Qualifier("cib7remote-service-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ThreadPoolExecutor::class, qualifier = "cib7remote-service-task-worker-executor")
  fun serviceTaskWorkerExecutor(c7AdapterProperties: Cib7RemoteAdapterProperties): ThreadPoolExecutor =
    ThreadPoolExecutor(
      c7AdapterProperties.serviceTasks.workerThreadPoolSize,
      c7AdapterProperties.serviceTasks.workerThreadPoolSize,
      0L, TimeUnit.MILLISECONDS,
      LinkedBlockingQueue(c7AdapterProperties.serviceTasks.workerThreadPoolQueueCapacity)
    )

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry::class)
  fun defaultPullServiceTaskDeliveryMetrics(registry: MeterRegistry): PullServiceTaskDeliveryMetrics =
    DefaultPullServiceTaskDeliveryMetrics(registry)

  @Bean
  @ConditionalOnMissingBean(PullServiceTaskDeliveryMetrics::class, MeterRegistry::class)
  fun noOpPullServiceTaskDeliveryMetrics(): PullServiceTaskDeliveryMetrics =
    NoOpPullServiceTaskDeliveryMetrics()

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   * This one is used for pull-strategies only.
   */
  @Bean("cib7remote-user-task-worker-executor")
  @Qualifier("cib7remote-user-task-worker-executor")
  @ConditionalOnMissingQualifiedBean(beanClass = ExecutorService::class, qualifier = "cib7remote-user-task-worker-executor")
  fun userTaskWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

  /**
   * Failure retry supplier.
   */
  @Bean("cib7remote-failure-retry-supplier")
  @Qualifier("cib7remote-failure-retry-supplier")
  @ConditionalOnMissingBean
  fun defaultFailureRetrySupplier(c7AdapterProperties: Cib7RemoteAdapterProperties): FailureRetrySupplier {
    return LinearMemoryFailureRetrySupplier(
      retry = c7AdapterProperties.serviceTasks.retries,
      retryTimeout = c7AdapterProperties.serviceTasks.retryTimeoutInSeconds
    )
  }

}
