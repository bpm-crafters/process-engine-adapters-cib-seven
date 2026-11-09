package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.modification

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.*
import dev.bpmcrafters.processengineapi.task.ChangeAssignmentModifyTaskCmd.*
import dev.bpmcrafters.processengineapi.task.ChangeDatesModifyTaskCmd.*
import dev.bpmcrafters.processengineapi.task.ChangePayloadModifyTaskCmd.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.api.TaskLocalVariableApi
import org.cibseven.community.rest.client.dto.IdentityLinkDto
import org.cibseven.community.rest.client.dto.PatchVariablesDto
import org.cibseven.community.rest.client.dto.TaskDto
import org.cibseven.community.rest.client.dto.UserIdDto
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class UserTaskModificationApiImpl(
  private val taskApi: TaskApi,
  private val taskIdentityLinkApi: TaskIdentityLinkApi,
  private val taskLocalVariableApi: TaskLocalVariableApi,
  private val valueMapper: ValueMapper,
) : UserTaskModificationApi {
  override fun update(cmd: ModifyTaskCmd): CompletableFuture<Empty> {
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-051: modifying user task ${cmd.taskId}." }
    return if (cmd is CompositeModifyTaskCmd) {
      cmd.commands.fold(CompletableFuture.completedFuture(Empty)) { future, command ->
        future.thenCompose { handleCommand(command) }
      }
    } else {
      handleCommand(cmd)
    }
  }

  private fun handleCommand(cmd: ModifyTaskCmd): CompletableFuture<Empty> = CompletableFuture.supplyAsync {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-052: handling command ${cmd}." }
    when (cmd) {
      is ChangeAssignmentModifyTaskCmd -> changeAssignment(cmd)
      is ChangePayloadModifyTaskCmd -> changePayload(cmd)
      is ChangeDatesModifyTaskCmd -> changeDates(cmd)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName ?: cmd.javaClass.name}.")
    }
    Empty
  }

  private fun changeAssignment(cmd: ChangeAssignmentModifyTaskCmd) {
    when (cmd) {
      is AssignTaskCmd -> taskApi.setAssignee(cmd.taskId, UserIdDto().userId(cmd.assignee))
      is UnassignTaskCmd -> taskApi.setAssignee(cmd.taskId, UserIdDto())
      is ClearCandidateUsersTaskCmd -> taskIdentityLinkApi.removeAllCandidateUsers(cmd.taskId)
      is ClearCandidateGroupsTaskCmd -> taskIdentityLinkApi.removeAllCandidateGroups(cmd.taskId)
      is SetCandidateUsersTaskCmd -> taskIdentityLinkApi.setCandidateUsers(cmd.taskId, cmd.toIdentityLinkDtoList())
      is SetCandidateGroupsTaskCmd -> taskIdentityLinkApi.setCandidateGroups(cmd.taskId, cmd.toIdentityLinkDtoList())
      is AddCandidateUserTaskCmd, is AddCandidateGroupTaskCmd -> taskIdentityLinkApi.addIdentityLink(cmd.taskId, cmd.toIdentityLinkDto())
      is RemoveCandidateUserTaskCmd, is RemoveCandidateGroupTaskCmd -> taskIdentityLinkApi.deleteIdentityLink(cmd.taskId, cmd.toIdentityLinkDto())
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName ?: cmd.javaClass.name}.")
    }
  }

  private fun changePayload(cmd: ChangePayloadModifyTaskCmd) {
    when (cmd) {
      is UpdatePayloadTaskCmd -> taskLocalVariableApi.modifyTaskLocalVariables(
        cmd.taskId,
        PatchVariablesDto().modifications(valueMapper.mapValues(cmd.get()))
      )

      is DeletePayloadTaskCmd -> taskLocalVariableApi.removeVariablesLocal(cmd.taskId, cmd.get())
      is ClearPayloadTaskCmd -> taskLocalVariableApi.clearTaskVariablesLocal(cmd.taskId)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName ?: cmd.javaClass.name}.")
    }
  }

  private fun changeDates(cmd: ChangeDatesModifyTaskCmd) {
    // Preserve task fields; without REST-level versioning, this read-modify-write may overwrite concurrent changes.
    val currentTask = taskApi.getTask(cmd.taskId)
      ?: throw IllegalArgumentException("Task with id ${cmd.taskId} not found.")
    val taskUpdate = TaskDto()
      .id(currentTask.id)
      .name(currentTask.name)
      .assignee(currentTask.assignee)
      .owner(currentTask.owner)
      .created(currentTask.created)
      .lastUpdated(currentTask.lastUpdated)
      .due(currentTask.due)
      .followUp(currentTask.followUp)
      .delegationState(currentTask.delegationState?.let { TaskDto.DelegationStateEnum.fromValue(it.value) })
      .description(currentTask.description)
      .executionId(currentTask.executionId)
      .parentTaskId(currentTask.parentTaskId)
      .priority(currentTask.priority)
      .processDefinitionId(currentTask.processDefinitionId)
      .processInstanceId(currentTask.processInstanceId)
      .caseExecutionId(currentTask.caseExecutionId)
      .caseDefinitionId(currentTask.caseDefinitionId)
      .caseInstanceId(currentTask.caseInstanceId)
      .taskDefinitionKey(currentTask.taskDefinitionKey)
      .suspended(currentTask.suspended)
      .formKey(currentTask.formKey)
      .camundaFormRef(currentTask.camundaFormRef)
      .tenantId(currentTask.tenantId)
      .taskState(currentTask.taskState)

    when (cmd) {
      is SetDueDateTaskCmd -> taskUpdate.due(cmd.dueDate)
      is ClearDueDateTaskCmd -> taskUpdate.due(null)
      is SetFollowUpDateTaskCmd -> taskUpdate.followUp(cmd.followUpDate)
      is ClearFollowUpDateTaskCmd -> taskUpdate.followUp(null)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName ?: cmd.javaClass.name}.")
    }
    taskApi.updateTask(cmd.taskId, taskUpdate)
  }

  private fun ChangeAssignmentModifyTaskCmd.toIdentityLinkDto(): IdentityLinkDto {
    return when (this) {
      is AddCandidateUserTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        userId = this@toIdentityLinkDto.candidateUser
      }

      is RemoveCandidateUserTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        userId = this@toIdentityLinkDto.candidateUser
      }

      is AddCandidateGroupTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        groupId = this@toIdentityLinkDto.candidateGroup
      }

      is RemoveCandidateGroupTaskCmd -> IdentityLinkDto().apply {
        type = "candidate"
        groupId = this@toIdentityLinkDto.candidateGroup
      }

      else -> throw UnsupportedOperationException("Unsupported command ${javaClass.canonicalName ?: javaClass.name}.")
    }
  }

  private fun ChangeAssignmentModifyTaskCmd.toIdentityLinkDtoList(): List<IdentityLinkDto> {
    return when (this) {
      is SetCandidateUsersTaskCmd ->
        this.candidateUsers.map { candidateUser ->
          IdentityLinkDto().apply {
            type = "candidate"
            userId = candidateUser
          }
        }

      is SetCandidateGroupsTaskCmd -> this.candidateGroups.map { candidateGroup ->
        IdentityLinkDto().apply {
          type = "candidate"
          groupId = candidateGroup
        }
      }

      else -> throw UnsupportedOperationException("Unsupported command ${javaClass.canonicalName ?: javaClass.name}.")
    }
  }

}
