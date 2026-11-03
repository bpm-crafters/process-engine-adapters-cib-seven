package dev.bpmcrafters.processengineapi.adapter.cibseven.common.task

import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class TaskOutcomeRecorderTest {

  private val recorder = TaskOutcomeRecorder()

  @Test
  fun `returns the outcome recorded during the execution`() {
    val outcome = recorder.track("task-1") { recorder.record("task-1", TaskOutcome.Completed) }

    assertThat(outcome).isEqualTo(TaskOutcome.Completed)
  }

  @Test
  fun `returns pending if nothing was recorded`() {
    assertThat(recorder.track("task-1") { }).isEqualTo(TaskOutcome.Pending)
  }

  @Test
  fun `keeps the first recorded outcome`() {
    val outcome = recorder.track("task-1") {
      recorder.record("task-1", TaskOutcome.Failed("boom", 2))
      recorder.record("task-1", TaskOutcome.Completed)
    }

    assertThat(outcome).isEqualTo(TaskOutcome.Failed("boom", 2))
  }

  @Test
  fun `ignores outcomes of tasks that are not executed right now`() {
    recorder.record("task-1", TaskOutcome.Completed)

    assertThat(recorder.track("task-1") { }).isEqualTo(TaskOutcome.Pending)
    assertThat(recorder.hasOutcome("task-1")).isFalse()
  }

  @Test
  fun `knows whether an outcome was recorded during the execution`() {
    val seen = mutableListOf<Boolean>()

    recorder.track("task-1") {
      seen.add(recorder.hasOutcome("task-1"))
      recorder.record("task-1", TaskOutcome.BpmnError("E-1"))
      seen.add(recorder.hasOutcome("task-1"))
    }

    assertThat(seen).containsExactly(false, true)
  }
}
