package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.FeignServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics.FetchAndLockSkipReason.QUEUE_FULL
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskHandler
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.cibseven.bpm.engine.variable.Variables
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.dto.ExternalTaskDto
import org.cibseven.community.rest.client.dto.ExternalTaskFailureDto
import org.cibseven.community.rest.client.dto.ExternalTaskQueryDto
import org.cibseven.community.rest.client.dto.FetchExternalTasksDto
import org.cibseven.community.rest.client.dto.LockedExternalTaskDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class PullServiceTaskDeliveryTest {

  companion object {
    const val WORKER_ID = "test-worker"
    const val TOPIC = "test-topic"
    const val RETRIES = 3
    const val RETRY_TIMEOUT_IN_SECONDS = 10L
  }

  private val externalTaskApi: ExternalTaskApi = mock()
  private val valueMapper: ValueMapper = mock {
    on { mapDtos(any(), any()) } doReturn Variables.createVariables()
  }
  private val metrics: PullServiceTaskDeliveryMetrics = mock()
  private val subscriptionRepository = InMemSubscriptionRepository()
  private val outcomeRecorder = TaskOutcomeRecorder()
  private val observedOutcomes = CopyOnWriteArrayList<TaskOutcome>()
  private val terminatedTasks = CopyOnWriteArrayList<TaskInformation>()
  private val completionApi = FeignServiceTaskCompletionApiImpl(
    workerId = WORKER_ID,
    externalTaskApi = externalTaskApi,
    subscriptionRepository = subscriptionRepository,
    failureRetrySupplier = LinearMemoryFailureRetrySupplier(RETRIES, RETRY_TIMEOUT_IN_SECONDS),
    valueMapper = valueMapper,
    outcomeRecorder = outcomeRecorder,
  )
  private val releaseSlowTasks = CountDownLatch(1)
  private val threadPool = Executors.newCachedThreadPool()

  @AfterEach
  fun releaseThreads() {
    releaseSlowTasks.countDown()
    threadPool.shutdown()
  }

  @Test
  fun `does not wait for a slow task and runs a fast task meanwhile`() {
    val workerPool = ServiceTaskWorkerPool(threadPool, maxConcurrentTasks = 2)
    subscribe { task, _ -> if (task.taskId == "slow") awaitRelease() else completionApi.completeTask(CompleteTaskCmd(task.taskId)) }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("slow"), lockedTask("fast")))

    delivery(workerPool).refresh()

    await untilAsserted { verify(externalTaskApi).completeExternalTaskResource(eq("fast"), any()) }
    assertThat(workerPool.isRunning("slow")).isTrue()
  }

  @Test
  fun `never locks more tasks than free slots`() {
    val workerPool = ServiceTaskWorkerPool(threadPool, maxConcurrentTasks = 2)
    subscribe { _, _ -> awaitRelease() }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>()))
      .thenReturn(listOf(lockedTask("first")))
      .thenReturn(listOf(lockedTask("second")))
    val delivery = delivery(workerPool)

    repeat(3) { delivery.refresh() }

    val fetches = argumentCaptor<FetchExternalTasksDto>()
    verify(externalTaskApi, times(2)).fetchAndLock(fetches.capture())
    assertThat(fetches.allValues.map { it.maxTasks }).containsExactly(2, 1)
    verify(metrics).incrementFetchAndLockTasksSkippedCounter(QUEUE_FULL)
  }

  @Test
  fun `does not start a running task again and does not terminate it`() {
    val workerPool = ServiceTaskWorkerPool(threadPool, maxConcurrentTasks = 2)
    val startedTasks = CopyOnWriteArrayList<String>()
    subscribe { task, _ ->
      startedTasks.add(task.taskId)
      awaitRelease()
    }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("running")))
    val delivery = delivery(workerPool)

    delivery.refresh()
    await untilAsserted { assertThat(startedTasks).containsExactly("running") }
    delivery.refresh()

    assertThat(startedTasks).containsExactly("running")
    assertThat(terminatedTasks).isEmpty()
    verify(externalTaskApi, never()).queryExternalTasks(anyOrNull(), anyOrNull(), any())
  }

  @Test
  fun `terminates a delivered task that no longer exists in the engine`() {
    val subscription = subscribe { _, _ -> }
    subscriptionRepository.activateSubscriptionForTask("gone", subscription)
    subscriptionRepository.activateSubscriptionForTask("existing", subscription)
    whenever(externalTaskApi.queryExternalTasks(anyOrNull(), anyOrNull(), any())).thenReturn(listOf(ExternalTaskDto().id("existing")))

    delivery(synchronousWorkerPool()).refresh()

    val queries = argumentCaptor<ExternalTaskQueryDto>()
    verify(externalTaskApi).queryExternalTasks(anyOrNull(), anyOrNull(), queries.capture())
    assertThat(queries.firstValue.externalTaskIdIn).containsExactlyInAnyOrder("gone", "existing")
    assertThat(terminatedTasks.map { it.taskId }).containsExactly("gone")
    assertThat(terminatedTasks.single().meta[TaskInformation.REASON]).isEqualTo(TaskInformation.DELETE)
  }

  @Test
  fun `queries the existence of delivered tasks in chunks of 500`() {
    val subscription = subscribe { _, _ -> }
    repeat(501) { subscriptionRepository.activateSubscriptionForTask("task-$it", subscription) }

    delivery(ServiceTaskWorkerPool(Executor(Runnable::run), maxConcurrentTasks = 600)).refresh()

    val queries = argumentCaptor<ExternalTaskQueryDto>()
    verify(externalTaskApi, times(2)).queryExternalTasks(anyOrNull(), anyOrNull(), queries.capture())
    assertThat(queries.allValues.map { it.externalTaskIdIn!!.size }).containsExactly(500, 1)
  }

  @Test
  fun `decorator sees completed when the handler completes the task`() {
    subscribe { task, _ -> completionApi.completeTask(CompleteTaskCmd(task.taskId)) }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("task")))

    delivery(synchronousWorkerPool()).refresh()

    assertThat(observedOutcomes).containsExactly(TaskOutcome.Completed)
  }

  @Test
  fun `decorator sees failed and the failure is reported once when the handler throws`() {
    val failure = IllegalStateException("handler failed")
    subscribe { _, _ -> throw failure }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("task", retries = 2)))

    delivery(synchronousWorkerPool()).refresh()

    val failures = argumentCaptor<ExternalTaskFailureDto>()
    verify(externalTaskApi, times(1)).handleFailure(eq("task"), failures.capture())
    assertThat(failures.firstValue.retries!!).isEqualTo(1)
    assertThat(failures.firstValue.retryTimeout!!).isEqualTo(RETRY_TIMEOUT_IN_SECONDS * 1000)
    assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("handler failed", 1, failure))
  }

  @Test
  fun `reports the configured retries when the engine reports none`() {
    subscribe { _, _ -> throw IllegalStateException("handler failed") }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("task", retries = null)))

    delivery(synchronousWorkerPool()).refresh()

    val failures = argumentCaptor<ExternalTaskFailureDto>()
    verify(externalTaskApi).handleFailure(eq("task"), failures.capture())
    assertThat(failures.firstValue.retries!!).isEqualTo(RETRIES)
  }

  @Test
  fun `decorator sees failed and the failure is not reported twice when the handler fails the task itself`() {
    subscribe { task, _ ->
      completionApi.failTask(FailTaskCmd(task.taskId, "worker failed", "details", 1, Duration.ofSeconds(5)))
      throw IllegalStateException("handler failed after failing the task")
    }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("task", retries = 2)))

    delivery(synchronousWorkerPool()).refresh()

    val failures = argumentCaptor<ExternalTaskFailureDto>()
    verify(externalTaskApi, times(1)).handleFailure(eq("task"), failures.capture())
    assertThat(failures.firstValue.errorMessage).isEqualTo("worker failed")
    assertThat(failures.firstValue.retryTimeout!!).isEqualTo(5000L)
    assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("worker failed", 1))
  }

  @Test
  fun `decorator sees bpmn error when the handler completes the task by error`() {
    subscribe { task, _ -> completionApi.completeTaskByError(CompleteTaskByErrorCmd(task.taskId, "ERROR_CODE", "message")) }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("task")))

    delivery(synchronousWorkerPool()).refresh()

    assertThat(observedOutcomes).containsExactly(TaskOutcome.BpmnError("ERROR_CODE"))
  }

  @Test
  fun `decorator sees pending when the handler returns without an outcome`() {
    subscribe { _, _ -> }
    whenever(externalTaskApi.fetchAndLock(any<FetchExternalTasksDto>())).thenReturn(listOf(lockedTask("task")))

    delivery(synchronousWorkerPool()).refresh()

    assertThat(observedOutcomes).containsExactly(TaskOutcome.Pending)
    verify(externalTaskApi, never()).handleFailure(any(), any())
  }

  private fun delivery(workerPool: ServiceTaskWorkerPool) = PullServiceTaskDelivery(
    externalTaskApi = externalTaskApi,
    processDefinitionMetaDataResolver = mock<ProcessDefinitionMetaDataResolver>(),
    workerId = WORKER_ID,
    subscriptionRepository = subscriptionRepository,
    lockDurationInSeconds = 60L,
    retryTimeoutInSeconds = RETRY_TIMEOUT_IN_SECONDS,
    retries = RETRIES,
    workerPool = workerPool,
    outcomeRecorder = outcomeRecorder,
    decoration = TaskExecutionDecoration(listOf(TaskExecutionDecorator { _, _, execution -> observedOutcomes.add(execution.proceed()) })),
    valueMapper = valueMapper,
    deserializeOnServer = false,
    metrics = metrics,
  )

  private fun synchronousWorkerPool() = ServiceTaskWorkerPool(Executor(Runnable::run), maxConcurrentTasks = 10)

  private fun subscribe(action: TaskHandler) = TaskSubscriptionHandle(
    taskType = TaskType.EXTERNAL,
    payloadDescription = null,
    restrictions = emptyMap(),
    taskDescriptionKey = TOPIC,
    action = action,
    termination = { terminatedTasks.add(it) },
  ).also { subscriptionRepository.createTaskSubscription(it) }

  private fun lockedTask(id: String, retries: Int? = null) = LockedExternalTaskDto()
    .id(id)
    .topicName(TOPIC)
    .processDefinitionId("process-definition")
    .lockExpirationTime(OffsetDateTime.now().plusMinutes(1))
    .variables(emptyMap())
    .retries(retries)

  private fun awaitRelease() {
    releaseSlowTasks.await(10, TimeUnit.SECONDS)
  }
}
