package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.springboot

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.common.task.ServiceTaskWorkerPool
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.ExperimentalTaskExecutionDecoratorApi
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecution
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskExecutionDecorator
import dev.bpmcrafters.processengineapi.adapter.cibseven.experimental.decorator.TaskOutcome
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalTaskExecutionDecoratorApi::class)
@Import(CibSevenEmbeddedServiceTaskWorkerPoolITest.CustomWorkerPoolAndDecoratorConfiguration::class)
class CibSevenEmbeddedServiceTaskWorkerPoolITest(
  @Autowired processTestHelperImpl: ProcessTestHelper,
  @Autowired private val runtimeService: RuntimeService,
  @Autowired private val recordingDecorator: RecordingDecorator,
) : AbstractCibSevenEmbeddedSpringApiITest(processTestHelperImpl) {

  companion object {
    const val CUSTOM_THREAD_NAME = "CUSTOM-SERVICE-TASK-EXECUTOR"
  }

  class RecordingDecorator : TaskExecutionDecorator {
    val outcomes = CopyOnWriteArrayList<TaskOutcome>()

    override fun decorate(task: TaskInformation, payload: Map<String, Any?>, execution: TaskExecution) {
      outcomes.add(execution.proceed())
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  class CustomWorkerPoolAndDecoratorConfiguration {

    @Bean("cib-seven-embedded-service-task-worker-pool")
    @Qualifier("cib-seven-embedded-service-task-worker-pool")
    fun serviceTaskWorkerPool(): ServiceTaskWorkerPool {
      val customExecutor = Executor { runnable -> Thread(runnable, CUSTOM_THREAD_NAME).start() }
      return ServiceTaskWorkerPool(customExecutor, maxConcurrentTasks = 5)
    }

    @Bean
    fun recordingDecorator() = RecordingDecorator()
  }

  @Test
  fun `should execute service tasks on the custom worker pool and pass the outcome to the decorator`() {
    val handlerThreadName = AtomicReference<String>()
    processTestHelper.getTaskSubscriptionApi().subscribeForTask(
      SubscribeForTaskCmd(
        restrictions = mapOf(CommonRestrictions.PROCESS_DEFINITION_KEY to KEY),
        taskType = TaskType.EXTERNAL,
        taskDescriptionKey = EXTERNAL_TASK,
        action = { task, _ ->
          handlerThreadName.set(Thread.currentThread().name)
          processTestHelper.getServiceTaskCompletionApi().completeTask(CompleteTaskCmd(task.taskId) { emptyMap() }).get()
        },
        termination = {},
      )
    ).get()

    runtimeService.startProcessInstanceByKey(KEY)

    await().untilAsserted { assertThat(recordingDecorator.outcomes).containsExactly(TaskOutcome.Completed) }
    assertThat(handlerThreadName.get()).isEqualTo(CUSTOM_THREAD_NAME)
  }
}
