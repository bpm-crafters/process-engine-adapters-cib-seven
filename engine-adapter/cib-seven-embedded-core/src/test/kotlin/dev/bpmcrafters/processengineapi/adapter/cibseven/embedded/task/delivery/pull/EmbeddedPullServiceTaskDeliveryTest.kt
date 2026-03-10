package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.externaltask.LockedExternalTask
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.Executors

class EmbeddedPullServiceTaskDeliveryTest {

  companion object {
    const val DEFAULT_LOCK_DURATION_SECONDS = 10L
    const val CUSTOM_LOCK_DURATION_MS = 25000L
  }

  private val externalTaskService: ExternalTaskService = mock()
  private val subscriptionRepository = InMemSubscriptionRepository()
  private val delivery = EmbeddedPullServiceTaskDelivery(
    externalTaskService = externalTaskService,
    workerId = "test-worker",
    subscriptionRepository = subscriptionRepository,
    maxTasks = 100,
    lockDurationInSeconds = DEFAULT_LOCK_DURATION_SECONDS,
    retryTimeoutInSeconds = 10L,
    retries = 3,
    executorService = Executors.newSingleThreadExecutor()
  )

  @Test
  fun `should use custom lock duration when provided in restrictions`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = mapOf("workerLockDurationInMilliseconds" to CUSTOM_LOCK_DURATION_MS.toString()),
      taskDescriptionKey = "custom-topic",
      action = { _, _ -> },
      termination = {}
    )

    assertThat(delivery.getLockDurationForSubscription(subscription)).isEqualTo(CUSTOM_LOCK_DURATION_MS)
  }

  @Test
  fun `should use default lock duration when restriction not provided`() {
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = emptyMap(),
      taskDescriptionKey = "default-topic",
      action = { _, _ -> },
      termination = {}
    )

    assertThat(delivery.getLockDurationForSubscription(subscription)).isEqualTo(DEFAULT_LOCK_DURATION_SECONDS * 1000)
  }

  @Test
  fun `should ignore workerLockDurationInMilliseconds restriction when matching tasks`() {
    val lockedTask: LockedExternalTask = mock {
      on { topicName }.thenReturn("test-topic")
      on { processDefinitionId }.thenReturn("pd-1")
    }
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = mapOf(
        CommonRestrictions.PROCESS_DEFINITION_ID to "pd-1",
        "workerLockDurationInMilliseconds" to "25000"
      ),
      taskDescriptionKey = "test-topic",
      action = { _, _ -> },
      termination = {}
    )

    assertThat(subscription.matches(lockedTask)).isTrue()
  }

  @Test
  fun `should fail to match when non-ignored restriction does not match`() {
    val lockedTask: LockedExternalTask = mock {
      on { topicName }.thenReturn("test-topic")
      on { processDefinitionId }.thenReturn("pd-2")
    }
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = mapOf(
        CommonRestrictions.PROCESS_DEFINITION_ID to "pd-1",
        "workerLockDurationInMilliseconds" to "25000"
      ),
      taskDescriptionKey = "test-topic",
      action = { _, _ -> },
      termination = {}
    )

    assertThat(subscription.matches(lockedTask)).isFalse()
  }
}
