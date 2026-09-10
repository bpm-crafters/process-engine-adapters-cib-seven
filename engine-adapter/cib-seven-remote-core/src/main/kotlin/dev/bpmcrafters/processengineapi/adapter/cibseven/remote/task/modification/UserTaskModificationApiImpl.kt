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
    if (cmd is CompositeModifyTaskCmd) {
      cmd.commands.forEach {
        handleCommand(it)
      }
    } else {
      handleCommand(cmd)
    }
    return CompletableFuture.completedFuture(Empty)
  }

  private fun handleCommand(cmd: ModifyTaskCmd) {
    logger.trace { "PROCESS-ENGINE-C7-EMBEDDED-052: handling command ${cmd}." }
    when (cmd) {
      is ChangeAssignmentModifyTaskCmd -> changeAssignment(cmd)
      is ChangePayloadModifyTaskCmd -> changePayload(cmd)
      is ChangeDatesModifyTaskCmd -> changeDates(cmd)
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
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
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
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
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
  }

  private fun changeDates(cmd: ChangeDatesModifyTaskCmd) {
    when (cmd) {
      is SetDueDateTaskCmd -> taskApi.updateTask(cmd.taskId, TaskDto().due(cmd.dueDate))
      is ClearDueDateTaskCmd -> taskApi.updateTask(cmd.taskId, TaskDto().due(null))
      is SetFollowUpDateTaskCmd -> taskApi.updateTask(cmd.taskId, TaskDto().followUp(cmd.followUpDate))
      is ClearFollowUpDateTaskCmd -> taskApi.updateTask(cmd.taskId, TaskDto().followUp(null))
      else -> throw UnsupportedOperationException("Unsupported command ${cmd.javaClass.canonicalName}.")
    }
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

      else -> throw UnsupportedOperationException("Unsupported command ${javaClass.canonicalName}.")
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

      else -> throw UnsupportedOperationException("Unsupported command ${javaClass.canonicalName}.")
    }
  }

}
