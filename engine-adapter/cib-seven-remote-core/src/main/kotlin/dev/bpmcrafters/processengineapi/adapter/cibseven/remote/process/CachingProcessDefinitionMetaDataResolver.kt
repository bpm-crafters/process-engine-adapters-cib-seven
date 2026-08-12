package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.process

import org.cibseven.community.rest.client.api.ProcessDefinitionApiClient

/**
 * Simple in-memory caching resolver for process definition for a given process definition id.
 */
data class CachingProcessDefinitionMetaDataResolver(
  val processDefinitionApiClient: ProcessDefinitionApiClient,
  private val keys: MutableMap<String, String> = mutableMapOf(),
  private val versionTags: MutableMap<String, String?> = mutableMapOf(),
  private val processDefinitionIds: MutableMap<Pair<String, String?>, String> = mutableMapOf()
) : ProcessDefinitionMetaDataResolver {

  override fun getProcessDefinitionKey(processDefinitionId: String?): String? {
    return if (processDefinitionId == null) {
      null
    } else {
      if (!keys.containsKey(processDefinitionId)) {
        fetchProcessByDefinitionId(processDefinitionId)
      }
      keys[processDefinitionId]
    }
  }

  override fun getProcessDefinitionVersionTag(processDefinitionId: String?): String? {
    return if (processDefinitionId == null) {
      null
    } else {
      if (!versionTags.containsKey(processDefinitionId)) {
        fetchProcessByDefinitionId(processDefinitionId)
      }
      versionTags[processDefinitionId]
    }
  }

  override fun getProcessDefinitionId(processDefinitionKey: String, tenantId: String?): String? {
    val keyWithTenant = processDefinitionKey to tenantId
    if (!processDefinitionIds.containsKey(keyWithTenant)) {
      fetchProcessByKeyAndTenant(processDefinitionKey, tenantId)
    }
    return processDefinitionIds[keyWithTenant]
  }

  private fun fetchProcessByKeyAndTenant(processDefinitionKey: String, tenantId: String?) {
    val result = if (tenantId != null) {
      processDefinitionApiClient.getLatestProcessDefinitionByTenantId(
        processDefinitionKey,
        tenantId
      )
    } else {
      processDefinitionApiClient.getProcessDefinitionByKey(
        processDefinitionKey
      )
    }
    val processDefinition = result
    processDefinitionIds[processDefinitionKey to tenantId] = processDefinition.id!!
    versionTags[processDefinition.id!!] = processDefinition.versionTag
    keys[processDefinition.id!!] = processDefinition.key!!
  }

  private fun fetchProcessByDefinitionId(processDefinitionId: String) {
    val definition = processDefinitionApiClient.getProcessDefinition(processDefinitionId)
    this.keys[processDefinitionId] = definition.key!!
    this.versionTags[processDefinitionId] = definition.versionTag
  }
}
