package dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator

import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
class TaskExecutionDecorationTest {

  private val task = TaskInformation("task-1", mapOf("topicName" to "topic"))
  private val payload = mapOf<String, Any?>("key" to "value")
  private val executions = AtomicInteger()
  private val trace = mutableListOf<String>()

  @Test
  fun `runs the execution without decorators`() {
    val outcome = TaskExecutionDecoration.NONE.run(task, payload, executionReturning(TaskOutcome.Completed))

    assertThat(outcome).isEqualTo(TaskOutcome.Completed)
    assertThat(executions.get()).isEqualTo(1)
  }

  @Test
  fun `passes the outcome to every decorator, the first one outermost`() {
    val failure = TaskOutcome.Failed("boom", retriesLeft = 2)

    val outcome = TaskExecutionDecoration(listOf(recording("outer"), recording("inner"))).run(task, payload, executionReturning(failure))

    assertThat(outcome).isEqualTo(failure)
    assertThat(trace).containsExactly("outer before", "inner before", "inner $failure", "outer $failure")
  }

  @Test
  fun `runs the execution once if a decorator skips it`() {
    val skipping = TaskExecutionDecorator { _, _, _ -> }

    val outcome = TaskExecutionDecoration(listOf(skipping)).run(task, payload, executionReturning(TaskOutcome.Completed))

    assertThat(outcome).isEqualTo(TaskOutcome.Completed)
    assertThat(executions.get()).isEqualTo(1)
  }

  @Test
  fun `runs the execution once if a decorator proceeds twice`() {
    val repeating = TaskExecutionDecorator { _, _, execution -> execution.proceed(); execution.proceed() }

    TaskExecutionDecoration(listOf(repeating)).run(task, payload, executionReturning(TaskOutcome.Completed))

    assertThat(executions.get()).isEqualTo(1)
  }

  @Test
  fun `a decorator failing before the execution does not prevent it`() {
    val failing = TaskExecutionDecorator { _, _, _ -> throw IllegalStateException("decorator broken") }

    val outcome = TaskExecutionDecoration(listOf(failing, recording("other"))).run(task, payload, executionReturning(TaskOutcome.Completed))

    assertThat(outcome).isEqualTo(TaskOutcome.Completed)
    assertThat(executions.get()).isEqualTo(1)
  }

  @Test
  fun `a decorator failing after the execution does not change the outcome`() {
    val failingAfter = TaskExecutionDecorator { _, _, execution -> execution.proceed(); throw IllegalStateException("decorator broken") }

    val outcome = TaskExecutionDecoration(listOf(failingAfter)).run(task, payload, executionReturning(TaskOutcome.BpmnError("E-1")))

    assertThat(outcome).isEqualTo(TaskOutcome.BpmnError("E-1"))
  }

  @Test
  fun `an exception of the execution passes through unchanged`() {
    val failure = IllegalArgumentException("execution broken")

    assertThatThrownBy {
      TaskExecutionDecoration(listOf(recording("decorator"))).run(task, payload) { throw failure }
    }.isSameAs(failure)
  }

  @Test
  fun `decorators get read-only views of task and payload`() {
    val modifying = TaskExecutionDecorator { decoratedTask, decoratedPayload, execution ->
      assertThatThrownBy { (decoratedTask.meta as MutableMap<String, String>)["x"] = "y" }.isInstanceOf(UnsupportedOperationException::class.java)
      assertThatThrownBy { (decoratedPayload as MutableMap<String, Any?>)["x"] = "y" }.isInstanceOf(UnsupportedOperationException::class.java)
      execution.proceed()
    }

    TaskExecutionDecoration(listOf(modifying)).run(task, payload, executionReturning(TaskOutcome.Completed))

    assertThat(executions.get()).isEqualTo(1)
  }

  private fun executionReturning(outcome: TaskOutcome) = TaskExecution {
    executions.incrementAndGet()
    outcome
  }

  private fun recording(name: String) = TaskExecutionDecorator { _, _, execution ->
    trace.add("$name before")
    trace.add("$name ${execution.proceed()}")
  }
}
