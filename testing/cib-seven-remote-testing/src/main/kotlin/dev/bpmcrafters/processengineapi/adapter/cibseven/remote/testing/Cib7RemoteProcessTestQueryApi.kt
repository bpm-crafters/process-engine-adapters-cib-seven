package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process.toProcessInformation
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.api.ProcessInstanceApi
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.dto.ExternalTaskDto
import org.cibseven.community.rest.client.dto.ExternalTaskQueryDto
import org.cibseven.community.rest.client.dto.FetchExternalTaskTopicDto
import org.cibseven.community.rest.client.dto.FetchExternalTasksDto
import org.cibseven.community.rest.client.dto.TaskQueryDto
import org.cibseven.community.rest.client.invoker.ApiException
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext
import java.util.function.Predicate

private const val MAX_RESULTS = 1_000
private const val EXTERNAL_TASK_LOCK_DURATION_IN_MILLIS = 300_000L
private const val NOT_FOUND = 404
private val ANY_IDENTITY_LINK_TYPE: String? = null

private val SUPPORTED_RESTRICTIONS = setOf(
  CommonRestrictions.PROCESS_INSTANCE_ID,
  CommonRestrictions.PROCESS_DEFINITION_ID,
  CommonRestrictions.PROCESS_DEFINITION_KEY,
  CommonRestrictions.ACTIVITY_ID,
  CommonRestrictions.TENANT_ID,
)

internal class Cib7RemoteProcessTestQueryApi(
  application: ConfigurableApplicationContext,
  private val workerId: String,
) : ProcessTestQueryApi {

  private val taskApi = application.getBean<TaskApi>()
  private val taskIdentityLinkApi = application.getBean<TaskIdentityLinkApi>()
  private val externalTaskApi = application.getBean<ExternalTaskApi>()
  private val processInstanceApi = application.getBean<ProcessInstanceApi>()
  private val metadata = application.getBean<ProcessDefinitionMetaDataResolver>()

  override fun getSupportedRestrictions() = SUPPORTED_RESTRICTIONS

  override fun findUserTasks(
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?,
  ): List<TaskInformation> {
    ensureSupported(restrictions)
    val query = TaskQueryDto().applyRestrictions(restrictions)
    if (taskDescriptionKey != null) {
      query.taskDefinitionKey(taskDescriptionKey)
    }
    return taskApi.queryTasks(0, MAX_RESULTS, query)
      .map { it.toTaskInformation(taskIdentityLinkApi.getIdentityLinks(it.id, ANY_IDENTITY_LINK_TYPE).toSet(), metadata) }
      .filter { predicate?.test(it) ?: true }
  }

  override fun findExternalTasks(
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?,
  ): List<TaskInformation> {
    ensureSupported(restrictions)
    val query = ExternalTaskQueryDto().applyRestrictions(restrictions)
    if (taskDescriptionKey != null) {
      query.topicName(taskDescriptionKey)
    }
    return externalTaskApi.queryExternalTasks(0, MAX_RESULTS, query)
      .filter { it.matchesDefinitionKey(restrictions) }
      .groupBy { it.topicName!! }
      .flatMap { (topicName, tasksOfTopic) -> lock(topicName, tasksOfTopic) }
      .filter { predicate?.test(it) ?: true }
  }

  override fun getProcessInformation(instanceId: String): ProcessInformation? =
    try {
      processInstanceApi.getProcessInstance(instanceId).toProcessInformation()
    } catch (e: ApiException) {
      if (e.code != NOT_FOUND) throw e
      null
    }

  private fun lock(topicName: String, tasksOfTopic: List<ExternalTaskDto>): List<TaskInformation> {
    val candidateIds = tasksOfTopic.map { it.id }.toSet()
    val topic = FetchExternalTaskTopicDto().topicName(topicName).lockDuration(EXTERNAL_TASK_LOCK_DURATION_IN_MILLIS)
    return externalTaskApi
      .fetchAndLock(FetchExternalTasksDto().workerId(workerId).maxTasks(tasksOfTopic.size).topics(listOf(topic)))
      .filter { it.id in candidateIds }
      .map { it.toTaskInformation(metadata) }
  }

  private fun ExternalTaskDto.matchesDefinitionKey(restrictions: Map<String, String>): Boolean {
    val restrictedDefinitionKey = restrictions[CommonRestrictions.PROCESS_DEFINITION_KEY] ?: return true
    return restrictedDefinitionKey == processDefinitionKey
  }
}

private fun TaskQueryDto.applyRestrictions(restrictions: Map<String, String>) = apply {
  restrictions.forEach { (restriction, value) ->
    when (restriction) {
      CommonRestrictions.PROCESS_INSTANCE_ID -> processInstanceId(value)
      CommonRestrictions.PROCESS_DEFINITION_ID -> processDefinitionId(value)
      CommonRestrictions.PROCESS_DEFINITION_KEY -> processDefinitionKey(value)
      CommonRestrictions.ACTIVITY_ID -> taskDefinitionKey(value)
      CommonRestrictions.TENANT_ID -> tenantIdIn(listOf(value))
    }
  }
}

private fun ExternalTaskQueryDto.applyRestrictions(restrictions: Map<String, String>) = apply {
  restrictions.forEach { (restriction, value) ->
    when (restriction) {
      CommonRestrictions.PROCESS_INSTANCE_ID -> processInstanceId(value)
      CommonRestrictions.PROCESS_DEFINITION_ID -> processDefinitionId(value)
      CommonRestrictions.ACTIVITY_ID -> activityId(value)
      CommonRestrictions.TENANT_ID -> tenantIdIn(listOf(value))
    }
  }
}
