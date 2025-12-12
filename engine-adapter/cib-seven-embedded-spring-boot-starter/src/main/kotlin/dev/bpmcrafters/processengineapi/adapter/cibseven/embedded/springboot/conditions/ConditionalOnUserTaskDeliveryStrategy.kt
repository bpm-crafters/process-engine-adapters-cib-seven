package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.UserTaskDeliveryStrategy
import org.springframework.context.annotation.Conditional

/**
 * Conditions matches if the given strategy is equal to the configured one in application property: `DEFAULT_PREFIX`.userTasks.deliveryStrategy
 */
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(OnUserTaskDeliveryStrategyCondition::class)
annotation class ConditionalOnUserTaskDeliveryStrategy(
  val strategies: Array<UserTaskDeliveryStrategy> = [UserTaskDeliveryStrategy.EMBEDDED_SCHEDULED],
)

