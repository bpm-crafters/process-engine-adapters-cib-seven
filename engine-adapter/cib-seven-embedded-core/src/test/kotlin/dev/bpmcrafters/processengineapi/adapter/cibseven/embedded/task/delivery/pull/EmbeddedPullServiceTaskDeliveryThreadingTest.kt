package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.externaltask.ExternalTask
import org.cibseven.bpm.engine.externaltask.ExternalTaskQuery
import org.cibseven.bpm.engine.externaltask.ExternalTaskQueryBuilder
import org.cibseven.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder
import org.cibseven.bpm.engine.externaltask.LockedExternalTask
import org.cibseven.bpm.engine.variable.Variables
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

@Timeout(30)
internal class EmbeddedPullServiceTaskDeliveryThreadingTest {

  private val workerId = "worker"
  private val slowTopic = "slow-topic"
  private val fastTopic = "fast-topic"

  private val externalTaskService = mock<ExternalTaskService>()
  private val fetchAndLockBuilder = mock<ExternalTaskQueryBuilder>()
  private val existingTasksQuery = mock<ExternalTaskQuery>(defaultAnswer = RETURNS_SELF)
  private val subscriptionRepository = InMemSubscriptionRepository()
  private val executor = ThreadPoolExecutor(2, 2, 0, MILLISECONDS, LinkedBlockingQueue())

  private val taskDelivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = externalTaskService,
    workerId = workerId,
    subscriptionRepository = subscriptionRepository,
    maxTasks = 10,
    lockDurationInSeconds = 60,
    retryTimeoutInSeconds = 10,
    retries = 1,
    executorService = executor,
  )

  private val slowTasksMayFinish = CountDownLatch(1)
  private val startedTaskIds = CopyOnWriteArrayList<String>()
  private val finishedTaskIds = CopyOnWriteArrayList<String>()
  private val terminatedTaskIds = CopyOnWriteArrayList<String>()

  @BeforeEach
  fun setUp() {
    subscribe(slowTopic) { taskId ->
      startedTaskIds.add(taskId)
      slowTasksMayFinish.await()
      finishedTaskIds.add(taskId)
    }
    subscribe(fastTopic) { taskId ->
      startedTaskIds.add(taskId)
      finishedTaskIds.add(taskId)
    }

    whenever(externalTaskService.fetchAndLock(any(), any())).thenReturn(fetchAndLockBuilder)
    whenever(fetchAndLockBuilder.topic(any(), any())).thenReturn(mock<ExternalTaskQueryTopicBuilder>(defaultAnswer = RETURNS_SELF))
    whenever(externalTaskService.createExternalTaskQuery()).thenReturn(existingTasksQuery)
  }

  @AfterEach
  fun tearDown() {
    slowTasksMayFinish.countDown()
    executor.shutdownNow()
    executor.awaitTermination(10, SECONDS)
  }

  @Test
  fun `slow task does not block delivery of a fast task`() {
    doReturn(listOf(lockedTask("slow", slowTopic)), listOf(lockedTask("fast", fastTopic)))
      .whenever(fetchAndLockBuilder).execute()

    taskDelivery.refresh()
    await untilAsserted { assertThat(startedTaskIds).containsExactly("slow") }

    taskDelivery.refresh()
    await untilAsserted { assertThat(finishedTaskIds).containsExactly("fast") }
  }

  @Test
  fun `fetches only as many tasks as worker threads are free`() {
    doReturn(listOf(lockedTask("slow-1", slowTopic)), listOf(lockedTask("slow-2", slowTopic)))
      .whenever(fetchAndLockBuilder).execute()

    taskDelivery.refresh()
    taskDelivery.refresh()
    taskDelivery.refresh()

    inOrder(externalTaskService) {
      verify(externalTaskService).fetchAndLock(2, workerId)
      verify(externalTaskService).fetchAndLock(1, workerId)
    }
    verify(externalTaskService, times(2)).fetchAndLock(any(), any())
  }

  @Test
  fun `fetches again after a running task has finished`() {
    doReturn(listOf(lockedTask("fast", fastTopic)), emptyList<LockedExternalTask>())
      .whenever(fetchAndLockBuilder).execute()

    taskDelivery.refresh()
    await untilAsserted {
      taskDelivery.refresh()
      verify(externalTaskService, atLeast(2)).fetchAndLock(2, workerId)
    }
  }

  @Test
  fun `running task offered again is not started a second time`() {
    doReturn(listOf(lockedTask("slow", slowTopic)))
      .whenever(fetchAndLockBuilder).execute()

    taskDelivery.refresh()
    taskDelivery.refresh()

    assertThat(executor.taskCount).isEqualTo(1)
  }

  @Test
  fun `running task is not terminated although it is not fetched again`() {
    doReturn(listOf(lockedTask("slow", slowTopic)), emptyList<LockedExternalTask>())
      .whenever(fetchAndLockBuilder).execute()
    doReturn(emptyList<ExternalTask>()).whenever(existingTasksQuery).list()

    taskDelivery.refresh()
    await untilAsserted { assertThat(startedTaskIds).containsExactly("slow") }
    taskDelivery.refresh()

    assertThat(terminatedTaskIds).isEmpty()
  }

  @Test
  fun `delivered task that still exists in the engine is not terminated`() {
    doReturn(listOf(lockedTask("fast", fastTopic)), emptyList<LockedExternalTask>())
      .whenever(fetchAndLockBuilder).execute()
    val stillExistingTask = mock<ExternalTask> { on { id } doReturn "fast" }
    doReturn(listOf(stillExistingTask)).whenever(existingTasksQuery).list()

    taskDelivery.refresh()
    await untilAsserted { assertThat(finishedTaskIds).containsExactly("fast") }
    taskDelivery.refresh()

    assertThat(terminatedTaskIds).isEmpty()
  }

  @Test
  fun `delivered task that is gone from the engine is terminated`() {
    doReturn(listOf(lockedTask("fast", fastTopic)), emptyList<LockedExternalTask>())
      .whenever(fetchAndLockBuilder).execute()
    doReturn(emptyList<ExternalTask>()).whenever(existingTasksQuery).list()

    taskDelivery.refresh()
    await untilAsserted { assertThat(finishedTaskIds).containsExactly("fast") }
    taskDelivery.refresh()

    await untilAsserted { assertThat(terminatedTaskIds).containsExactly("fast") }
  }

  private fun subscribe(topic: String, action: (String) -> Unit) {
    subscriptionRepository.createTaskSubscription(
      TaskSubscriptionHandle(
        taskType = TaskType.EXTERNAL,
        payloadDescription = null,
        restrictions = mapOf(),
        taskDescriptionKey = topic,
        action = { taskInformation, _ -> action(taskInformation.taskId) },
        termination = { taskInformation -> terminatedTaskIds.add(taskInformation.taskId) }
      )
    )
  }

  private fun lockedTask(id: String, topic: String): LockedExternalTask = mock {
    on { this.id } doReturn id
    on { topicName } doReturn topic
    on { variables } doReturn Variables.createVariables()
  }
}
