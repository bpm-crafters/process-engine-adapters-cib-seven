package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion

import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.TaskOutcomeRecorder
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.AbstractCibSevenEmbeddedApiITest
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.CibSevenServiceTaskCompletionApiImplITest.Companion.BPMN
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.engine.impl.util.ClockUtil
import org.cibseven.bpm.engine.test.Deployment
import org.cibseven.bpm.engine.test.junit5.ProcessEngineExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.Date

@Deployment(resources = [BPMN])
class CibSevenServiceTaskCompletionApiImplITest {

  companion object {
    const val BPMN = "bpmn/single-external-task.bpmn"
    const val PROCESS_KEY = "single-external-task"
    const val TOPIC = "single-external-task-topic"
    const val WORKER_ID = "completion-itest-worker"
    const val DEFAULT_RETRY_TIMEOUT_IN_SECONDS = 10L

    @RegisterExtension
    val engine: ProcessEngineExtension = ProcessEngineExtension.builder().useProcessEngine(AbstractCibSevenEmbeddedApiITest.processEngine).build()
  }

  private val processEngine = AbstractCibSevenEmbeddedApiITest.processEngine
  private val externalTaskService = processEngine.externalTaskService
  private val now = Date(1_700_000_000_000)
  private val completionApi = CibSevenServiceTaskCompletionApiImpl(
    WORKER_ID,
    externalTaskService,
    InMemSubscriptionRepository(),
    LinearMemoryFailureRetrySupplier(3, DEFAULT_RETRY_TIMEOUT_IN_SECONDS),
    TaskOutcomeRecorder(),
  )

  @BeforeEach
  fun freezeClock() {
    ClockUtil.setCurrentTime(now)
  }

  @AfterEach
  fun resetClock() {
    ClockUtil.reset()
  }

  @Test
  fun `fail task passes the retry backoff in milliseconds`() {
    val taskId = startProcessAndLockTask()

    completionApi.failTask(FailTaskCmd(taskId, "reason", null, 2, Duration.ofSeconds(30))).get()

    assertThat(lockExpirationTimeInMillis()).isEqualTo(now.time + 30_000)
  }

  @Test
  fun `fail task passes the default retry timeout in milliseconds`() {
    val taskId = startProcessAndLockTask()

    completionApi.failTask(FailTaskCmd(taskId, "reason", null)).get()

    assertThat(lockExpirationTimeInMillis()).isEqualTo(now.time + DEFAULT_RETRY_TIMEOUT_IN_SECONDS * 1000)
  }

  private fun startProcessAndLockTask(): String {
    processEngine.runtimeService.startProcessInstanceByKey(PROCESS_KEY)
    return externalTaskService.fetchAndLock(1, WORKER_ID).topic(TOPIC, 60_000).execute().single().id
  }

  private fun lockExpirationTimeInMillis(): Long = externalTaskService.createExternalTaskQuery().singleResult().lockExpirationTime.time
}
