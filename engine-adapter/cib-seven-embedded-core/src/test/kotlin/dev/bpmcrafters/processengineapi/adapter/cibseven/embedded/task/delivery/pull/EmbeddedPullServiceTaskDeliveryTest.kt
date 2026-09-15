package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.threading.ThreadContextClassLoaderThreadFactory
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.task.TaskType
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.engine.ExternalTaskService
import org.cibseven.bpm.engine.externaltask.ExternalTaskQueryBuilder
import org.cibseven.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder
import org.cibseven.bpm.engine.externaltask.LockedExternalTask
import org.cibseven.bpm.engine.variable.Variables
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

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
      restrictions = mapOf(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS to CUSTOM_LOCK_DURATION_MS.toString()),
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
        CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS to "25000"
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
        CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS to "25000"
      ),
      taskDescriptionKey = "test-topic",
      action = { _, _ -> },
      termination = {}
    )

    assertThat(subscription.matches(lockedTask)).isFalse()
  }

  /**
   * Reproduces the class loader bug: the worker thread that executes the handler runs with an isolated
   * context class loader that cannot see application classes (e.g. a Spring-Boot fat-jar nested class loader).
   * The delivery must run the handler with the handler's own class loader as the thread context class loader,
   * so that application classes (and JAAS login modules such as Kafka's OAuthBearerLoginModule) stay loadable.
   */
  @Test
  fun `handler runs with its own class loader despite isolated worker class loader`() {
    val applicationClassName = javaClass.name
    val observedContextClassLoader = AtomicReference<ClassLoader>()
    var handlerLoadedApplicationClass = false

    val subscription = TaskSubscriptionHandle(
      taskType = TaskType.EXTERNAL,
      payloadDescription = null,
      restrictions = emptyMap(),
      taskDescriptionKey = "test-topic",
      action = { _, _ ->
        val contextClassLoader = Thread.currentThread().contextClassLoader
        observedContextClassLoader.set(contextClassLoader)
        contextClassLoader.loadClass(applicationClassName)
        handlerLoadedApplicationClass = true
      },
      termination = {}
    )
    subscriptionRepository.createTaskSubscription(subscription)

    val lockedTask: LockedExternalTask = mock {
      on { id }.thenReturn("1")
      on { topicName }.thenReturn("test-topic")
      on { variables }.thenReturn(Variables.createVariables())
      on { retries }.thenReturn(3)
    }
    val topicBuilder: ExternalTaskQueryTopicBuilder = mock {
      on { enableCustomObjectDeserialization() }.thenReturn(mock)
    }
    val queryBuilder: ExternalTaskQueryBuilder = mock {
      on { topic(any(), any()) }.thenReturn(topicBuilder)
      on { execute() }.thenReturn(listOf(lockedTask))
    }
    whenever(externalTaskService.fetchAndLock(any(), any())).thenReturn(queryBuilder)

    // An isolated class loader (no parent) that cannot see application classes, emulating a fat-jar worker thread.
    val workerContextClassLoader = object : ClassLoader(null) {}
    val deliveryWithIsolatedWorker = EmbeddedPullServiceTaskDelivery(
      externalTaskService = externalTaskService,
      workerId = "test-worker",
      subscriptionRepository = subscriptionRepository,
      maxTasks = 100,
      lockDurationInSeconds = DEFAULT_LOCK_DURATION_SECONDS,
      retryTimeoutInSeconds = 10L,
      retries = 3,
      executorService = Executors.newSingleThreadExecutor(ThreadContextClassLoaderThreadFactory(workerContextClassLoader))
    )

    deliveryWithIsolatedWorker.refresh()

    // The handler could load the application class, i.e. the delivery switched to the handler's class loader.
    assertThat(handlerLoadedApplicationClass).isTrue()
    assertThat(observedContextClassLoader.get()).isSameAs(javaClass.classLoader)
  }
}
