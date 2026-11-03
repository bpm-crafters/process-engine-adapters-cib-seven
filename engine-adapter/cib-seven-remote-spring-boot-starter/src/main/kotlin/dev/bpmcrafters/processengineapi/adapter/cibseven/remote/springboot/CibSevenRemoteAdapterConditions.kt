package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.UserTaskDeliveryStrategy
import org.springframework.boot.context.properties.bind.BindResult
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Condition which returns true if `dev.bpm-crafters.process-api.adapter.cib-seven-remote.enabled` is true
 */
open class CibSevenRemoteAdapterEnabledCondition : Condition {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    // bind the value of "enabled" property
    val booleanBinderResult = Binder.get(context.environment).bind("$DEFAULT_PREFIX.${CibSevenRemoteAdapterProperties::enabled.name}", Boolean::class.java)
    if (booleanBinderResult.isBound) {
      return booleanBinderResult.get()
    }
    return false
  }
}

/**
 * Conditions matches if the given strategy is equal to the configured one in application property: `DEFAULT_PREFIX`.userTasks.deliveryStrategy
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(
  OnUserTaskDeliveryStrategyCondition::class
)
annotation class ConditionalOnUserTaskDeliveryStrategy(
  val strategy: UserTaskDeliveryStrategy = UserTaskDeliveryStrategy.REMOTE_SCHEDULED,
)

internal class OnUserTaskDeliveryStrategyCondition : CibSevenRemoteAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {

    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<CibSevenRemoteAdapterProperties> = Binder.get(context.environment)
      .bind(DEFAULT_PREFIX, CibSevenRemoteAdapterProperties::class.java)

    if (propertiesBindResult.isBound) {
      val properties: CibSevenRemoteAdapterProperties = propertiesBindResult.get()

      val strategy = metadata
        .getAnnotationAttributes(ConditionalOnUserTaskDeliveryStrategy::class.java.name)
        ?.get(ConditionalOnUserTaskDeliveryStrategy::strategy.name) as UserTaskDeliveryStrategy

      return properties.userTasks.deliveryStrategy == strategy
    }

    return false
  }
}

/**
 * Conditions matches if the given strategy is equal to the configured one in application property: `DEFAULT_PREFIX`.serviceTasks.deliveryStrategy
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(
  OnServiceTaskDeliveryStrategyCondition::class
)
annotation class ConditionalOnServiceTaskDeliveryStrategy(
  val strategy: ExternalServiceTaskDeliveryStrategy = ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED,
)

internal class OnServiceTaskDeliveryStrategyCondition : CibSevenRemoteAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<CibSevenRemoteAdapterProperties> = Binder.get(context.environment)
      .bind(DEFAULT_PREFIX, CibSevenRemoteAdapterProperties::class.java)

    if (propertiesBindResult.isBound) {
      val properties: CibSevenRemoteAdapterProperties = propertiesBindResult.get()

      val strategy = metadata
        .getAnnotationAttributes(ConditionalOnServiceTaskDeliveryStrategy::class.java.name)
        ?.get(ConditionalOnServiceTaskDeliveryStrategy::strategy.name) as ExternalServiceTaskDeliveryStrategy

      return properties.serviceTasks.deliveryStrategy == strategy
    }

    return false
  }
}
