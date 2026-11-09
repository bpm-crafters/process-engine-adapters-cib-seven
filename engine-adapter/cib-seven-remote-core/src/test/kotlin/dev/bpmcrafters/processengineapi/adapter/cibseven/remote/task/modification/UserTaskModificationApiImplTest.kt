package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.modification

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import dev.bpmcrafters.processengineapi.task.ChangeAssignmentModifyTaskCmd
import dev.bpmcrafters.processengineapi.task.ChangeDatesModifyTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskModification
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.api.TaskLocalVariableApi
import org.cibseven.community.rest.client.dto.CamundaFormRef
import org.cibseven.community.rest.client.dto.TaskDto
import org.cibseven.community.rest.client.dto.TaskWithAttachmentAndCommentDto
import org.cibseven.community.rest.client.dto.UserIdDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.concurrent.ExecutionException

internal class UserTaskModificationApiImplTest {

  private val taskId = "task-id"
  private val taskApi = mock<TaskApi>()

  private val api = UserTaskModificationApiImpl(
    taskApi = taskApi,
    taskIdentityLinkApi = mock<TaskIdentityLinkApi>(),
    taskLocalVariableApi = mock<TaskLocalVariableApi>(),
    valueMapper = mock<ValueMapper>(),
  )

  @Test
  fun `set due date`() {
    val dueDate = OffsetDateTime.now().plusDays(7)
    val update = updateDate(ChangeDatesModifyTaskCmd.SetDueDateTaskCmd(taskId = taskId, dueDate = dueDate))

    assertThat(update.due).isEqualTo(dueDate)
  }

  @Test
  fun `clear due date`() {
    val update = updateDate(ChangeDatesModifyTaskCmd.ClearDueDateTaskCmd(taskId = taskId))

    assertThat(update.due).isNull()
  }

  @Test
  fun `set follow-up date`() {
    val followUpDate = OffsetDateTime.now().plusDays(3)
    val update = updateDate(ChangeDatesModifyTaskCmd.SetFollowUpDateTaskCmd(taskId = taskId, followUpDate = followUpDate))

    assertThat(update.followUp).isEqualTo(followUpDate)
  }

  @Test
  fun `clear follow-up date`() {
    val update = updateDate(ChangeDatesModifyTaskCmd.ClearFollowUpDateTaskCmd(taskId = taskId))

    assertThat(update.followUp).isNull()
  }

  @Test
  fun `fail date update when task is not found`() {
    whenever(taskApi.getTask(taskId)).thenReturn(null)

    val exception = assertThrows<ExecutionException> {
      api.update(ChangeDatesModifyTaskCmd.ClearFollowUpDateTaskCmd(taskId = taskId)).get()
    }

    assertThat(exception.cause)
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("Task with id $taskId not found.")
    verify(taskApi).getTask(taskId)
    verifyNoMoreInteractions(taskApi)
  }

  @Test
  fun `return an exceptional future when a remote modification fails`() {
    doThrow(IllegalStateException("remote failure")).whenever(taskApi).setAssignee(eq(taskId), any())

    val future = api.update(ChangeAssignmentModifyTaskCmd.AssignTaskCmd(taskId, "kermit"))

    val exception = assertThrows<ExecutionException> { future.get() }
    assertThat(exception.cause)
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("remote failure")
  }

  @Test
  fun `stop a composite modification after a failed command`() {
    doThrow(IllegalStateException("remote failure")).whenever(taskApi).setAssignee(taskId, UserIdDto().userId("kermit"))

    val future = api.update(
      TaskModification.taskModification(taskId) {
        assign("kermit")
        unassign()
      }
    )

    val exception = assertThrows<ExecutionException> { future.get() }
    assertThat(exception.cause)
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("remote failure")
    verify(taskApi).setAssignee(taskId, UserIdDto().userId("kermit"))
    verify(taskApi, never()).setAssignee(taskId, UserIdDto())
  }

  @Test
  fun `return an exceptional future for an unsupported command`() {
    val unsupportedCommand = object : ChangeAssignmentModifyTaskCmd(taskId) {}

    val exception = assertThrows<ExecutionException> { api.update(unsupportedCommand).get() }

    assertThat(exception.cause)
      .isInstanceOf(UnsupportedOperationException::class.java)
      .hasMessageMatching("Unsupported command .+\\.")
  }

  private fun updateDate(command: ChangeDatesModifyTaskCmd): TaskDto {
    val currentTask = taskWithAllFields()
    whenever(taskApi.getTask(taskId)).thenReturn(currentTask)

    api.update(command).get()

    val taskUpdateCaptor = argumentCaptor<TaskDto>()
    verify(taskApi).getTask(taskId)
    verify(taskApi).updateTask(eq(taskId), taskUpdateCaptor.capture())
    assertTaskFieldsAreCopied(currentTask, taskUpdateCaptor.firstValue)
    verifyNoMoreInteractions(taskApi)
    return taskUpdateCaptor.firstValue
  }

  private fun taskWithAllFields() = TaskWithAttachmentAndCommentDto()
    .id("task-id")
    .name("task name")
    .assignee("kermit")
    .owner("gonzo")
    .created(OffsetDateTime.parse("2026-01-02T03:04:05Z"))
    .lastUpdated(OffsetDateTime.parse("2026-01-03T03:04:05Z"))
    .due(OffsetDateTime.parse("2026-01-04T03:04:05Z"))
    .followUp(OffsetDateTime.parse("2026-01-05T03:04:05Z"))
    .delegationState(TaskWithAttachmentAndCommentDto.DelegationStateEnum.PENDING)
    .description("task description")
    .executionId("execution-id")
    .parentTaskId("parent-task-id")
    .priority(73)
    .processDefinitionId("process-definition-id")
    .processInstanceId("process-instance-id")
    .caseExecutionId("case-execution-id")
    .caseDefinitionId("case-definition-id")
    .caseInstanceId("case-instance-id")
    .taskDefinitionKey("task-definition-key")
    .suspended(false)
    .formKey("form-key")
    .camundaFormRef(CamundaFormRef().key("form-ref-key").binding("version").version(2))
    .tenantId("tenant-id")
    .taskState("task-state")

  private fun assertTaskFieldsAreCopied(currentTask: TaskWithAttachmentAndCommentDto, taskUpdate: TaskDto) {
    assertThat(taskUpdate.id).isEqualTo(currentTask.id)
    assertThat(taskUpdate.name).isEqualTo(currentTask.name)
    assertThat(taskUpdate.assignee).isEqualTo(currentTask.assignee)
    assertThat(taskUpdate.owner).isEqualTo(currentTask.owner)
    assertThat(taskUpdate.created).isEqualTo(currentTask.created)
    assertThat(taskUpdate.lastUpdated).isEqualTo(currentTask.lastUpdated)
    assertThat(taskUpdate.delegationState?.value).isEqualTo(currentTask.delegationState?.value)
    assertThat(taskUpdate.description).isEqualTo(currentTask.description)
    assertThat(taskUpdate.executionId).isEqualTo(currentTask.executionId)
    assertThat(taskUpdate.parentTaskId).isEqualTo(currentTask.parentTaskId)
    assertThat(taskUpdate.priority).isEqualTo(currentTask.priority)
    assertThat(taskUpdate.processDefinitionId).isEqualTo(currentTask.processDefinitionId)
    assertThat(taskUpdate.processInstanceId).isEqualTo(currentTask.processInstanceId)
    assertThat(taskUpdate.caseExecutionId).isEqualTo(currentTask.caseExecutionId)
    assertThat(taskUpdate.caseDefinitionId).isEqualTo(currentTask.caseDefinitionId)
    assertThat(taskUpdate.caseInstanceId).isEqualTo(currentTask.caseInstanceId)
    assertThat(taskUpdate.taskDefinitionKey).isEqualTo(currentTask.taskDefinitionKey)
    assertThat(taskUpdate.suspended).isEqualTo(currentTask.suspended)
    assertThat(taskUpdate.formKey).isEqualTo(currentTask.formKey)
    assertThat(taskUpdate.camundaFormRef).isEqualTo(currentTask.camundaFormRef)
    assertThat(taskUpdate.tenantId).isEqualTo(currentTask.tenantId)
    assertThat(taskUpdate.taskState).isEqualTo(currentTask.taskState)
  }

}
