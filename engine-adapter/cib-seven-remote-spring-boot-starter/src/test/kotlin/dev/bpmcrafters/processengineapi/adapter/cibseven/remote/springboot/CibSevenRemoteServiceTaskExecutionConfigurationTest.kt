package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.client.CibSevenRemoteClientAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskHandler
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.dto.FetchExternalTasksDto
import org.cibseven.community.rest.client.dto.LockedExternalTaskDto
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.OffsetDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

private const val PROCESS_DEFINITION_META_DATA_RESOLVER_BEAN_NAME = "cib-seven-remote-process-definition-meta-data-resolver"
private const val WORKER_POOL_BEAN_NAME = "cib-seven-remote-service-task-worker-pool"
private const val TOPIC = "topic"

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class CibSevenRemoteServiceTaskExecutionConfigurationTest {

  private val contextRunner = ApplicationContextRunner()
    .withConfiguration(
      AutoConfigurations.of(
        CibSevenRemoteSerializationAutoConfiguration::class.java,
        CibSevenRemoteClientAutoConfiguration::class.java,
        CibSevenRemoteAdapterAutoConfiguration::class.java,
      )
    )
    .withBean(PROCESS_DEFINITION_META_DATA_RESOLVER_BEAN_NAME, ProcessDefinitionMetaDataResolver::class.java, { mock<ProcessDefinitionMetaDataResolver>() })
    .withPropertyValues(
      "$DEFAULT_PREFIX.enabled=true",
      "$DEFAULT_PREFIX.service-tasks.worker-id=worker",
      "$DEFAULT_PREFIX.service-tasks.delivery-strategy=disabled",
      "$DEFAULT_PREFIX.user-tasks.delivery-strategy=disabled",
    )

  @Test
  fun `pull delivery runs tasks on a custom worker pool bean`() {
    contextRunner
      .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration::class.java, CibSevenRemotePullServicesAutoConfiguration::class.java))
      .withPropertyValues("$DEFAULT_PREFIX.service-tasks.delivery-strategy=remote_scheduled")
      .withUserConfiguration(CustomWorkerPoolConfiguration::class.java)
      .run { context ->
        val customExecutor = context.getBean(CountingExecutor::class.java)
        val deliveredTaskIds = CopyOnWriteArrayList<String>()
        context.getBean(SubscriptionRepository::class.java).createTaskSubscription(externalTaskSubscription { task, _ -> deliveredTaskIds.add(task.taskId) })

        context.getBean(PullServiceTaskDelivery::class.java).refresh()

        val fetches = argumentCaptor<FetchExternalTasksDto>()
        verify(context.getBean(ExternalTaskApi::class.java)).fetchAndLock(fetches.capture())
        assertThat(context.getBean(WORKER_POOL_BEAN_NAME)).isSameAs(context.getBean(CustomWorkerPoolConfiguration::class.java).workerPool)
        assertThat(fetches.firstValue.maxTasks).isEqualTo(1)
        assertThat(customExecutor.executions.get()).isEqualTo(1)
        assertThat(deliveredTaskIds).containsExactly("task")
      }
  }

  @Test
  fun `task execution decoration uses the decorator beans`() {
    contextRunner.withUserConfiguration(DecoratorConfiguration::class.java).run { context ->
      val decoration = context.getBean(TaskExecutionDecoration::class.java)

      decoration.run(TaskInformation("task", emptyMap()), emptyMap()) { TaskOutcome.Completed }

      assertThat(context.getBean(DecoratorConfiguration::class.java).observedOutcomes).containsExactly(TaskOutcome.Completed)
    }
  }

  private fun externalTaskSubscription(action: TaskHandler) = TaskSubscriptionHandle(
    taskType = TaskType.EXTERNAL,
    payloadDescription = null,
    restrictions = emptyMap(),
    taskDescriptionKey = TOPIC,
    action = action,
    termination = {},
  )

  class CountingExecutor : Executor {
    val executions = AtomicInteger()

    override fun execute(command: Runnable) {
      executions.incrementAndGet()
      command.run()
    }
  }

  @Configuration(proxyBeanMethods = false)
  class CustomWorkerPoolConfiguration {
    private val customExecutor = CountingExecutor()
    val workerPool = ServiceTaskWorkerPool(customExecutor, maxConcurrentTasks = 1)

    @Bean
    fun customExecutor() = customExecutor

    @Bean(WORKER_POOL_BEAN_NAME)
    fun customServiceTaskWorkerPool() = workerPool

    @Bean
    fun externalTaskApi(): ExternalTaskApi = mock {
      on { fetchAndLock(any<FetchExternalTasksDto>()) } doReturn listOf(
        LockedExternalTaskDto()
          .id("task")
          .topicName(TOPIC)
          .processDefinitionId("process-definition")
          .lockExpirationTime(OffsetDateTime.now().plusMinutes(1))
          .variables(emptyMap())
      )
    }
  }

  @Configuration(proxyBeanMethods = false)
  class DecoratorConfiguration {
    val observedOutcomes = CopyOnWriteArrayList<TaskOutcome>()

    @Bean
    fun outcomeObservingDecorator() = TaskExecutionDecorator { _, _, execution -> observedOutcomes.add(execution.proceed()) }
  }
}
