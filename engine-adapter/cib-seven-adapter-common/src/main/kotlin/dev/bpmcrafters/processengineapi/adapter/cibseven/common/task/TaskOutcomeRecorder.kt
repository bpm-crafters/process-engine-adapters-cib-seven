package dev.bpmcrafters.processengineapi.adapter.cibseven.common.task

import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects the outcome a task reports to the engine while it is executed. The delivery tracks the execution,
 * the completion API and the delivery's failure handling record the outcome they report.
 * Outcomes of tasks that are not executed right now are ignored; the first recorded outcome wins.
 */
@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class TaskOutcomeRecorder {

  private val outcomesByTaskId = ConcurrentHashMap<String, TaskOutcome>()

  fun track(taskId: String, execution: () -> Unit): TaskOutcome {
    outcomesByTaskId[taskId] = TaskOutcome.Pending
    try {
      execution()
      return outcomesByTaskId.getValue(taskId)
    } finally {
      outcomesByTaskId.remove(taskId)
    }
  }

  fun record(taskId: String, outcome: TaskOutcome) {
    outcomesByTaskId.computeIfPresent(taskId) { _, currentOutcome ->
      val isFirstOutcome = currentOutcome == TaskOutcome.Pending
      if (isFirstOutcome) outcome else currentOutcome
    }
  }

  fun hasOutcome(taskId: String): Boolean {
    val outcome = outcomesByTaskId[taskId]
    return outcome != null && outcome != TaskOutcome.Pending
  }
}
