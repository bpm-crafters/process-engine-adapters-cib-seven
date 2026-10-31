package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedTestApplication
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor.MetricsTaskHandlerInterceptor.Companion.EXECUTIONS
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor.MetricsTaskHandlerInterceptor.Companion.OUTCOME_COMPLETED
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor.MetricsTaskHandlerInterceptor.Companion.OUTCOME_COMPLETED_BY_ERROR
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor.MetricsTaskHandlerInterceptor.Companion.OUTCOME_EXCEPTION
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.TaskType
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.cibseven.bpm.engine.RepositoryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Demonstrates task handler interceptors with the embedded CIB seven adapter:
 * a logging interceptor and a metrics interceptor are registered as Spring beans and run as a chain
 * around every service task handler.
 */
@SpringBootTest(
  classes = [Cib7EmbeddedTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(InterceptorTestConfiguration::class)
@ActiveProfiles("itest")
@DirtiesContext
@ExtendWith(OutputCaptureExtension::class)
class Cib7EmbeddedSpringTaskHandlerInterceptorITest {

  companion object {
    const val KEY = "simple-process"
    const val BPMN = "bpmn/$KEY.bpmn"
    const val EXTERNAL_TASK = "execute-action-external"
    const val MODE = "handlerMode"
  }

  @Autowired
  lateinit var repositoryService: RepositoryService

  @Autowired
  lateinit var startProcessApi: StartProcessApi

  @Autowired
  lateinit var taskSubscriptionApi: TaskSubscriptionApi

  @Autowired
  lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @Autowired
  lateinit var subscriptionRepository: SubscriptionRepository

  @Autowired
  lateinit var meterRegistry: MeterRegistry

  /** Ids of tasks the handler has been invoked for. */
  private val handledTaskIds = ConcurrentLinkedQueue<String>()

  @BeforeEach
  fun setUp() {
    repositoryService.createDeployment().name("Simple Process").addClasspathResource(BPMN).deploy()
    // handler behaviour is controlled by a process variable, so that it does not matter which test started the task
    taskSubscriptionApi.subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = CommonRestrictions.builder().build(),
        taskType = TaskType.EXTERNAL,
        taskDescriptionKey = EXTERNAL_TASK,
        payloadDescription = null,
        action = { taskInformation, payload ->
          handledTaskIds.add(taskInformation.taskId)
          when (payload[MODE]) {
            "complete" -> serviceTaskCompletionApi.completeTask(CompleteTaskCmd(taskInformation.taskId) { emptyMap() }).get()
            "bpmnError" -> serviceTaskCompletionApi.completeTaskByError(
              CompleteTaskByErrorCmd(taskInformation.taskId, "action_error", "something went wrong") { emptyMap() }
            ).get()

            "throw" -> throw IllegalStateException("handler exploded")
            else -> Unit
          }
        },
        termination = { }
      )
    ).get()
  }

  @AfterEach
  fun tearDown() {
    (subscriptionRepository as InMemSubscriptionRepository).deleteAllTaskSubscriptions()
  }

  @Test
  fun `should run logging and metrics interceptor around a completed external task`(output: CapturedOutput) {
    val before = executions(OUTCOME_COMPLETED)

    startProcess("complete")

    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(executions(OUTCOME_COMPLETED)).isEqualTo(before + 1)
    }
    val taskId = handledTaskIds.first()
    assertInterceptorOrder(output, taskId, "Completed")
    assertThat(meterRegistry.find(MetricsTaskHandlerInterceptor.DURATION).tag("taskDescriptionKey", EXTERNAL_TASK).timer()!!.count())
      .isGreaterThanOrEqualTo(1)
  }

  @Test
  fun `should report BPMN error completion as outcome to both interceptors`(output: CapturedOutput) {
    val before = executions(OUTCOME_COMPLETED_BY_ERROR)

    startProcess("bpmnError")

    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(executions(OUTCOME_COMPLETED_BY_ERROR)).isEqualTo(before + 1)
    }
    assertInterceptorOrder(output, handledTaskIds.first(), "BPMN error action_error")
  }

  @Test
  fun `should let handler exceptions pass through the interceptor chain to the adapter`(output: CapturedOutput) {
    val before = executions(OUTCOME_EXCEPTION)

    startProcess("throw")

    await().atMost(Duration.ofSeconds(15)).untilAsserted {
      assertThat(executions(OUTCOME_EXCEPTION)).isGreaterThan(before)
    }
    val taskId = handledTaskIds.first()
    assertInterceptorOrder(output, taskId, "exception handler exploded")
    // the exception still reaches the delivery which fails the task in the engine
    await().atMost(Duration.ofSeconds(5)).untilAsserted {
      assertThat(output.out).contains("PROCESS-ENGINE-CIB7-EMBEDDED-034: successfully failed delivering task $taskId")
    }
  }

  private fun startProcess(mode: String) {
    startProcessApi.startProcess(StartProcessByDefinitionCmd(definitionKey = KEY, payloadSupplier = { mapOf(MODE to mode) })).get()
  }

  private fun executions(outcome: String): Double =
    meterRegistry.find(EXECUTIONS)
      .tags("taskType", TaskType.EXTERNAL.name, "taskDescriptionKey", EXTERNAL_TASK, "outcome", outcome)
      .counter()?.count() ?: 0.0

  /**
   * Logging is the outer interceptor, metrics the inner one:
   * logging(before) -> metrics -> handler -> metrics(counted) -> logging(after)
   */
  private fun assertInterceptorOrder(output: CapturedOutput, taskId: String, expectedOutcomeLog: String) {
    val out = output.out
    val loggingBefore = out.indexOf("${LoggingTaskHandlerInterceptor.PREFIX} before task $taskId")
    val metricsCounted = out.indexOf("${MetricsTaskHandlerInterceptor.PREFIX} counted task $taskId")
    val loggingAfter = out.indexOf("${LoggingTaskHandlerInterceptor.PREFIX} after task $taskId: $expectedOutcomeLog")
    assertThat(loggingBefore).describedAs("logging interceptor before handler").isGreaterThanOrEqualTo(0)
    assertThat(metricsCounted).describedAs("metrics interceptor after handler").isGreaterThan(loggingBefore)
    assertThat(loggingAfter).describedAs("logging interceptor after handler").isGreaterThan(metricsCounted)
  }
}
