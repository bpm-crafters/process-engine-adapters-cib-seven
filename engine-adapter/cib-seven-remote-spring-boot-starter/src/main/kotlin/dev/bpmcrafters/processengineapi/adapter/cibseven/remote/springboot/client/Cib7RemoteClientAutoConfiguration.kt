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
import org.cibseven.community.rest.client.api.DecisionDefinitionApi
import org.cibseven.community.rest.client.api.DeploymentApi
import org.cibseven.community.rest.client.api.ExternalTaskApi
import org.cibseven.community.rest.client.api.MessageApi
import org.cibseven.community.rest.client.api.ProcessDefinitionApi
import org.cibseven.community.rest.client.api.ProcessInstanceApi
import org.cibseven.community.rest.client.api.SignalApi
import org.cibseven.community.rest.client.api.TaskApi
import org.cibseven.community.rest.client.api.TaskIdentityLinkApi
import org.cibseven.community.rest.client.api.TaskLocalVariableApi
import org.cibseven.community.rest.client.api.TaskVariableApi
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
 * `*Api` as a bean built from it. The value mapper is wired the same way the Camunda 7
 * `ValueMapperConfiguration` did, so serialization behaves identically.
 */
@AutoConfiguration(before = [dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.Cib7RemoteAdapterAutoConfiguration::class])
@Conditional(Cib7RemoteAdapterEnabledCondition::class)
class Cib7RemoteClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ApiClient::class)
  fun cib7RemoteApi(properties: Cib7RemoteAdapterProperties): ApiClient =
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
  fun processDefinitionApi(apiClient: ApiClient) = ProcessDefinitionApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun processInstanceApi(apiClient: ApiClient) = ProcessInstanceApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun messageApi(apiClient: ApiClient) = MessageApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun signalApi(apiClient: ApiClient) = SignalApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun deploymentApi(apiClient: ApiClient) = DeploymentApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun decisionDefinitionApi(apiClient: ApiClient) = DecisionDefinitionApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun externalTaskApi(apiClient: ApiClient) = ExternalTaskApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskApi(apiClient: ApiClient) = TaskApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskIdentityLinkApi(apiClient: ApiClient) = TaskIdentityLinkApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskLocalVariableApi(apiClient: ApiClient) = TaskLocalVariableApi(apiClient)

  @Bean
  @ConditionalOnMissingBean
  fun taskVariableApi(apiClient: ApiClient) = TaskVariableApi(apiClient)

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
