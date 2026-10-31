package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.interceptor

import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptor
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order

/**
 * Registers two interceptors as plain Spring beans. The adapter auto-configuration picks up all
 * [TaskHandlerInterceptor] beans and chains them in `@Order`:
 *
 * logging (outer) -> metrics (inner) -> task handler
 */
@TestConfiguration(proxyBeanMethods = false)
class InterceptorTestConfiguration {

  @Bean
  fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

  @Bean
  @Order(1)
  fun loggingTaskHandlerInterceptor(): TaskHandlerInterceptor = LoggingTaskHandlerInterceptor()

  @Bean
  @Order(2)
  fun metricsTaskHandlerInterceptor(registry: MeterRegistry): TaskHandlerInterceptor = MetricsTaskHandlerInterceptor(registry)
}
