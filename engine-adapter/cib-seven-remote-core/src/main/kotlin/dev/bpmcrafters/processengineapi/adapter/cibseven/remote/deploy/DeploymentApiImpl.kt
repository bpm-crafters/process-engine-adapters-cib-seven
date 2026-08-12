package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.deploy

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.cibseven.community.rest.client.invoker.ApiClient
import org.cibseven.community.rest.client.model.DeploymentWithDefinitionsDto
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

/**
 * Creates deployments over the CIB seven REST API.
 *
 * The generated `DeploymentApiClient.createDeployment` only accepts a single `File`, which cannot
 * represent a multi-resource bundle deployment. We therefore build the `multipart/form-data` request
 * by hand — one `data` part per resource — and execute it with the [ApiClient]'s configured HTTP
 * client, so base path, authentication and interceptors stay consistent with the rest of the adapter.
 */
class DeploymentApiImpl(
  private val apiClient: ApiClient
) : DeploymentApi {

  override fun deploy(cmd: DeployBundleCommand): CompletableFuture<DeploymentInformation> {
    require(cmd.resources.isNotEmpty()) { "Resources must not be empty, at least one resource must be provided." }
    logger.debug { "PROCESS-ENGINE-C7-REMOTE-003: executing a bundle deployment with ${cmd.resources.size} resources." }
    return CompletableFuture.supplyAsync {
      val tenantId = cmd.tenantId?.takeUnless { it.isBlank() }

      val entityBuilder = MultipartEntityBuilder.create()
        .addTextBody("deployment-name", "ProcessEngineApiRemote")
        .addTextBody("enable-duplicate-filtering", "true")
        .addTextBody("deploy-changed-only", "false")
      tenantId?.let { entityBuilder.addTextBody("tenant-id", it) }
      cmd.resources.forEach { resource ->
        entityBuilder.addBinaryBody(resource.name, resource.resourceStream.readBytes(), ContentType.DEFAULT_BINARY, resource.name)
      }

      val post = HttpPost("${apiClient.basePath}/deployment/create").apply {
        entity = entityBuilder.build()
      }

      apiClient.httpClient.execute(post) { response ->
        val payload = response.entity?.let { EntityUtils.toString(it) }
        check(response.code in 200..299) {
          "Could not create deployment, resulting status was ${response.code}: $payload"
        }
        apiClient.objectMapper.readValue(payload, DeploymentWithDefinitionsDto::class.java)
      }.toDeploymentInformation()
    }
  }

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }

  private fun DeploymentWithDefinitionsDto.toDeploymentInformation() = DeploymentInformation(
    deploymentKey = this.id!!,
    deploymentTime = this.deploymentTime!!.toInstant(),
    tenantId = this.tenantId
  )
}
