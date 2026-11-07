package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.client

import org.assertj.core.api.Assertions.assertThat
import org.cibseven.community.rest.client.dto.TaskDto
import org.cibseven.community.rest.client.invoker.ApiClient
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class EngineDateFormatModuleTest {

  private val objectMapper = ApiClient().objectMapper.registerModule(EngineDateFormatModule())

  @Test
  fun `writes dates in the engine date format`() {
    val task = TaskDto().followUp(OffsetDateTime.parse("2026-11-07T16:51:27.041+01:00"))

    val json = objectMapper.writeValueAsString(task)

    assertThat(json).contains("\"followUp\":\"2026-11-07T16:51:27.041+0100\"")
  }

  @Test
  fun `writes cleared dates as null`() {
    val json = objectMapper.writeValueAsString(TaskDto().due(null))

    assertThat(json).contains("\"due\":null")
  }
}
