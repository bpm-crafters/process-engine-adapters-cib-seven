package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization

import jakarta.annotation.PostConstruct
import org.cibseven.bpm.engine.variable.Variables
import org.cibseven.bpm.engine.variable.type.ValueTypeResolver
import org.cibseven.bpm.engine.variable.value.SerializableValue
import org.cibseven.bpm.engine.variable.value.SerializationDataFormat
import org.cibseven.bpm.engine.variable.value.TypedValue
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueTypeRegistration
import org.cibseven.spin.Spin
import org.cibseven.spin.json.SpinJsonNode
import org.cibseven.spin.plugin.variable.SpinValues
import org.cibseven.spin.plugin.variable.type.impl.JsonValueTypeImpl
import org.cibseven.spin.plugin.variable.value.JsonValue
import org.cibseven.spin.plugin.variable.value.impl.JsonValueImpl

/**
 * Custom value mapper to map SPIN values.
 * Will only be used, if the SPIN dependencies are on the classpath.
 */
class SpinJsonValueSerializer(
  private val valueTypeResolver: ValueTypeResolver,
  private val valueTypeRegistration: ValueTypeRegistration
) : CustomValueSerializer {

  override val serializationDataFormat: SerializationDataFormat = Variables.SerializationDataFormats.JSON

  /**
   * Adds the SPIN value types to the list of known types by the [ValueTypeResolver].
   */
  @PostConstruct
  fun addValueTypes() {
    valueTypeResolver.addType(JsonValueTypeImpl())
    valueTypeRegistration.registerTypeForClass(SpinJsonNode::class
    ) { value, isTransient, _ -> SpinValues.jsonValue(value as SpinJsonNode, isTransient).create() }
  }

  override fun canSerializeValue(value: TypedValue): Boolean = value is JsonValue

  override fun canDeserializeValue(value: SerializableValue): Boolean = value is JsonValue

  override fun serializeValue(value: TypedValue): SerializableValue =
    (if (value is JsonValueImpl) {
      value.apply { valueSerialized = value.value.toString() }
    } else {
      value
    }) as SerializableValue

  override fun deserializeValue(value: SerializableValue): SerializableValue =
    when (value) {
      is JsonValue ->
        SpinValues.jsonValue(Spin.JSON(value.valueSerialized))
          .create()
          .apply { (this as JsonValueImpl).valueSerialized = value.valueSerialized }
      else -> value
    }

}
