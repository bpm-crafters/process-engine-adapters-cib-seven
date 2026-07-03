package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.deploy

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cibseven.bpm.engine.RepositoryService
import org.cibseven.bpm.engine.repository.Deployment
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Implementation for deployment API using repository service.
 */
class DeploymentApiImpl(
  private val repositoryService: RepositoryService,
  private val commandExecutor: EngineCommandExecutor,
  private val deployOnlyOnChange: Boolean = false
) : DeploymentApi {

  override fun deploy(cmd: DeployBundleCommand): CompletableFuture<DeploymentInformation> {
    require(cmd.resources.isNotEmpty()) { "Resources must not be empty, at least one resource must be provided." }
    logger.debug { "PROCESS-ENGINE-CIB7-EMBEDDED-003: executing a bundle deployment with ${cmd.resources.size} resources." }
    return commandExecutor.execute {
      repositoryService
        .createDeployment()
        .apply {
          cmd.resources.forEach { resource -> this.addInputStream(resource.name, resource.resourceStream) }
          if (deployOnlyOnChange) {
            this.enableDuplicateFiltering(true)
          }
        }
        .apply {
          if (!cmd.tenantId.isNullOrBlank()) {
            this.tenantId(cmd.tenantId)
          }
        }
        .deploy()
        .toDeploymentInformation()
    }
  }

  private fun Deployment.toDeploymentInformation() = DeploymentInformation(
    deploymentKey = this.id,
    tenantId = this.tenantId,
    deploymentTime = this.deploymentTime.toInstant()
  )

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

}
