package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion

import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import org.cibseven.bpm.engine.TaskService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class Cib7UserTaskCompletionApiImplTest {

  private val taskService = mock<TaskService>()
  private val completionApi = Cib7UserTaskCompletionApiImpl(
    taskService = taskService,
    subscriptionRepository = mock<SubscriptionRepository>()
  )

  @Test
  fun `completeTaskByError passes error code, message and payload to the engine`() {
    completionApi.completeTaskByError(
      CompleteTaskByErrorCmd(
        taskId = "task",
        errorCode = "REJECTED",
        errorMessage = "Document incomplete",
        payloadSupplier = { mapOf("rejectionReason" to "missing signature") }
      )
    ).join()

    verify(taskService).handleBpmnError(
      "task",
      "REJECTED",
      "Document incomplete",
      mapOf("rejectionReason" to "missing signature")
    )
  }
}
