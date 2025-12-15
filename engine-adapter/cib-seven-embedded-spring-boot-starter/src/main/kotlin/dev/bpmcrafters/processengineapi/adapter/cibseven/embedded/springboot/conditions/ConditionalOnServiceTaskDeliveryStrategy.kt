package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy
import org.springframework.context.annotation.Conditional

/**
 * Conditions matches if the given strategy is equal to the configured one in application property: `DEFAULT_PREFIX`.serviceTasks.deliveryStrategy
 */
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(OnServiceTaskDeliveryStrategyCondition::class)
annotation class ConditionalOnServiceTaskDeliveryStrategy(
  val strategy: ExternalServiceTaskDeliveryStrategy = ExternalServiceTaskDeliveryStrategy.EMBEDDED_SCHEDULED,
)

