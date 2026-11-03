package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.ExternalServiceTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.ServiceTasks
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.UserTaskDeliveryStrategy
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterProperties.UserTasks
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class CibSevenRemoteAdapterPropertiesTest {

  @Test
  fun `falls back to default seconds when no duration is set`() {
    assertThat(userTasks().scheduleDeliveryIntervalInMilliseconds).isEqualTo(5_000)
    assertThat(serviceTasks().scheduleDeliveryIntervalInMilliseconds).isEqualTo(13_000)
  }

  @Test
  fun `uses configured seconds when no duration is set`() {
    val tasks = userTasks().copy(scheduleDeliveryFixedRateInSeconds = 2)

    assertThat(tasks.scheduleDeliveryIntervalInMilliseconds).isEqualTo(2_000)
  }

  @Test
  fun `duration takes precedence over seconds`() {
    val rate = Duration.ofMillis(200)

    assertThat(userTasks().copy(scheduleDeliveryFixedRate = rate).scheduleDeliveryIntervalInMilliseconds).isEqualTo(200)
    assertThat(serviceTasks().copy(scheduleDeliveryFixedRate = rate).scheduleDeliveryIntervalInMilliseconds).isEqualTo(200)
  }

  @Test
  fun `rejects a duration below one millisecond`() {
    assertThatThrownBy { userTasks().copy(scheduleDeliveryFixedRate = Duration.ofNanos(500_000)) }
      .isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { serviceTasks().copy(scheduleDeliveryFixedRate = Duration.ZERO) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  private fun userTasks() = UserTasks(deliveryStrategy = UserTaskDeliveryStrategy.REMOTE_SCHEDULED)

  private fun serviceTasks() = ServiceTasks(
    workerId = "worker",
    deliveryStrategy = ExternalServiceTaskDeliveryStrategy.REMOTE_SCHEDULED,
  )
}
