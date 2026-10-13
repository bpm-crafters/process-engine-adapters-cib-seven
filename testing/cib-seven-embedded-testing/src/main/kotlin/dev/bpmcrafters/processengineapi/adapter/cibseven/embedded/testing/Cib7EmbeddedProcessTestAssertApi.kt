package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestAssertApi
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestQueryApi
import dev.bpmcrafters.processengineapi.testing.config.ProcessTestPollingConfiguration
import org.awaitility.Awaitility.await
import org.cibseven.bpm.engine.ProcessEngine
import java.util.concurrent.Callable
import java.util.function.Predicate

internal class Cib7EmbeddedProcessTestAssertApi(
  private val engine: ProcessEngine,
  private val query: ProcessTestQueryApi,
  private val polling: ProcessTestPollingConfiguration,
) : ProcessTestAssertApi {

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
      val activeElementIds = engine.runtimeService.getActiveActivityIds(instanceId)
      require(elementId in activeElementIds) { "Process $instanceId is not waiting in $elementId, active elements: $activeElementIds" }
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
      val runningInstance = engine.runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult()
      require(runningInstance == null) { "Process $instanceId is still active" }
      val historicInstance = engine.historyService.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult()
      require(historicInstance != null) { "No historic process instance found for $instanceId" }
    }
  }

  override fun processHasIncidents(instanceId: String, restrictions: Map<String, String>) {
    eventually {
      val incidentCount = engine.runtimeService.createIncidentQuery().processInstanceId(instanceId).count()
      require(incidentCount > 0) { "No incidents found for process $instanceId" }
    }
  }

  private fun passedElementIds(instanceId: String, expectedElementIds: Array<out String>): List<String> {
    require(expectedElementIds.isNotEmpty()) { "At least one element id is required." }
    return engine.historyService.createHistoricActivityInstanceQuery()
      .processInstanceId(instanceId)
      .orderByHistoricActivityInstanceStartTime().asc()
      .orderPartiallyByOccurrence().asc()
      .list()
      .map { it.activityId }
  }

  private fun <T> eventually(supplier: () -> T): T =
    await()
      .pollInterval(polling.pollInterval)
      .atMost(polling.timeout)
      .ignoreExceptionsInstanceOf(IllegalArgumentException::class.java)
      .until(Callable { supplier() }, Predicate { true })
}

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
