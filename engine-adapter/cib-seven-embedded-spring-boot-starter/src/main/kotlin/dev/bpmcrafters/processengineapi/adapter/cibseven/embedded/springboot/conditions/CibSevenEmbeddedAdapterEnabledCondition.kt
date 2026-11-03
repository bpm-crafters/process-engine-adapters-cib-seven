package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata


/**
 * Condition which returns true if `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.enabled` is true
 */
open class CibSevenEmbeddedAdapterEnabledCondition : Condition {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    // bind the value of an "enabled" property
    val booleanBinderResult = Binder.get(context.environment)
      .bind("$DEFAULT_PREFIX.${CibSevenEmbeddedAdapterProperties::enabled.name}", Boolean::class.java)
    if (booleanBinderResult.isBound) {
      return booleanBinderResult.get()
    }
    return false
  }
}
