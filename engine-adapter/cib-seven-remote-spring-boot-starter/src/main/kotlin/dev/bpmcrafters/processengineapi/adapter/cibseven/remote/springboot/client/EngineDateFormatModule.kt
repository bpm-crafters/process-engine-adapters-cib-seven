package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.client

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal class EngineDateFormatModule : SimpleModule("cib-seven-engine-date-format") {
  init {
    addSerializer(OffsetDateTime::class.java, EngineOffsetDateTimeSerializer)
  }
}

private object EngineOffsetDateTimeSerializer : StdSerializer<OffsetDateTime>(OffsetDateTime::class.java) {
  private val engineDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  override fun serialize(value: OffsetDateTime, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeString(engineDateFormat.format(value))
  }
}
