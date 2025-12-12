package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion

import java.util.function.Function

@FunctionalInterface
interface FailureRetrySupplier : Function<String, FailureRetrySupplier.FailureRetry> {

  data class FailureRetry(
    val retryCount: Int,
    val retryTimeout: Long
  )
}
