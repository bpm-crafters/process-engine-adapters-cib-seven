package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.serialization.Jackson2AdapterDataConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Provides the (Jackson 2) object mapper and data converter used by the adapter.
 *
 * The generated CIB seven REST client and the ported value mapper both work with the Jackson 2
 * (`com.fasterxml`) API, so the adapter uses a dedicated Jackson 2 object mapper of its own rather
 * than relying on the application's (possibly Jackson 3) `ObjectMapper` bean under Spring Boot 4.
 */
@AutoConfiguration(before = [Cib7RemoteAdapterAutoConfiguration::class])
class Cib7RemoteSerializationAutoConfiguration {

  @Bean("cib7remote-object-mapper")
  @Qualifier("cib7remote-object-mapper")
  @ConditionalOnMissingBean(name = ["cib7remote-object-mapper"])
  fun cib7RemoteObjectMapper(): ObjectMapper =
    JsonMapper.builder().addModule(JavaTimeModule()).build()

  @Bean
  @ConditionalOnMissingBean(AdapterDataConverter::class)
  fun adapterDataConverter(@Qualifier("cib7remote-object-mapper") objectMapper: ObjectMapper): AdapterDataConverter =
    Jackson2AdapterDataConverter(objectMapper)
}
