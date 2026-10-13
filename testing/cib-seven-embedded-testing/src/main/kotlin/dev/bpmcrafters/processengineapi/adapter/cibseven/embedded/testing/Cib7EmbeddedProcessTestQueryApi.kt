package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.ProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.toProcessInformation
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.externaltask.ExternalTask
import org.cibseven.bpm.engine.task.Task
import java.util.function.Predicate

private const val EXTERNAL_TASK_LOCK_DURATION_IN_MILLIS = 300_000L

private val SUPPORTED_RESTRICTIONS = setOf(
  CommonRestrictions.PROCESS_INSTANCE_ID,
  CommonRestrictions.PROCESS_DEFINITION_ID,
  CommonRestrictions.PROCESS_DEFINITION_KEY,
  CommonRestrictions.ACTIVITY_ID,
  CommonRestrictions.TENANT_ID,
)

internal class Cib7EmbeddedProcessTestQueryApi(
  private val engine: ProcessEngine,
  private val metadata: ProcessDefinitionMetaDataResolver,
  private val workerId: String,
) : ProcessTestQueryApi {

  override fun getSupportedRestrictions() = SUPPORTED_RESTRICTIONS

  override fun findUserTasks(
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?,
  ): List<TaskInformation> {
    ensureSupported(restrictions)
    return engine.taskService.createTaskQuery().active().initializeFormKeys().list()
      .filter { taskDescriptionKey == null || taskDescriptionKey == it.taskDefinitionKey || taskDescriptionKey == it.id }
      .filter { it.matches(restrictions) }
      .map { it.toTaskInformation(engine.taskService.getIdentityLinksForTask(it.id).toSet(), metadata.getProcessDefinitionKey(it.processDefinitionId)) }
      .filter { predicate?.test(it) ?: true }
  }

  override fun findExternalTasks(
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?,
  ): List<TaskInformation> {
    ensureSupported(restrictions)
    val candidates = engine.externalTaskService.createExternalTaskQuery().list()
      .filter { taskDescriptionKey == null || taskDescriptionKey == it.topicName || taskDescriptionKey == it.id }
      .filter { it.matches(restrictions) }
    return candidates
      .groupBy { it.topicName }
      .flatMap { (topicName, tasksOfTopic) -> lock(topicName, tasksOfTopic) }
      .filter { predicate?.test(it) ?: true }
  }

  override fun getProcessInformation(instanceId: String): ProcessInformation? =
    engine.runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult()?.toProcessInformation(metadata)

  private fun lock(topicName: String, tasksOfTopic: List<ExternalTask>): List<TaskInformation> {
    val candidateIds = tasksOfTopic.map { it.id }.toSet()
    return engine.externalTaskService.fetchAndLock(tasksOfTopic.size, workerId)
      .topic(topicName, EXTERNAL_TASK_LOCK_DURATION_IN_MILLIS)
      .execute()
      .filter { it.id in candidateIds }
      .map { it.toTaskInformation() }
  }

  private fun Task.matches(restrictions: Map<String, String>) =
    matches(restrictions, processInstanceId, processDefinitionId, taskDefinitionKey, tenantId)

  private fun ExternalTask.matches(restrictions: Map<String, String>) =
    matches(restrictions, processInstanceId, processDefinitionId, activityId, tenantId)

  private fun matches(
    restrictions: Map<String, String>,
    processInstanceId: String,
    processDefinitionId: String,
    activityId: String,
    tenantId: String?,
  ) = restrictions.all { (restriction, expected) ->
    when (restriction) {
      CommonRestrictions.PROCESS_INSTANCE_ID -> expected == processInstanceId
      CommonRestrictions.PROCESS_DEFINITION_ID -> expected == processDefinitionId
      CommonRestrictions.PROCESS_DEFINITION_KEY -> expected == metadata.getProcessDefinitionKey(processDefinitionId)
      CommonRestrictions.ACTIVITY_ID -> expected == activityId
      CommonRestrictions.TENANT_ID -> expected == tenantId
      else -> false
    }
  }
}
