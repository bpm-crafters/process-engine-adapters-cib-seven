package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.modification

import org.cibseven.community.rest.client.api.TaskIdentityLinkApiClient
import org.cibseven.community.rest.client.api.TaskLocalVariableApiClient
import org.cibseven.community.rest.client.model.IdentityLinkDto
import org.cibseven.community.rest.client.model.PatchVariablesDto
import org.cibseven.community.rest.client.model.VariableValueDto

/*
 * In CIB seven's REST API the task identity-link and (local) variable operations live on their own
 * OpenAPI tags, so the generated client splits them into dedicated *ApiClient types. These helpers
 * therefore extend the specific client that owns each operation.
 */

private fun TaskLocalVariableApiClient.getLocalTaskVariables(taskId: String): Map<String, VariableValueDto> =
  this.getTaskLocalVariables(taskId, false)

private fun TaskIdentityLinkApiClient.getAllTaskCandidates(taskId: String): List<IdentityLinkDto> =
  this.getIdentityLinks(taskId, "candidate")

private fun TaskIdentityLinkApiClient.getCandidateGroups(taskId: String) =
  getAllTaskCandidates(taskId).filter { it.groupId != null }

private fun TaskIdentityLinkApiClient.getCandidateUsers(taskId: String) =
  getAllTaskCandidates(taskId).filter { it.userId != null }

private fun TaskIdentityLinkApiClient.synchronizeIdentityLinks(
  taskId: String,
  toRemove: List<IdentityLinkDto> = listOf(),
  toAdd: List<IdentityLinkDto> = listOf()
) {
  // The generated client throws ApiException on a non-2xx response, so a successful
  // return already implies the identity link was removed / added.
  toRemove.forEach { this.deleteIdentityLink(taskId, it) }
  toAdd.forEach { this.addIdentityLink(taskId, it) }
}

fun TaskIdentityLinkApiClient.removeAllCandidateUsers(taskId: String) {
  setCandidateUsers(taskId, listOf())
}

fun TaskIdentityLinkApiClient.removeAllCandidateGroups(taskId: String) {
  setCandidateGroups(taskId, listOf())
}

fun TaskIdentityLinkApiClient.setCandidateUsers(taskId: String, candidateUsers: List<IdentityLinkDto>) {
  val all = getCandidateUsers(taskId)
  val toRemove = all.filter { it !in candidateUsers }
  val toAdd = candidateUsers.filter { it !in all }
  synchronizeIdentityLinks(taskId = taskId, toRemove = toRemove, toAdd = toAdd)
}

fun TaskIdentityLinkApiClient.setCandidateGroups(taskId: String, candidateGroups: List<IdentityLinkDto>) {
  val all = getCandidateGroups(taskId)
  val toRemove = all.filter { it !in candidateGroups }
  val toAdd = candidateGroups.filter { it !in all }
  synchronizeIdentityLinks(taskId = taskId, toRemove = toRemove, toAdd = toAdd)
}

fun TaskLocalVariableApiClient.clearTaskVariablesLocal(taskId: String) {
  val all = getLocalTaskVariables(taskId)
  this.modifyTaskLocalVariables(
    taskId,
    PatchVariablesDto().deletions(all.keys.toList())
  )
}

fun TaskLocalVariableApiClient.removeVariablesLocal(taskId: String, variables: List<String>) {
  this.modifyTaskLocalVariables(
    taskId,
    PatchVariablesDto().deletions(variables)
  )
}
