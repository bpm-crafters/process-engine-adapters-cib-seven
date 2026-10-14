package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import dev.bpmcrafters.processengineapi.testing.api.ProcessTestPayloadApi
import org.cibseven.community.rest.client.api.ProcessInstanceApi
import org.cibseven.community.rest.client.api.TaskLocalVariableApi
import org.cibseven.community.rest.client.dto.PatchVariablesDto
import org.springframework.beans.factory.getBean
import org.springframework.context.ConfigurableApplicationContext

private const val DESERIALIZE_ON_SERVER = false

internal class Cib7RemoteProcessTestPayloadApi(application: ConfigurableApplicationContext) : ProcessTestPayloadApi {

  private val processInstanceApi = application.getBean<ProcessInstanceApi>()
  private val taskLocalVariableApi = application.getBean<TaskLocalVariableApi>()
  private val valueMapper = application.getBean<ValueMapper>()

  override fun updateProcessPayload(instanceId: String, payload: Map<String, Any?>) {
    processInstanceApi.modifyProcessInstanceVariables(instanceId, PatchVariablesDto().modifications(valueMapper.mapValues(payload)))
  }

  override fun getProcessPayload(instanceId: String): Map<String, Any?> =
    valueMapper.mapDtos(variables = processInstanceApi.getProcessInstanceVariables(instanceId, DESERIALIZE_ON_SERVER), deserializeValues = true)

  override fun getTaskPayload(taskId: String): Map<String, Any?> =
    valueMapper.mapDtos(variables = taskLocalVariableApi.getTaskLocalVariables(taskId, DESERIALIZE_ON_SERVER), deserializeValues = true)
}
