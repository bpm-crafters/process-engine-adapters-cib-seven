package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion

import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import org.cibseven.bpm.engine.ExternalTaskService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration

internal class CibSevenServiceTaskCompletionApiImplTest {

  private val externalTaskService = mock<ExternalTaskService>()
  private val completionApi = CibSevenServiceTaskCompletionApiImpl(
    workerId = "worker",
    externalTaskService = externalTaskService,
    subscriptionRepository = mock<SubscriptionRepository>(),
    failureRetrySupplier = FixedFailureRetrySupplier
  )

  @Test
  fun `failTask passes provided retryBackoff in milliseconds to cib seven engine`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", 2, Duration.ofSeconds(3))
    ).join()

    verify(externalTaskService).handleFailure("task", "worker", "reason", "details", 2, 3_000)
  }

  @Test
  fun `failTask converts supplier retry timeout seconds to milliseconds`() {
    completionApi.failTask(
      FailTaskCmd("task", "reason", "details", null, null)
    ).join()

    verify(externalTaskService).handleFailure("task", "worker", "reason", "details", 4, 7_000)
  }

  private object FixedFailureRetrySupplier : FailureRetrySupplier {
    override fun apply(taskId: String) = FailureRetrySupplier.FailureRetry(
      retryCount = 4,
      retryTimeout = 7
    )
  }
}
