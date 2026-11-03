package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.decorator

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.TaskMetaKeys
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedTestApplication
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.RepositoryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
@SpringBootTest(
  classes = [Cib7EmbeddedTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(Cib7EmbeddedSpringTaskExecutionDecoratorITest.DecoratorConfiguration::class)
@ActiveProfiles("itest")
@DirtiesContext
class Cib7EmbeddedSpringTaskExecutionDecoratorITest {

  companion object {
    const val KEY = "simple-process"
    const val BPMN = "bpmn/$KEY.bpmn"
    const val EXTERNAL_TASK = "execute-action-external"
    const val SERVICE_TASK = "service-do-action1"
    const val HANDLER_MODE = "handlerMode"
  }

  data class TraceEntry(val event: String, val taskId: String)

  @TestConfiguration(proxyBeanMethods = false)
  class DecoratorConfiguration {

    @Bean
    fun trace(): MutableList<TraceEntry> = CopyOnWriteArrayList()

    @Bean
    @Order(1)
    fun tracingDecorator(trace: MutableList<TraceEntry>) = TaskExecutionDecorator { task, _, execution ->
      Runnable {
        trace += TraceEntry("before:${task.meta[TaskMetaKeys.ACTIVITY_INSTANCE_ID]}", task.taskId)
        try {
          execution.run()
        } finally {
          trace += TraceEntry("after", task.taskId)
        }
      }
    }

    @Bean
    @Order(2)
    fun throwingDecorator() = TaskExecutionDecorator { _, _, _ -> throw IllegalStateException("decorate failed") }
  }

  @Autowired
  lateinit var repositoryService: RepositoryService

  @Autowired
  lateinit var externalTaskService: ExternalTaskService

  @Autowired
  lateinit var startProcessApi: StartProcessApi

  @Autowired
  lateinit var taskSubscriptionApi: TaskSubscriptionApi

  @Autowired
  lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @Autowired
  lateinit var subscriptionRepository: SubscriptionRepository

  @Autowired
  lateinit var trace: MutableList<TraceEntry>

  @BeforeEach
  fun setUp() {
    repositoryService.createDeployment().name("Simple Process").addClasspathResource(BPMN).deploy()
    taskSubscriptionApi.subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = CommonRestrictions.builder().build(),
        taskType = TaskType.EXTERNAL,
        taskDescriptionKey = EXTERNAL_TASK,
        payloadDescription = null,
        action = { taskInformation, payload ->
          trace += TraceEntry("handler", taskInformation.taskId)
          when (payload[HANDLER_MODE]) {
            "complete" -> serviceTaskCompletionApi.completeTask(CompleteTaskCmd(taskInformation.taskId) { emptyMap() }).get()
            "fail" -> throw IllegalStateException("handler failed")
          }
        },
        termination = { }
      )
    ).get()
  }

  @AfterEach
  fun tearDown() {
    (subscriptionRepository as InMemSubscriptionRepository).deleteAllTaskSubscriptions()
    trace.clear()
  }

  @Test
  fun `decorates a completed external task and a throwing decorator does not affect it`() {
    val processInstanceId = startProcess("complete")

    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(trace.map { it.event }).contains("after")
    }

    assertThat(
      externalTaskService.createExternalTaskQuery().processInstanceId(processInstanceId).activityId(SERVICE_TASK).count()
    ).isZero()
    val taskId = trace.first().taskId
    val activityInstanceId = trace.first().event.removePrefix("before:")
    assertThat(activityInstanceId).startsWith("$SERVICE_TASK:")
    assertThat(trace).containsExactly(
      TraceEntry("before:$activityInstanceId", taskId),
      TraceEntry("handler", taskId),
      TraceEntry("after", taskId)
    )
  }

  @Test
  fun `decorates the failure handling of the delivery`() {
    val processInstanceId = startProcess("fail")

    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(trace.map { it.event }).contains("after")
    }

    val failedTask = externalTaskService.createExternalTaskQuery().processInstanceId(processInstanceId).activityId(SERVICE_TASK).singleResult()
    assertThat(failedTask.errorMessage).isEqualTo("handler failed")
    assertThat(trace.map { it.event }).endsWith("handler", "after")
  }

  private fun startProcess(mode: String): String =
    startProcessApi.startProcess(
      StartProcessByDefinitionCmd(definitionKey = KEY, payloadSupplier = { mapOf(HANDLER_MODE to mode) })
    ).get().instanceId
}
