package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.client

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterEnabledCondition
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterProperties
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueTypeRegistration
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueTypeResolverImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization.CustomValueSerializer
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization.JavaSerializationValueSerializer
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization.JsonValueSerializer
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization.SpinJsonValueSerializer
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.serialization.SpinXmlValueSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.variable.Variables
import org.cibseven.bpm.engine.variable.type.ValueTypeResolver
import org.cibseven.community.rest.client.api.DecisionDefinitionApiClient
import org.cibseven.community.rest.client.api.DeploymentApiClient
import org.cibseven.community.rest.client.api.ExternalTaskApiClient
import org.cibseven.community.rest.client.api.MessageApiClient
import org.cibseven.community.rest.client.api.ProcessDefinitionApiClient
import org.cibseven.community.rest.client.api.ProcessInstanceApiClient
import org.cibseven.community.rest.client.api.SignalApiClient
import org.cibseven.community.rest.client.api.TaskApiClient
import org.cibseven.community.rest.client.api.TaskIdentityLinkApiClient
import org.cibseven.community.rest.client.api.TaskLocalVariableApiClient
import org.cibseven.community.rest.client.api.TaskVariableApiClient
import org.cibseven.community.rest.client.invoker.ApiClient
import org.cibseven.spin.plugin.variable.value.SpinValue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional

private val logger = KotlinLogging.logger {}

/**
 * Builds the generated CIB seven REST client and the (Spin-backed) value mapper.
 *
 * Unlike the Camunda 7 adapter — which relies on a Feign client with Spring auto-registered beans —
 * the generated CIB seven client is `apache-httpclient` based. We therefore construct a single
 * [ApiClient] (configured from [Cib7RemoteAdapterProperties.client]) and expose each generated
 * `*ApiClient` as a bean built from it. The value mapper is wired the same way the Camunda 7
 * `ValueMapperConfiguration` did, so serialization behaves identically.
 */
@AutoConfiguration(before = [dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterAutoConfiguration::class])
@Conditional(Cib7RemoteAdapterEnabledCondition::class)
class Cib7RemoteClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ApiClient::class)
  fun cib7RemoteApiClient(properties: Cib7RemoteAdapterProperties): ApiClient =
    ApiClient().apply {
      setBasePath(properties.client.baseUrl)
      if (!properties.client.username.isNullOrBlank()) {
        setUsername(properties.client.username)
        setPassword(properties.client.password)
      }
      logger.debug { "PROCESS-ENGINE-C7-REMOTE-201: REST client configured for ${properties.client.baseUrl}." }
    }

  @Bean
  @ConditionalOnMissingBean
  fun processDefinitionApiClient(apiClient: ApiClient) = ProcessDefinitionApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun processInstanceApiClient(apiClient: ApiClient) = ProcessInstanceApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun messageApiClient(apiClient: ApiClient) = MessageApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun signalApiClient(apiClient: ApiClient) = SignalApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun deploymentApiClient(apiClient: ApiClient) = DeploymentApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun decisionDefinitionApiClient(apiClient: ApiClient) = DecisionDefinitionApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun externalTaskApiClient(apiClient: ApiClient) = ExternalTaskApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskApiClient(apiClient: ApiClient) = TaskApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskIdentityLinkApiClient(apiClient: ApiClient) = TaskIdentityLinkApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskLocalVariableApiClient(apiClient: ApiClient) = TaskLocalVariableApiClient(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskVariableApiClient(apiClient: ApiClient) = TaskVariableApiClient(apiClient)

  // --- value mapping (mirrors the Camunda 7 ValueMapperConfiguration) ---

  @Bean
  @ConditionalOnMissingBean(ValueTypeResolver::class)
  fun valueTypeResolver(): ValueTypeResolver = ValueTypeResolverImpl()

  @Bean
  @ConditionalOnMissingBean
  fun valueTypeRegistration() = ValueTypeRegistration()

  @Bean
  @ConditionalOnClass(SpinValue::class)
  @ConditionalOnMissingBean
  fun spinJsonValueSerializer(valueTypeResolver: ValueTypeResolver, valueTypeRegistration: ValueTypeRegistration) =
    SpinJsonValueSerializer(valueTypeResolver = valueTypeResolver, valueTypeRegistration = valueTypeRegistration)

  @Bean
  @ConditionalOnClass(SpinValue::class)
  @ConditionalOnMissingBean
  fun spinXmlValueSerializer(valueTypeResolver: ValueTypeResolver, valueTypeRegistration: ValueTypeRegistration) =
    SpinXmlValueSerializer(valueTypeResolver = valueTypeResolver, valueTypeRegistration = valueTypeRegistration)

  @Bean
  @ConditionalOnMissingBean(ValueMapper::class)
  fun valueMapper(
    @Qualifier("cib7remote-object-mapper") objectMapper: ObjectMapper,
    valueTypeResolver: ValueTypeResolver,
    valueTypeRegistration: ValueTypeRegistration,
    customValueSerializers: List<CustomValueSerializer>,
  ): ValueMapper = ValueMapper(
    objectMapper = objectMapper,
    valueTypeResolver = valueTypeResolver,
    valueTypeRegistration = valueTypeRegistration,
    serializationFormat = Variables.SerializationDataFormats.JSON,
    valueSerializers = listOf(JavaSerializationValueSerializer(), JsonValueSerializer(objectMapper)),
    customValueSerializers = customValueSerializers,
  )
}
