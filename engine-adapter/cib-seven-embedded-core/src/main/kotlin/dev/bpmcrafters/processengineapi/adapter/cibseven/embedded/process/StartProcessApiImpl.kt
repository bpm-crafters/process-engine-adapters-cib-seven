package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.applyTenantRestrictions
import dev.bpmcrafters.processengineapi.process.*
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
) : StartProcessApi {

  override fun startProcess(cmd: StartProcessCommand): CompletableFuture<ProcessInformation> {
    return when (cmd) {
      is StartProcessByDefinitionCmd ->
        CompletableFuture.supplyAsync {
          logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-004: starting a new process instance by definition ${cmd.definitionKey}." }
          ensureSupported(cmd.restrictions)
          val payload = cmd.payloadSupplier.get()
          val businessKey = payload[CommonRestrictions.BUSINESS_KEY]?.toString()
          val tenantId = cmd.restrictions[CommonRestrictions.TENANT_ID]
          if (!tenantId.isNullOrBlank()) {
            repositoryService
              .createProcessDefinitionQuery()
              .processDefinitionKey(cmd.definitionKey)
              .tenantIdIn(tenantId)
              .active()
              .latestVersion()
              .singleResult()?.let { processDefinition ->
                runtimeService.startProcessInstanceById(
                  processDefinition.id,
                  businessKey,
                  payload,
                ).toProcessInformation()
              }
          } else {
            runtimeService.startProcessInstanceByKey(
              cmd.definitionKey,
              businessKey,
              payload,
            ).toProcessInformation()
          }
        }

      is StartProcessByMessageCmd ->
        CompletableFuture.supplyAsync {
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
        CompletableFuture.supplyAsync {
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
