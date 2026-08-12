package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization

import org.cibseven.bpm.engine.variable.value.SerializableValue
import org.cibseven.bpm.engine.variable.value.SerializationDataFormat
import org.cibseven.bpm.engine.variable.value.TypedValue

interface ValueSerializer {

  val serializationDataFormat: SerializationDataFormat

  fun serializeValue(value: TypedValue): SerializableValue

  fun deserializeValue(value: SerializableValue): TypedValue

}
