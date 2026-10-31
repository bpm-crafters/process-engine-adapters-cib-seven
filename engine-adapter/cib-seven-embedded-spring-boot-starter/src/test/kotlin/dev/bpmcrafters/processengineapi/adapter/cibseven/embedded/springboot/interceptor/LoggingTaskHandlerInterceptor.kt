package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor

import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptor
import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptorChain
import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptorContext
import dev.bpmcrafters.processengineapi.task.TaskHandlerOutcome
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Example interceptor: logs every task handler execution and its outcome.
 *
 * Shows the "around" nature of an interceptor: code before `chain.proceed()` runs before the
 * handler (and before all interceptors further down the chain), code after it runs afterward.
 */
class LoggingTaskHandlerInterceptor : TaskHandlerInterceptor {

  companion object {
    const val PREFIX = "[INTERCEPTOR-LOGGING]"
  }

  override fun intercept(context: TaskHandlerInterceptorContext, chain: TaskHandlerInterceptorChain): TaskHandlerOutcome {
    val taskId = context.taskInformation.taskId
    logger.info { "$PREFIX before task $taskId (type=${context.taskType}, key=${context.taskDescriptionKey})" }
    try {
      val outcome = chain.proceed()
      when (outcome) {
        is TaskHandlerOutcome.Failed -> logger.warn { "$PREFIX after task $taskId: failed: ${outcome.reason}" }
        is TaskHandlerOutcome.CompletedByError -> logger.info { "$PREFIX after task $taskId: BPMN error ${outcome.errorCode}" }
        else -> logger.info { "$PREFIX after task $taskId: $outcome" }
      }
      return outcome
    } catch (e: RuntimeException) {
      logger.error { "$PREFIX after task $taskId: exception ${e.message}" }
      throw e
    }
  }
}
