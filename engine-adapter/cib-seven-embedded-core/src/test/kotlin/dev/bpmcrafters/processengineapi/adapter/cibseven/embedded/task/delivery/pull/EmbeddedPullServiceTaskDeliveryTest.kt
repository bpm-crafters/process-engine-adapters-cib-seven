package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.engine.ExternalTaskService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.Executors

/**
 * Unit test for EmbeddedPullServiceTaskDelivery focusing on lock duration logic.
 */
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
    // GIVEN
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = mapOf("workerLockDurationInMilliseconds" to CUSTOM_LOCK_DURATION_MS.toString()),
      taskDescriptionKey = "custom-topic",
      action = { _, _ -> },
      termination = {}
    )

    // WHEN
    val result = delivery.getLockDurationForSubscription(subscription)

    // THEN
    assertThat(result).isEqualTo(CUSTOM_LOCK_DURATION_MS)
  }

  @Test
  fun `should use default lock duration when restriction not provided`() {
    // GIVEN
    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = emptyMap(),
      taskDescriptionKey = "default-topic",
      action = { _, _ -> },
      termination = {}
    )

    // WHEN
    val result = delivery.getLockDurationForSubscription(subscription)

    // THEN
    val expectedDefault = DEFAULT_LOCK_DURATION_SECONDS * 1000
    assertThat(result).isEqualTo(expectedDefault)
  }
}
