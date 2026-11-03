package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.CibSevenEmbeddedAdapterProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.validation.annotation.Validated
import java.time.Duration

@ConfigurationProperties(prefix = DEFAULT_PREFIX)
@Validated
class CibSevenEmbeddedAdapterProperties(
  /**
   * Flag, controlling if the CIB-seven adapter is active.
   */
  val enabled: Boolean = true,
  /**
   * Configuration for external service tasks.
   */
  @NestedConfigurationProperty
  val serviceTasks: ServiceTasks,

  /**
   * Configuration of user tasks.
   */
  @NestedConfigurationProperty
  val userTasks: UserTasks,

  /**
   * Configuration of process deployment.
   */
  @NestedConfigurationProperty
  val deployment: Deployment = Deployment()
) {

  companion object {
    const val DEFAULT_PREFIX = "dev.bpm-crafters.process-api.adapter.cib-seven-embedded"
  }

  /**
   * Configuration for process deployment.
   */
  data class Deployment(
    /**
     * If `true`, unchanged resources are not redeployed as new versions.
     * Defaults to `false` to match the [CIB Seven] default
     */
    val deployOnlyOnChange: Boolean = false
  )

  /**
   * Configuration for user task handling.
   */
  data class UserTasks(
    /**
     * Delivery strategy for user tasks.
     */
    val deliveryStrategy: UserTaskDeliveryStrategy,
    /**
     * Fixed rate for scheduled user task delivery.
     */
    @get:DeprecatedConfigurationProperty(replacement = "$DEFAULT_PREFIX.user-tasks.schedule-delivery-fixed-rate")
    val scheduleDeliveryFixedRateInSeconds: Long = 5L,
    /**
     * Should an initial pull be executed on startup.
     */
    val executeInitialPullOnStartup: Boolean = true,
    /**
     * Delay between scheduled deliveries, e.g. `200ms`. Takes precedence over `schedule-delivery-fixed-rate-in-seconds`. Must be at least 1ms.
     */
    val scheduleDeliveryFixedRate: Duration? = null,
  ) {
    val scheduleDeliveryIntervalInMilliseconds: Long
      get() = (scheduleDeliveryFixedRate ?: Duration.ofSeconds(scheduleDeliveryFixedRateInSeconds)).toMillis()

    init {
      require(scheduleDeliveryFixedRate == null || scheduleDeliveryFixedRate.toMillis() > 0) { "schedule-delivery-fixed-rate must be at least 1ms, but was $scheduleDeliveryFixedRate." }
    }
  }


  /**
   * Configuration for external service task handling.
   */
  data class ServiceTasks(
    /**
     * Default id of the worker used for the external task.
     */
    val workerId: String,
    /**
     * Max count of external tasks to fetch. Defaults to 100.
     * A pull cycle never fetches more tasks than worker threads are free.
     */
    val maxTaskCount: Int = 100,
    /**
     * Time in seconds to lock external task. Default to 10.
     * Must exceed the runtime of the slowest worker, otherwise the task is delivered again (at-least-once).
     * Can be overridden per subscription with the `workerLockDurationInMilliseconds` restriction.
     */
    val lockTimeInSeconds: Long = 10L,
    /**
     * Retry timout in seconds.
     */
    val retryTimeoutInSeconds: Long = 10L,
    /**
     * Fixed rate for scheduled user task delivery.
     */
    @get:DeprecatedConfigurationProperty(replacement = "$DEFAULT_PREFIX.service-tasks.schedule-delivery-fixed-rate")
    val scheduleDeliveryFixedRateInSeconds: Long = 13L,
    /**
     * Delivery strategy for external service tasks.
     */
    val deliveryStrategy: ExternalServiceTaskDeliveryStrategy,
    /**
     * Should an initial pull be executed on startup.
     */
    val executeInitialPullOnStartup: Boolean = true,
    /**
     * Default initial number of retries.
     */
    val retries: Int = 3,
    /**
     * Delay between scheduled deliveries, e.g. `200ms`. Takes precedence over `schedule-delivery-fixed-rate-in-seconds`. Must be at least 1ms.
     */
    val scheduleDeliveryFixedRate: Duration? = null,
  ) {
    val scheduleDeliveryIntervalInMilliseconds: Long
      get() = (scheduleDeliveryFixedRate ?: Duration.ofSeconds(scheduleDeliveryFixedRateInSeconds)).toMillis()

    init {
      require(scheduleDeliveryFixedRate == null || scheduleDeliveryFixedRate.toMillis() > 0) { "schedule-delivery-fixed-rate must be at least 1ms, but was $scheduleDeliveryFixedRate." }
    }
  }

  /**
   * Strategy how the user tasks are delivered to subscriptions.
   */
  @Suppress("unused")
  enum class UserTaskDeliveryStrategy {
    /**
     * Delivery via scheduler.
     */
    EMBEDDED_SCHEDULED,

    /**
     * Custom delivery.
     */
    CUSTOM,

    /**
     * Disabled delivery
     */
    DISABLED
  }


  /**
   * Strategy how the external service tasks are delivered to subscriptions.
   */
  @Suppress("unused")
  enum class ExternalServiceTaskDeliveryStrategy {
    /**
     * Delivery via scheduler.
     */
    EMBEDDED_SCHEDULED,

    /**
     * Custom delivery.
     */
    CUSTOM,

    /**
     * Disabled delivery
     */
    DISABLED
  }
}
