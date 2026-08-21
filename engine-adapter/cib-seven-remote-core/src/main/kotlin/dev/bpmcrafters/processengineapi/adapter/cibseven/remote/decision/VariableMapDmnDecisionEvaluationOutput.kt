package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.decision

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.decision.DecisionEvaluationOutput
import org.cibseven.bpm.engine.variable.VariableMap

/**
 * Delegating output.
 */
data class VariableMapDmnDecisionEvaluationOutput(
  val entries: VariableMap,
  val dataConverter: AdapterDataConverter
) : DecisionEvaluationOutput {

  override fun <T : Any> asType(type: Class<T>): T? {
    try {
      if (entries.keys.size == 1) {
        if (entries.values.first() == null) {
          return null
        }
        return dataConverter.convert(entries.values.first(), type)
      }
      return dataConverter.convert(entries, type)
    } catch (e: Exception) {
      throw IllegalStateException("Can't deserialize into ${type.name} decision output: ${asMap()}", e)
    }
  }

  override fun asMap(): Map<String, Any?> {
    return entries
  }
}
