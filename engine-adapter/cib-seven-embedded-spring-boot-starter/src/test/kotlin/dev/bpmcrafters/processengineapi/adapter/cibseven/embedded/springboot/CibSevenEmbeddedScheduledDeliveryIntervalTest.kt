package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot

import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.schedule.CibSevenEmbeddedServiceTaskPullStrategyAutoConfiguration
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot.schedule.CibSevenEmbeddedUserTaskPullStrategyAutoConfiguration
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.TaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

@SpringBootTest(
  properties = [
    "dev.bpm-crafters.process-api.adapter.cib-seven-embedded.service-tasks.schedule-delivery-fixed-rate = 200ms",
    "dev.bpm-crafters.process-api.adapter.cib-seven-embedded.user-tasks.schedule-delivery-fixed-rate = 300ms"
  ]
)
@ActiveProfiles("itest")
@DirtiesContext
class CibSevenEmbeddedScheduledDeliveryIntervalTest {

  @Autowired
  lateinit var context: ApplicationContext

  @MockitoBean(name = "cib-seven-embedded-task-scheduler")
  lateinit var taskScheduler: TaskScheduler

  @Test
  fun `starts and stops scheduled pulls with the configured sub-second delay`() {
    val scheduledServiceTaskDelivery = mock(ScheduledFuture::class.java)
    val scheduledUserTaskDelivery = mock(ScheduledFuture::class.java)
    clearInvocations(taskScheduler)
    `when`(taskScheduler.scheduleWithFixedDelay(any(Runnable::class.java), any(Instant::class.java), eq(Duration.ofMillis(200))))
      .thenReturn(scheduledServiceTaskDelivery)
    `when`(taskScheduler.scheduleWithFixedDelay(any(Runnable::class.java), any(Instant::class.java), eq(Duration.ofMillis(300))))
      .thenReturn(scheduledUserTaskDelivery)

    context.getBean(CibSevenEmbeddedServiceTaskPullStrategyAutoConfiguration::class.java).start()
    context.getBean(CibSevenEmbeddedUserTaskPullStrategyAutoConfiguration::class.java).start()

    verify(taskScheduler).scheduleWithFixedDelay(any(), any(), eq(Duration.ofMillis(200)))
    verify(taskScheduler).scheduleWithFixedDelay(any(), any(), eq(Duration.ofMillis(300)))

    context.getBean(CibSevenEmbeddedServiceTaskPullStrategyAutoConfiguration::class.java).stop()
    context.getBean(CibSevenEmbeddedUserTaskPullStrategyAutoConfiguration::class.java).stop()

    verify(scheduledServiceTaskDelivery).cancel(false)
    verify(scheduledUserTaskDelivery).cancel(false)
  }
}
