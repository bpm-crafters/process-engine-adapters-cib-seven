package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.decision

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.serialization.AdapterDataConverter
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.decision.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.community.rest.client.api.DecisionDefinitionApiClient
import org.cibseven.community.rest.client.model.EvaluateDecisionDto
import org.cibseven.community.rest.client.model.VariableValueDto
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.variables.ValueMapper
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class EvaluateDecisionApiImpl(
  private val decisionDefinitionApiClient: DecisionDefinitionApiClient,
  private val valueMapper: ValueMapper,
  private val dataConverter: AdapterDataConverter
) : EvaluateDecisionApi {

  override fun evaluateDecision(command: DecisionEvaluationCommand): CompletableFuture<DecisionEvaluationResult> {
    when (command) {
      is DecisionByRefEvaluationCommand -> {
        logger.debug {
          "PROCESS-ENGINE-C7-REMOTE-061: Evaluating decision by reference ${command.decisionRef}"
        }
        return CompletableFuture.supplyAsync {

          val restrictions = command.restrictionSupplier.get()

          val tenantId = if (restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
            require(!restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
              "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
                "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive."
            }
            restrictions[CommonRestrictions.TENANT_ID]
          } else if (restrictions.containsKey(CommonRestrictions.WITHOUT_TENANT_ID)) {
            require(!restrictions.containsKey(CommonRestrictions.TENANT_ID)) {
              "Illegal restriction combination. ${CommonRestrictions.TENANT_ID} " +
                "and ${CommonRestrictions.WITHOUT_TENANT_ID} can't be provided in the same time because they are mutually exclusive."
            }
            null
          } else {
            null
          }

          val variables = valueMapper.mapValues(command.payloadSupplier.get())
          val result = if (tenantId != null) {
            decisionDefinitionApiClient
              .evaluateDecisionByKeyAndTenant(
                command.decisionRef,
                tenantId,
                EvaluateDecisionDto().variables(variables)
              )
          } else {
            decisionDefinitionApiClient
              .evaluateDecisionByKey(
                command.decisionRef,
                EvaluateDecisionDto().variables(variables)
              )
          }
          result.toResult()
        }
      }

      else -> throw UnsupportedOperationException("Evaluate Decision command of type ${command::class.qualifiedName} is not implemented yet")
    }

  }

  private fun List<Map<String, VariableValueDto>>.toResult(): DecisionEvaluationResult {
    return if (this.isEmpty()) {
      NoDecisionResult
    } else {
      DelegatingDmnDecisionResult(this.map { valueMapper.mapDtos(it) }, dataConverter)
    }
  }

  override fun getSupportedRestrictions(): Set<String> {
    return setOf(
      CommonRestrictions.TENANT_ID,
      CommonRestrictions.WITHOUT_TENANT_ID,
    )
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }
}
