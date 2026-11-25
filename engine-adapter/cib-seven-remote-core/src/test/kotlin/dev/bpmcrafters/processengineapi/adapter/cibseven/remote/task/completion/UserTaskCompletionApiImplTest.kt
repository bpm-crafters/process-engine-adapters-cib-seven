package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.community.rest.client.api.TaskApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException

internal class UserTaskCompletionApiImplTest {

  private val taskId = "task-id"
  private val taskApi = mock<TaskApi>()
  private val subscriptionRepository = mock<SubscriptionRepository>()

  private val api = UserTaskCompletionApiImpl(
    taskApi = taskApi,
    subscriptionRepository = subscriptionRepository,
    valueMapper = mock<ValueMapper>(),
  )

  @Test
  fun `return an exceptional future and keep subscription when remote completion fails`() {
    doThrow(IllegalStateException("remote failure")).whenever(taskApi).complete(eq(taskId), any())

    val future = api.completeTask(CompleteTaskCmd(taskId) { emptyMap() })

    val exception = assertThrows<ExecutionException> { future.get() }
    assertThat(exception.cause)
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("remote failure")
    verifyNoInteractions(subscriptionRepository)
  }
}
