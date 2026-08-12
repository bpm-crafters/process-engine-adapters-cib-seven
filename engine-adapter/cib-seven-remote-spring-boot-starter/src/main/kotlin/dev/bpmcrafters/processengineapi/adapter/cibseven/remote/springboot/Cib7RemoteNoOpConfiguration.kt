package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.NoOpServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion.NoOpUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional

private val logger = KotlinLogging.logger {}

@AutoConfiguration
@AutoConfigureAfter(Cib7RemoteAdapterAutoConfiguration::class)
@Conditional(Cib7RemoteAdapterEnabledCondition::class)
class Cib7RemoteNoOpConfiguration {

  @ConditionalOnUserTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.UserTaskDeliveryStrategy.DISABLED
  )
  @Bean
  fun noOpUserTaskCompletionApi(): UserTaskCompletionApi {
    logger.info { "PROCESS-ENGINE-C7-REMOTE-210: Configured a NO-OP UserTaskCompletion API, no user task completion is possible." }
    return NoOpUserTaskCompletionApiImpl()
  }

  @ConditionalOnServiceTaskDeliveryStrategy(
    strategy = Cib7RemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy.DISABLED
  )
  @Bean
  fun noOpServiceTaskCompletionApi(): ServiceTaskCompletionApi {
    logger.info { "PROCESS-ENGINE-C7-REMOTE-211: Configured a NO-OP ServiceTaskCompletion API, no service task completion is possible." }
    return NoOpServiceTaskCompletionApiImpl()
  }
}
