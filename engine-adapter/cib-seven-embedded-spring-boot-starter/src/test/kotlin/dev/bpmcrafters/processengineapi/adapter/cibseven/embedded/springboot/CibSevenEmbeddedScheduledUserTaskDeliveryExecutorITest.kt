package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskType
import dev.bpmcrafters.processengineapi.test.ProcessTestHelper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.cibseven.bpm.engine.RuntimeService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Import(CibSevenEmbeddedScheduledUserTaskDeliveryExecutorITest.NamedExecutorsConfiguration::class)
class CibSevenEmbeddedScheduledUserTaskDeliveryExecutorITest(
  @Autowired processTestHelperImpl: ProcessTestHelper,
  @Autowired private val runtimeService: RuntimeService,
) : AbstractCibSevenEmbeddedSpringApiITest(processTestHelperImpl) {

  @TestConfiguration(proxyBeanMethods = false)
  class NamedExecutorsConfiguration {

    @Bean("cib-seven-embedded-service-task-worker-pool")
    @Qualifier("cib-seven-embedded-service-task-worker-pool")
    fun serviceTaskWorkerPool(): ServiceTaskWorkerPool = ServiceTaskWorkerPool(namedExecutor("SERVICE-TASK-EXECUTOR"), maxConcurrentTasks = 2)

    @Bean("cib-seven-embedded-user-task-worker-executor")
    @Qualifier("cib-seven-embedded-user-task-worker-executor")
    fun userTaskWorkerExecutor(): ExecutorService = namedExecutor("USER-TASK-EXECUTOR")

    private fun namedExecutor(prefix: String): ExecutorService {
      val threadCounter = AtomicInteger()
      return Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "$prefix-${threadCounter.incrementAndGet()}")
      }
    }
  }

  @Test
  fun `should deliver user tasks on the user task executor`() {
    val handlerThreadName = AtomicReference<String>()
    processTestHelper.getTaskSubscriptionApi().subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = mapOf(CommonRestrictions.PROCESS_DEFINITION_KEY to KEY),
        taskType = TaskType.USER,
        taskDescriptionKey = USER_TASK,
        action = { _, _ -> handlerThreadName.compareAndSet(null, Thread.currentThread().name) },
        termination = {},
      )
    ).get()

    runtimeService.startProcessInstanceByKey(KEY)

    await().until { handlerThreadName.get() != null }
    assertThat(handlerThreadName.get()).startsWith("USER-TASK-EXECUTOR")
  }
}
