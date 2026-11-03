package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.AbstractCibSevenEmbeddedApiITest
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullServiceTaskDeliveryITest.Companion.BPMN
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskHandler
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.cibseven.bpm.engine.externaltask.ExternalTask
import org.cibseven.bpm.engine.impl.util.ClockUtil
import org.cibseven.bpm.engine.test.Deployment
import org.cibseven.bpm.engine.test.junit5.ProcessEngineExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Deployment(resources = [BPMN])
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class EmbeddedPullServiceTaskDeliveryITest {

  companion object {
    const val BPMN = "bpmn/single-external-task.bpmn"
    const val PROCESS_KEY = "single-external-task"
    const val TOPIC = "single-external-task-topic"
    const val WORKER_ID = "delivery-itest-worker"
    const val RETRIES = 3
    const val LOCK_DURATION_IN_SECONDS = 60L

    @RegisterExtension
    val engine: ProcessEngineExtension = ProcessEngineExtension.builder().useProcessEngine(AbstractCibSevenEmbeddedApiITest.processEngine).build()
  }

  private val processEngine = AbstractCibSevenEmbeddedApiITest.processEngine
  private val externalTaskService = processEngine.externalTaskService
  private val subscriptionRepository = InMemSubscriptionRepository()
  private val outcomeRecorder = TaskOutcomeRecorder()
  private val executor = Executors.newFixedThreadPool(4)
  private val release = CountDownLatch(1)
  private val observedOutcomes = CopyOnWriteArrayList<TaskOutcome>()
  private val terminations = CopyOnWriteArrayList<TaskInformation>()
  private val completionApi = CibSevenServiceTaskCompletionApiImpl(
    WORKER_ID, externalTaskService, subscriptionRepository, LinearMemoryFailureRetrySupplier(RETRIES, 10L), outcomeRecorder
  )

  @AfterEach
  fun cleanUp() {
    release.countDown()
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
    ClockUtil.reset()
  }

  @Test
  fun `a slow task does not block a fast one`() {
    subscribe { task, payload -> if (payload["slow"] == true) release.await() else complete(task) }
    val slowInstanceId = startProcess(mapOf("slow" to true))
    val fastInstanceId = startProcess(mapOf("slow" to false))

    assertTimeoutPreemptively(Duration.ofSeconds(5)) { delivery().refresh() }

    await untilAsserted { assertThat(isEnded(fastInstanceId)).isTrue() }
    assertThat(isEnded(slowInstanceId)).isFalse()
  }

  @Test
  fun `never locks more tasks than the max concurrent tasks`() {
    val invocations = AtomicInteger()
    subscribe { _, _ -> invocations.incrementAndGet(); release.await() }
    repeat(5) { startProcess() }
    val delivery = delivery(ServiceTaskWorkerPool(executor, maxConcurrentTasks = 2))

    delivery.refresh()
    delivery.refresh()

    assertThat(externalTaskService.createExternalTaskQuery().locked().count()).isEqualTo(2)
    await untilAsserted { assertThat(invocations.get()).isEqualTo(2) }
  }

  @Test
  fun `a running task is not started twice and not terminated`() {
    val invocations = AtomicInteger()
    subscribe { _, _ -> invocations.incrementAndGet(); release.await() }
    startProcess()
    val delivery = delivery()
    delivery.refresh()
    await untilAsserted { assertThat(invocations.get()).isEqualTo(1) }

    advanceClockBeyondLockDuration()
    delivery.refresh()

    Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted {
      assertThat(invocations.get()).isEqualTo(1)
      assertThat(terminations).isEmpty()
    }
  }

  @Test
  fun `a gone task is terminated, a pending one is not`() {
    subscribe { _, _ -> }
    val instanceId = startProcess()
    val delivery = delivery()
    delivery.refresh()
    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.Pending) }

    delivery.refresh()
    assertThat(terminations).isEmpty()

    processEngine.runtimeService.deleteProcessInstance(instanceId, "gone")

    await untilAsserted {
      delivery.refresh()
      assertThat(terminations).singleElement().extracting { it.meta[TaskInformation.REASON] }.isEqualTo(TaskInformation.DELETE)
    }
  }

  @Test
  fun `decorator sees completed when the handler completes the task`() {
    subscribe { task, _ -> complete(task) }
    startProcess()

    delivery().refresh()

    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.Completed) }
  }

  @Test
  fun `decorator sees failed when the handler throws, reported once with camunda retries`() {
    val failure = IllegalStateException("boom")
    subscribe { _, _ -> throw failure }
    startProcess()
    val workerPool = ServiceTaskWorkerPool(executor, maxConcurrentTasks = 2)
    val delivery = delivery(workerPool)

    delivery.refresh()

    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("boom", RETRIES, failure)) }
    assertThat(singleTask().retries).isEqualTo(RETRIES)
    assertThat(singleTask().errorMessage).isEqualTo("boom")
    await untilAsserted { assertThat(workerPool.runningTaskCount).isZero() }

    advanceClockBeyondLockDuration()
    delivery.refresh()

    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("boom", RETRIES, failure), TaskOutcome.Failed("boom", RETRIES - 1, failure)) }
    assertThat(singleTask().retries).isEqualTo(RETRIES - 1)
  }

  @Test
  fun `decorator sees failed when the handler fails the task itself, without a second report`() {
    subscribe { task, _ ->
      completionApi.failTask(FailTaskCmd(task.taskId, "handled", "details", 5, Duration.ofMinutes(1))).get()
      throw IllegalStateException("rethrown after reporting")
    }
    startProcess()

    delivery().refresh()

    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("handled", 5)) }
    assertThat(singleTask().retries).isEqualTo(5)
    assertThat(singleTask().errorMessage).isEqualTo("handled")
  }

  @Test
  fun `decorator sees bpmn error when the handler completes the task by error`() {
    subscribe { task, _ -> completionApi.completeTaskByError(CompleteTaskByErrorCmd(task.taskId, "E-1", "business error") { emptyMap() }).get() }
    startProcess()

    delivery().refresh()

    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.BpmnError("E-1")) }
  }

  @Test
  fun `decorator sees pending when the handler returns without outcome`() {
    subscribe { _, _ -> }
    startProcess()

    delivery().refresh()

    await untilAsserted { assertThat(observedOutcomes).containsExactly(TaskOutcome.Pending) }
  }

  private fun delivery(workerPool: ServiceTaskWorkerPool = ServiceTaskWorkerPool(executor, maxConcurrentTasks = 2)) = EmbeddedPullServiceTaskDelivery(
    externalTaskService = externalTaskService,
    workerId = WORKER_ID,
    subscriptionRepository = subscriptionRepository,
    lockDurationInSeconds = LOCK_DURATION_IN_SECONDS,
    retryTimeoutInSeconds = 10L,
    retries = RETRIES,
    workerPool = workerPool,
    outcomeRecorder = outcomeRecorder,
    decoration = TaskExecutionDecoration(listOf(TaskExecutionDecorator { _, _, execution -> observedOutcomes.add(execution.proceed()) })),
  )

  private fun subscribe(action: TaskHandler) = subscriptionRepository.createTaskSubscription(
    TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = emptyMap(),
      taskDescriptionKey = TOPIC,
      action = action,
      termination = { terminations.add(it) },
    )
  )

  private fun complete(task: TaskInformation) {
    completionApi.completeTask(CompleteTaskCmd(task.taskId) { emptyMap() }).get()
  }

  private fun startProcess(variables: Map<String, Any> = emptyMap()): String =
    processEngine.runtimeService.startProcessInstanceByKey(PROCESS_KEY, variables).id

  private fun isEnded(instanceId: String) =
    processEngine.runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() == null

  private fun singleTask(): ExternalTask = externalTaskService.createExternalTaskQuery().singleResult()

  private fun advanceClockBeyondLockDuration() {
    ClockUtil.setCurrentTime(Date(ClockUtil.getCurrentTime().time + Duration.ofSeconds(LOCK_DURATION_IN_SECONDS * 2).toMillis()))
  }
}
