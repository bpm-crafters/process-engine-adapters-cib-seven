package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor

import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptor
import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptorChain
import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptorContext
import dev.bpmcrafters.processengineapi.task.TaskHandlerOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

private val logger = KotlinLogging.logger {}

/**
 * Example interceptor: counts task handler executions per outcome and measures their duration
 * using Micrometer.
 *
 * Counter `process.engine.task.handler.executions` with tags `taskType`, `taskDescriptionKey`, `outcome`.
 * Timer   `process.engine.task.handler.duration` with tags `taskType`, `taskDescriptionKey`.
 */
class MetricsTaskHandlerInterceptor(
  private val registry: MeterRegistry
) : TaskHandlerInterceptor {

  companion object {
    const val PREFIX = "[INTERCEPTOR-METRICS]"
    const val EXECUTIONS = "process.engine.task.handler.executions"
    const val DURATION = "process.engine.task.handler.duration"

    const val OUTCOME_COMPLETED = "completed"
    const val OUTCOME_COMPLETED_BY_ERROR = "completed_by_error"
    const val OUTCOME_FAILED = "failed"
    const val OUTCOME_UNDETERMINED = "undetermined"
    const val OUTCOME_EXCEPTION = "exception"

    fun TaskHandlerOutcome.tag(): String = when (this) {
      TaskHandlerOutcome.Completed -> OUTCOME_COMPLETED
      is TaskHandlerOutcome.CompletedByError -> OUTCOME_COMPLETED_BY_ERROR
      is TaskHandlerOutcome.Failed -> OUTCOME_FAILED
      TaskHandlerOutcome.Undetermined -> OUTCOME_UNDETERMINED
    }
  }

  override fun intercept(context: TaskHandlerInterceptorContext, chain: TaskHandlerInterceptorChain): TaskHandlerOutcome {
    val sample = Timer.start(registry)
    var outcomeTag = OUTCOME_EXCEPTION
    try {
      return chain.proceed().also { outcomeTag = it.tag() }
    } finally {
      val taskType = context.taskType.name
      val key = context.taskDescriptionKey ?: "none"
      sample.stop(registry.timer(DURATION, "taskType", taskType, "taskDescriptionKey", key))
      registry.counter(EXECUTIONS, "taskType", taskType, "taskDescriptionKey", key, "outcome", outcomeTag).increment()
      logger.info { "$PREFIX counted task ${context.taskInformation.taskId} with outcome=$outcomeTag" }
    }
  }
}
