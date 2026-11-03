package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.subscribe

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecoration
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.OfficialClientServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskHandler
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.client.ExternalTaskClient
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskHandler
import org.cibseven.bpm.client.task.ExternalTaskService
import org.cibseven.bpm.client.topic.TopicSubscription
import org.cibseven.bpm.client.topic.TopicSubscriptionBuilder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class SubscribingServiceTaskDeliveryTest {

  companion object {
    const val TOPIC = "test-topic"
    const val TASK_ID = "task"
    const val RETRIES = 3
    const val RETRY_TIMEOUT_IN_SECONDS = 10L
  }

  private val topicSubscriptionBuilder: TopicSubscriptionBuilder = mock(defaultAnswer = RETURNS_SELF) {
    on { open() } doReturn mock<TopicSubscription>()
  }
  private val externalTaskClient: ExternalTaskClient = mock {
    on { subscribe(TOPIC) } doReturn topicSubscriptionBuilder
  }
  private val externalTaskService: ExternalTaskService = mock()
  private val externalTask: ExternalTask = mock {
    on { id } doReturn TASK_ID
    on { topicName } doReturn TOPIC
    on { retries } doReturn 2
    on { allVariables } doReturn emptyMap()
  }
  private val subscriptionRepository = InMemSubscriptionRepository()
  private val outcomeRecorder = TaskOutcomeRecorder()
  private val observedOutcomes = CopyOnWriteArrayList<TaskOutcome>()
  private val completionApi = OfficialClientServiceTaskCompletionApiImpl(
    externalTaskService = externalTaskService,
    subscriptionRepository = subscriptionRepository,
    failureRetrySupplier = LinearMemoryFailureRetrySupplier(RETRIES, RETRY_TIMEOUT_IN_SECONDS),
    outcomeRecorder = outcomeRecorder,
  )

  @Test
  fun `decorator sees completed when the handler completes the task`() {
    deliverWith { task, _ -> completionApi.completeTask(CompleteTaskCmd(task.taskId)) }

    assertThat(observedOutcomes).containsExactly(TaskOutcome.Completed)
  }

  @Test
  fun `decorator sees failed and the failure is reported once when the handler throws`() {
    val failure = IllegalStateException("handler failed")

    deliverWith { _, _ -> throw failure }

    verify(externalTaskService, times(1)).handleFailure(eq(TASK_ID), anyOrNull(), eq("handler failed"), eq(1), eq(RETRY_TIMEOUT_IN_SECONDS * 1000))
    assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("handler failed", 1, failure))
  }

  @Test
  fun `decorator sees failed and the failure is not reported twice when the handler fails the task itself`() {
    deliverWith { task, _ ->
      completionApi.failTask(FailTaskCmd(task.taskId, "worker failed", "details", 0, Duration.ofSeconds(5)))
      throw IllegalStateException("handler failed after failing the task")
    }

    verify(externalTaskService, times(1)).handleFailure(any<String>(), anyOrNull(), anyOrNull(), any(), any())
    verify(externalTaskService).handleFailure(TASK_ID, "worker failed", "details", 0, 5000L)
    assertThat(observedOutcomes).containsExactly(TaskOutcome.Failed("worker failed", 0))
  }

  @Test
  fun `decorator sees pending when the handler returns without an outcome`() {
    deliverWith { _, _ -> }

    assertThat(observedOutcomes).containsExactly(TaskOutcome.Pending)
  }

  private fun deliverWith(action: TaskHandler) {
    subscriptionRepository.createTaskSubscription(
      TaskSubscriptionHandle(
        taskType = TaskType.EXTERNAL,
        payloadDescription = null,
        restrictions = emptyMap(),
        taskDescriptionKey = TOPIC,
        action = action,
        termination = {},
      )
    )
    SubscribingServiceTaskDelivery(
      externalTaskClient = externalTaskClient,
      subscriptionRepository = subscriptionRepository,
      lockDurationInSeconds = 60L,
      retryTimeoutInSeconds = RETRY_TIMEOUT_IN_SECONDS,
      retries = RETRIES,
      outcomeRecorder = outcomeRecorder,
      decoration = TaskExecutionDecoration(listOf(TaskExecutionDecorator { _, _, execution -> observedOutcomes.add(execution.proceed()) })),
    ).subscribe()
    val handler = argumentCaptor<ExternalTaskHandler>()
    verify(topicSubscriptionBuilder).handler(handler.capture())
    handler.firstValue.execute(externalTask, externalTaskService)
  }
}
