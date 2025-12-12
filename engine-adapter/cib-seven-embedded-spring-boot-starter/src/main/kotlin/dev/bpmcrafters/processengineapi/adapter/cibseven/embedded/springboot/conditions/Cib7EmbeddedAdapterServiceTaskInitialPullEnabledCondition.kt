package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.conditions

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.Cib7EmbeddedAdapterProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.bind.BindResult
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Condition which returns true if the following conditions are true:
 * * `dev.bpm-crafters.process-api.adapter.cib7embedded.enabled` is true
 * * `dev.bpm-crafters.process-api.adapter.cib7embedded.service-tasks.execute-initial-pull-on-startup` is true
 */
open class Cib7EmbeddedAdapterServiceTaskInitialPullEnabledCondition : Cib7EmbeddedAdapterEnabledCondition() {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    if (!super.matches(context, metadata)) {
      return false
    }

    val propertiesBindResult: BindResult<Cib7EmbeddedAdapterProperties> = Binder.get(context.environment)
      .bind(DEFAULT_PREFIX, Cib7EmbeddedAdapterProperties::class.java)

    if (propertiesBindResult.isBound) {
      return propertiesBindResult.get().serviceTasks.executeInitialPullOnStartup
    }
    return false
  }
}
