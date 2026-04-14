package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.schedule

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy.EMBEDDED_SCHEDULED
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.UserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.Cib7EmbeddedAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.ConditionalOnServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions.ConditionalOnUserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.toolisticon.spring.condition.ConditionalOnMissingQualifiedBean
import jakarta.annotation.PostConstruct
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.RepositoryService
import org.cibseven.bpm.engine.TaskService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading
import org.springframework.boot.autoconfigure.thread.Threading
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ExecutorService

private val logger = KotlinLogging.logger {}

@Configuration
@EnableScheduling
@EnableAsync
@AutoConfigureAfter(Cib7EmbeddedAdapterAutoConfiguration::class)
@Conditional(Cib7EmbeddedAdapterEnabledCondition::class)
class Cib7EmbeddedSchedulingAutoConfiguration {

  @PostConstruct
  fun report() {
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-201: Configuration for schedule-based deliver applied." }
  }

  @Bean("cib-seven-embedded-task-scheduler")
  @Qualifier("cib-seven-embedded-task-scheduler")
  @Order(200)
  @ConditionalOnMissingQualifiedBean(beanClass = TaskScheduler::class, qualifier = "cib-seven-embedded-task-scheduler")
  fun taskScheduler(): TaskScheduler {
    val threadPoolTaskScheduler = ThreadPoolTaskScheduler()
    threadPoolTaskScheduler.poolSize = 2 // we have two schedulers, one for user tasks one for service tasks
    threadPoolTaskScheduler.threadNamePrefix = "CIB-SEVEN-EMBEDDED-SCHEDULER-"
    return threadPoolTaskScheduler
  }

  @Bean("taskScheduler")
  @Order(100)
  @ConditionalOnThreading(Threading.VIRTUAL)
  fun taskSchedulerVirtualThreads(builder: SimpleAsyncTaskSchedulerBuilder): SimpleAsyncTaskScheduler {
    return builder.build()
  }

  @Bean("taskScheduler")
  @Order(100)
  @ConditionalOnThreading(Threading.PLATFORM)
  fun taskSchedulerPlatformThreads(threadPoolTaskSchedulerBuilder: ThreadPoolTaskSchedulerBuilder): ThreadPoolTaskScheduler {
    return threadPoolTaskSchedulerBuilder.build()
  }

  @Bean("cib-seven-embedded-service-task-delivery")
  @Qualifier("cib-seven-embedded-service-task-delivery")
  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = EMBEDDED_SCHEDULED
  )
  fun serviceTaskDelivery(
    subscriptionRepository: SubscriptionRepository,
    externalTaskService: ExternalTaskService,
    adapterProperties: Cib7EmbeddedAdapterProperties,
    @Qualifier("cib-seven-embedded-service-task-worker-executor")
    executorService: ExecutorService
  ) = EmbeddedPullServiceTaskDelivery(
    subscriptionRepository = subscriptionRepository,
    externalTaskService = externalTaskService,
    workerId = adapterProperties.serviceTasks.workerId,
    maxTasks = adapterProperties.serviceTasks.maxTaskCount,
    lockDurationInSeconds = adapterProperties.serviceTasks.lockTimeInSeconds,
    retryTimeoutInSeconds = adapterProperties.serviceTasks.retryTimeoutInSeconds,
    retries = adapterProperties.serviceTasks.retries,
    executorService = executorService
  )

  @Bean("cib-seven-embedded-process-definition-meta-data-resolver")
  @Qualifier("cib-seven-embedded-process-definition-meta-data-resolver")
  @ConditionalOnMissingQualifiedBean(
    beanClass = ProcessDefinitionMetaDataResolver::class,
    qualifier = "cib-seven-embedded-process-definition-meta-data-resolver"
  )
  fun cachingProcessDefinitionMetaDataResolver(repositoryService: RepositoryService): ProcessDefinitionMetaDataResolver {
    return CachingProcessDefinitionMetaDataResolver(repositoryService = repositoryService)
  }

  @Bean("cib-seven-embedded-schedule-user-task-delivery")
  @Qualifier("cib-seven-embedded-schedule-user-task-delivery")
  @ConditionalOnUserTaskDeliveryStrategy(
    strategies = [UserTaskDeliveryStrategy.EMBEDDED_SCHEDULED]
  )
  fun embeddedScheduledUserTaskDelivery(
    subscriptionRepository: SubscriptionRepository,
    taskService: TaskService,
    @Qualifier("cib-seven-embedded-process-definition-meta-data-resolver")
    processDefinitionMetaDataResolver: ProcessDefinitionMetaDataResolver,
    @Qualifier("cib-seven-embedded-service-task-worker-executor")
    executorService: ExecutorService
  ): EmbeddedPullUserTaskDelivery {
    return EmbeddedPullUserTaskDelivery(
      subscriptionRepository = subscriptionRepository,
      taskService = taskService,
      processDefinitionMetaDataResolver = processDefinitionMetaDataResolver,
      executorService = executorService
    )
  }
}
