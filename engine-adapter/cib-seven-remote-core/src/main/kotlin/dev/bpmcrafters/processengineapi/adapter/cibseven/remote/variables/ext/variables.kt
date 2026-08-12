/**
 * Extensions for Camunda BPM variables.
 */
package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ext

import org.cibseven.bpm.engine.variable.Variables
import org.cibseven.bpm.engine.variable.value.SerializationDataFormat
import org.cibseven.bpm.engine.variable.value.builder.ObjectValueBuilder

// Serialization data formats overloads name() and getName() in kotlin, so we use the format alias.
val SerializationDataFormat.format: String get() = this.name

fun ObjectValueBuilder.serializationDataFormat(format: Variables.SerializationDataFormats) = apply {
  serializationDataFormat(format.format)
}
