package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.applyTenantRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionAtElementCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageAtElementCmd
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import dev.bpmcrafters.processengineapi.process.StartProcessCommand
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.RepositoryService
import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.runtime.ProcessInstance
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Implementation of a start proces sapi using runtime service.
 */
class StartProcessApiImpl(
  private val runtimeService: RuntimeService,
  private val repositoryService: RepositoryService,
  private val commandExecutor: EngineCommandExecutor
) : StartProcessApi {

  override fun startProcess(cmd: StartProcessCommand): CompletableFuture<ProcessInformation> {
    return when (cmd) {
      is StartProcessByDefinitionCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-004: starting a new process instance by definition ${cmd.definitionKey}." }
          ensureSupported(cmd.restrictions)
          val payload = cmd.payloadSupplier.get()
          val businessKey = payload[CommonRestrictions.BUSINESS_KEY]?.toString()
          val tenantId = cmd.restrictions[CommonRestrictions.TENANT_ID]
          if (!tenantId.isNullOrBlank()) {
            val processDefinition = repositoryService
              .createProcessDefinitionQuery()
              .processDefinitionKey(cmd.definitionKey)
              .tenantIdIn(tenantId)
              .active()
              .latestVersion()
              .singleResult()
            requireNotNull(processDefinition) { "No process definition found for key ${cmd.definitionKey} and tenant $tenantId" }
            runtimeService.startProcessInstanceById(
              processDefinition.id,
              businessKey,
              payload,
            ).toProcessInformation()
          } else {
            runtimeService.startProcessInstanceByKey(
              cmd.definitionKey,
              businessKey,
              payload,
            ).toProcessInformation()
          }
        }

      is StartProcessByMessageCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-005: starting a new process instance by message ${cmd.messageName}." }
          val payload = cmd.payloadSupplier.get()
          var correlationBuilder = runtimeService.createMessageCorrelation(cmd.messageName)
          val businessKey = payload[CommonRestrictions.BUSINESS_KEY]
          if (businessKey != null) {
            correlationBuilder = correlationBuilder.processInstanceBusinessKey(businessKey.toString())
          }
          correlationBuilder
            .applyTenantRestrictions(ensureSupported(cmd.restrictions))
            .setVariables(payload)
            .correlateStartMessage()
            .toProcessInformation()
        }

      is StartProcessByDefinitionAtElementCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-006: starting a new process instance by definition ${cmd.definitionKey} at element ${cmd.elementId}" }
          val startProcessCommand = StartProcessByDefinitionCmd(
            definitionKey = cmd.definitionKey,
            payloadSupplier = cmd.payloadSupplier,
            restrictions = cmd.restrictions,
          )
          val instance = this.startProcess(startProcessCommand).get()
          val processDefinitionId = instance.meta[CommonRestrictions.PROCESS_DEFINITION_KEY] as String
          runtimeService.createModification(processDefinitionId)
            .processInstanceIds(instance.instanceId)
            .startBeforeActivity(cmd.elementId)
            .execute()
          instance
        }

      is StartProcessByMessageAtElementCmd ->
        commandExecutor.execute {
          logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-015: starting a new process instance by message ${cmd.messageName} at element ${cmd.elementId}" }
          val startProcessCommand = StartProcessByMessageCmd(
            messageName = cmd.messageName,
            payloadSupplier = cmd.payloadSupplier,
            restrictions = cmd.restrictions,
          )
          val instance = this.startProcess(startProcessCommand).get()
          val processDefinitionId = instance.meta[CommonRestrictions.PROCESS_DEFINITION_KEY] as String
          runtimeService.createModification(processDefinitionId)
            .processInstanceIds(instance.instanceId)
            .startBeforeActivity(cmd.elementId)
            .execute()
          instance
        }

      else -> throw IllegalArgumentException("Unsupported start command $cmd")
    }
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO()
  }

  override fun getSupportedRestrictions(): Set<String> = setOf(
    CommonRestrictions.TENANT_ID,
    CommonRestrictions.WITHOUT_TENANT_ID,
  )
}

fun ProcessInstance.toProcessInformation() = ProcessInformation(
  instanceId = this.id,
  meta = mapOf(
    CommonRestrictions.PROCESS_DEFINITION_KEY to this.processDefinitionId,
    CommonRestrictions.BUSINESS_KEY to this.businessKey,
    CommonRestrictions.TENANT_ID to this.tenantId,
    "rootProcessInstanceId" to this.rootProcessInstanceId
  )
)
