package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.bind.BindResult
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Condition which returns true if the following conditions are true:
 * * `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.enabled` is true
 * * `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.user-tasks.execute-initial-pull-on-startup` is true
 */
open class CibSevenEmbeddedAdapterUserTaskInitialPullEnabledCondition : CibSevenEmbeddedAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<CibSevenEmbeddedAdapterProperties> = Binder.get(context.environment)
      .bind(DEFAULT_PREFIX, CibSevenEmbeddedAdapterProperties::class.java)
    if (propertiesBindResult.isBound) {
      return propertiesBindResult.get().userTasks.executeInitialPullOnStartup
    }
    return false
  }
}
