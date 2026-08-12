package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.serialization

interface AdapterDataConverter {

  fun <T : Any> convert(value: Any?, type: Class<T>): T?

}
