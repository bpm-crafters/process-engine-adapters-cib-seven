package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestAssertApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.awaitility.Awaitility.await
import org.cibseven.community.rest.client.api.HistoricActivityInstanceApi
import org.cibseven.community.rest.client.api.HistoricProcessInstanceApi
import org.cibseven.community.rest.client.api.ProcessInstanceApi
import org.cibseven.community.rest.client.dto.ActivityInstanceDto
import org.cibseven.community.rest.client.dto.HistoricActivityInstanceQueryDto
import org.cibseven.community.rest.client.dto.HistoricActivityInstanceQueryDtoSortingInner
import org.cibseven.community.rest.client.dto.HistoricActivityInstanceQueryDtoSortingInner.SortByEnum
import org.cibseven.community.rest.client.dto.HistoricProcessInstanceQueryDto
import org.cibseven.community.rest.client.dto.ProcessInstanceQueryDto
import org.cibseven.community.rest.client.invoker.ApiClient
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.Callable
import java.util.function.Predicate

private const val MAX_HISTORIC_ACTIVITIES = 1_000

internal class Cib7RemoteProcessTestAssertApi(
  application: ConfigurableApplicationContext,
  private val query: ProcessTestQueryApi,
  private val polling: ProcessTestPollingConfiguration,
) : ProcessTestAssertApi {

  private val processInstanceApi = application.getBean<ProcessInstanceApi>()
  private val historicActivityInstanceApi = HistoricActivityInstanceApi(application.getBean<ApiClient>())
  private val historicProcessInstanceApi = HistoricProcessInstanceApi(application.getBean<ApiClient>())

  override fun getSupportedRestrictions() = query.getSupportedRestrictions()

  override fun processWaitsInUserTask(
    instanceId: String,
    taskDescriptionKey: String?,
    restrictions: Map<String, String>,
    predicate: Predicate<TaskInformation>?,
  ) = eventually {
    query.findUserTasks(taskDescriptionKey, restrictions + (CommonRestrictions.PROCESS_INSTANCE_ID to instanceId), predicate).exactlyOne()
  }

  override fun processWaitsInElement(instanceId: String, elementId: String, restrictions: Map<String, String>) {
    eventually {
      val activityInstanceTree = processInstanceApi.getActivityInstanceTree(instanceId)
      require(activityInstanceTree.contains(elementId)) { "Process $instanceId is not waiting in $elementId" }
    }
  }

  override fun userTaskIsSelected(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?) =
    eventually { query.findUserTasks(taskDescriptionKey, restrictions, predicate).exactlyOne() }

  override fun externalTaskIsSelected(taskDescriptionKey: String?, restrictions: Map<String, String>, predicate: Predicate<TaskInformation>?) =
    eventually { query.findExternalTasks(taskDescriptionKey, restrictions, predicate).exactlyOne() }

  override fun processHasPassed(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) {
    val passedElementIds = passedElementIds(instanceId, elementIds)
    require(elementIds.all { it in passedElementIds }) {
      "Expected ${elementIds.toList()} to be passed, actually passed: $passedElementIds"
    }
  }

  override fun processHasPassedInOrder(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) {
    val passedElementIds = passedElementIds(instanceId, elementIds)
    require(passedElementIds.containsInOrder(elementIds.toList())) {
      "Expected ${elementIds.toList()} to be passed in order, actually passed: $passedElementIds"
    }
  }

  override fun processHasNotPassed(instanceId: String, restrictions: Map<String, String>, vararg elementIds: String) {
    val passedElementIds = passedElementIds(instanceId, elementIds)
    require(elementIds.none { it in passedElementIds }) {
      "Expected ${elementIds.toList()} not to be passed, actually passed: $passedElementIds"
    }
  }

  override fun processIsFinished(instanceId: String, restrictions: Map<String, String>) {
    eventually {
      require(query.getProcessInformation(instanceId) == null) { "Process $instanceId is still active" }
      val historicInstances = historicProcessInstanceApi.queryHistoricProcessInstances(0, 1, HistoricProcessInstanceQueryDto().processInstanceId(instanceId))
      require(historicInstances.isNotEmpty()) { "No historic process instance found for $instanceId" }
    }
  }

  override fun processHasIncidents(instanceId: String, restrictions: Map<String, String>) {
    eventually {
      val instancesWithIncident = processInstanceApi.queryProcessInstances(
        0, 1, ProcessInstanceQueryDto().processInstanceIds(listOf(instanceId)).withIncident(true)
      )
      require(instancesWithIncident.isNotEmpty()) { "No incidents found for process $instanceId" }
    }
  }

  private fun passedElementIds(instanceId: String, expectedElementIds: Array<out String>): List<String> {
    require(expectedElementIds.isNotEmpty()) { "At least one element id is required." }
    val historicActivitiesInExecutionOrder = HistoricActivityInstanceQueryDto()
      .processInstanceId(instanceId)
      .sorting(listOf(ascendingBy(SortByEnum.START_TIME), ascendingBy(SortByEnum.OCCURRENCE)))
    return historicActivityInstanceApi
      .queryHistoricActivityInstances(0, MAX_HISTORIC_ACTIVITIES, historicActivitiesInExecutionOrder)
      .mapNotNull { it.activityId }
  }

  private fun <T> eventually(supplier: () -> T): T =
    await()
      .pollInterval(polling.pollInterval)
      .atMost(polling.timeout)
      .ignoreExceptionsInstanceOf(IllegalArgumentException::class.java)
      .until(Callable { supplier() }, Predicate { true })
}

private fun ascendingBy(sortBy: SortByEnum) =
  HistoricActivityInstanceQueryDtoSortingInner().sortBy(sortBy).sortOrder(HistoricActivityInstanceQueryDtoSortingInner.SortOrderEnum.ASC)

private fun ActivityInstanceDto.contains(elementId: String): Boolean =
  activityId == elementId || childActivityInstances.orEmpty().any { it.contains(elementId) }

private fun List<TaskInformation>.exactlyOne(): TaskInformation {
  require(size == 1) { "Expected exactly one task but found $size: $this" }
  return single()
}

private fun List<String>.containsInOrder(expected: List<String>): Boolean {
  var searchFrom = 0
  return expected.all { element ->
    val offset = subList(searchFrom, size).indexOf(element)
    searchFrom += offset + 1
    offset >= 0
  }
}
