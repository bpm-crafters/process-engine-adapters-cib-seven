package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties
import org.springframework.boot.context.properties.bind.BindResult
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

internal class OnServiceTaskDeliveryStrategyCondition : CibSevenEmbeddedAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<CibSevenEmbeddedAdapterProperties> = Binder.get(context.environment)
      .bind(CibSevenEmbeddedAdapterProperties.DEFAULT_PREFIX, CibSevenEmbeddedAdapterProperties::class.java)

    if (propertiesBindResult.isBound) {
      val properties: CibSevenEmbeddedAdapterProperties = propertiesBindResult.get()

      val strategy = metadata
        .getAnnotationAttributes(ConditionalOnServiceTaskDeliveryStrategy::class.java.name)
        ?.get(ConditionalOnServiceTaskDeliveryStrategy::strategy.name) as CibSevenEmbeddedAdapterProperties.ExternalServiceTaskDeliveryStrategy

      return properties.serviceTasks.deliveryStrategy == strategy
    }

    return false
  }
}
