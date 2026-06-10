package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.cibseven.bpm.engine.delegate.DelegateTask
import org.cibseven.bpm.engine.externaltask.LockedExternalTask
import org.cibseven.bpm.engine.impl.persistence.entity.ExternalTaskEntity
import org.cibseven.bpm.engine.impl.persistence.entity.TaskEntity
import org.cibseven.bpm.engine.task.IdentityLink
import org.cibseven.bpm.engine.task.Task
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

fun Task.toTaskInformation(candidates: Set<IdentityLink>, processDefinitionKey: String? = null) =
  TaskInformation(
    taskId = this.id,
    meta = metaOf(
      CommonRestrictions.PROCESS_DEFINITION_KEY to processDefinitionKey,
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.ACTIVITY_ID to this.taskDefinitionKey,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      "taskName" to this.name,
      "taskDescription" to this.description,
      "assignee" to this.assignee,
      "creationDate" to this.createTime.toDateString(),
      "followUpDate" to this.followUpDate.toDateString(),
      "dueDate" to this.dueDate.toDateString(),
      "formKey" to this.formKey,
      "candidateUsers" to candidates.toUsersString(),
      "candidateGroups" to candidates.toGroupsString(),
      "lastUpdatedDate" to this.lastUpdated.toDateString()
    )
  )

fun DelegateTask.toTaskInformation() =
  TaskInformation(
    taskId = this.id,
    meta = metaOf(
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.ACTIVITY_ID to this.taskDefinitionKey,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      CommonRestrictions.BUSINESS_KEY to this.variables?.get(CommonRestrictions.BUSINESS_KEY)?.toString(),
      "taskName" to this.name,
      "taskDescription" to this.description,
      "assignee" to this.assignee,
      "creationDate" to this.createTime.toDateString(),
      "followUpDate" to this.followUpDate.toDateString(),
      "dueDate" to this.dueDate.toDateString(),
      "candidateUsers" to this.candidates.toUsersString(),
      "candidateGroups" to this.candidates.toGroupsString(),
      "lastUpdatedDate" to this.lastUpdated.toDateString()
    )
  )

fun LockedExternalTask.toTaskInformation(): TaskInformation =
  TaskInformation(
    taskId = this.id,
    meta = metaOf(
      CommonRestrictions.ACTIVITY_ID to this.activityId,
      CommonRestrictions.PROCESS_DEFINITION_ID to this.processDefinitionId,
      CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionKey,
      CommonRestrictions.PROCESS_INSTANCE_ID to this.processInstanceId,
      CommonRestrictions.TENANT_ID to this.tenantId,
      CommonRestrictions.BUSINESS_KEY to this.businessKey,
      "topicName" to this.topicName,
      "creationDate" to this.createTime.toDateString(),
      TaskInformation.RETRIES to this.retries?.toString(),
    )
  )

/**
 * Creates a map of the provided pairs, dropping any pair whose value is `null`.
 */
fun metaOf(vararg pairs: Pair<String, String?>): Map<String, String> =
  sequenceOf(*pairs)
    .filter { it.second != null }
    .associate {
      @Suppress("UNCHECKED_CAST")
      it as Pair<String, String>
    }

/**
 * Converts engine internal representation into a string.
 */
fun Date?.toDateString() = this?.toInstant()?.toIso8601()

/**
 * Converts to offset date time in ISO8601 in UTC.
 */
fun Instant.toIso8601() = OffsetDateTime.ofInstant(this, ZoneOffset.UTC).toString()

/**
 * Extracts candidates groups as a comma-separated string.
 */
fun Set<IdentityLink>.toGroupsString() = this.mapNotNull { it.groupId }.sorted().joinToString(",")
/**
 * Extracts candidates users as a comma-separated string.
 */
fun Set<IdentityLink>.toUsersString() = this.mapNotNull { it.userId }.sorted().joinToString(",")
