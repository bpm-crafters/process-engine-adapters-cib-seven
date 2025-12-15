package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties
import org.springframework.boot.context.properties.bind.BindResult
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

internal class OnServiceTaskDeliveryStrategyCondition : Cib7EmbeddedAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<Cib7EmbeddedAdapterProperties> = Binder.get(context.environment)
      .bind(Cib7EmbeddedAdapterProperties.DEFAULT_PREFIX, Cib7EmbeddedAdapterProperties::class.java)

    if (propertiesBindResult.isBound) {
      val properties: Cib7EmbeddedAdapterProperties = propertiesBindResult.get()

      val strategy = metadata
        .getAnnotationAttributes(ConditionalOnServiceTaskDeliveryStrategy::class.java.name)
        ?.get(ConditionalOnServiceTaskDeliveryStrategy::strategy.name) as Cib7EmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy

      return properties.serviceTasks.deliveryStrategy == strategy
    }

    return false
  }
}
