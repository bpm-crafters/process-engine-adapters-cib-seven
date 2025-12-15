package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties
import org.springframework.boot.context.properties.bind.BindResult
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

internal class OnUserTaskDeliveryStrategyCondition : Cib7EmbeddedAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {

    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<Cib7EmbeddedAdapterProperties> = Binder.get(context.environment)
      .bind(Cib7EmbeddedAdapterProperties.DEFAULT_PREFIX, Cib7EmbeddedAdapterProperties::class.java)

    if (propertiesBindResult.isBound) {
      val properties: Cib7EmbeddedAdapterProperties = propertiesBindResult.get()

      @Suppress("UNCHECKED_CAST")
      val strategies: Array<Cib7EmbeddedAdapterProperties.UserTaskDeliveryStrategy> = metadata
        .getAnnotationAttributes(ConditionalOnUserTaskDeliveryStrategy::class.java.name)
        ?.get(ConditionalOnUserTaskDeliveryStrategy::strategies.name) as Array<Cib7EmbeddedAdapterProperties.UserTaskDeliveryStrategy>

      return strategies.contains(properties.userTasks.deliveryStrategy)
    }

    return false
  }
}
