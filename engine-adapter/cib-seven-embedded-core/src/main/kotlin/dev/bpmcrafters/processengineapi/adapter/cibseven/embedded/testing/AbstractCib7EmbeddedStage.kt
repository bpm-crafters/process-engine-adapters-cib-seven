package dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing

import com.tngtech.jgiven.Stage
import com.tngtech.jgiven.annotation.*
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.CorrelationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.correlation.SignalApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.CachingProcessDefinitionMetaDataResolver
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared.EngineCommandExecutor
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.Cib7ServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.Cib7UserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.completion.LinearMemoryFailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.delivery.pull.EmbeddedPullUserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.modification.Cib7UserTaskModificationApiImpl
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.task.subscription.Cib7TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.correlation.SignalApi
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource.Companion.fromClasspath
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.*
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import org.assertj.core.api.Assertions
import org.assertj.core.util.Lists
import org.awaitility.Awaitility
import org.cibseven.bpm.engine.ProcessEngineServices
import org.cibseven.bpm.engine.history.HistoricActivityInstance
import org.cibseven.bpm.engine.runtime.ProcessInstance
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests
import org.cibseven.bpm.engine.variable.VariableMap
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Abstract JGiven stage for implementing BDD tests operating on  CIB7 Embedded.
 * @param SUBTYPE type of your stage, subclassing this one.
 */
abstract class AbstractCib7EmbeddedStage<SUBTYPE : AbstractCib7EmbeddedStage<SUBTYPE>> : Stage<SUBTYPE>() {

  @ProvidedScenarioState
  private lateinit var restrictions: Map<String, String>

  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  protected lateinit var workerId: String

  @ProvidedScenarioState
  protected lateinit var userTaskSupport: UserTaskSupport

  @ProvidedScenarioState
  protected lateinit var startProcessApi: StartProcessApi

  @ProvidedScenarioState
  protected lateinit var userTaskCompletionApi: UserTaskCompletionApi

  @ProvidedScenarioState
  protected lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @ProvidedScenarioState
  protected lateinit var taskSubscriptionApi: TaskSubscriptionApi

  @ProvidedScenarioState
  protected lateinit var deploymentApi: DeploymentApi

  @ProvidedScenarioState
  protected lateinit var signalApi: SignalApi

  @ProvidedScenarioState
  protected lateinit var correlationApi: CorrelationApi

  @ProvidedScenarioState
  protected lateinit var userTaskModificationApi: UserTaskModificationApi

  @ProvidedScenarioState
  protected lateinit var processEngineServices: ProcessEngineServices

  @ProvidedScenarioState
  lateinit var embeddedPullUserTaskDelivery: EmbeddedPullUserTaskDelivery

  @ProvidedScenarioState
  private lateinit var embeddedPullServiceTaskDelivery: EmbeddedPullServiceTaskDelivery

  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  private lateinit var topicToExternalTaskId: MutableMap<String, String>

  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  private lateinit var topicToElementId: MutableMap<String, String>

  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  private lateinit var activeEvents: Set<String>

  @ProvidedScenarioState
  protected lateinit var taskInformation: TaskInformation

  @ExpectedScenarioState
  protected lateinit var processInstanceId: String


  /**
   * Initializes the engine. Should be called from a method of your test marked with `@BeforeEach`
   * to make sure, the engine is initialized early.
   * @param processEngineServices either process engine or process engine extension.
   * @param restrictions list of restrictions used in task subscription API. Usually, contains a restriction to the process definition key. Please use `CommonRestrictions` builder.
   */
  open fun initializeEngine(
    processEngineServices: ProcessEngineServices,
    restrictions: Map<String, String>
  ): SUBTYPE {
    this.topicToExternalTaskId = mutableMapOf()
    this.topicToElementId = mutableMapOf()
    this.activeEvents = mutableSetOf()

    this.restrictions = restrictions
    this.processEngineServices = processEngineServices
    this.workerId = self().javaClass.simpleName

    val subscriptionRepository = InMemSubscriptionRepository()
    val commandExecutor = EngineCommandExecutor(Executor { it.run() })

    startProcessApi = StartProcessApiImpl(
      runtimeService = processEngineServices.runtimeService,
      repositoryService = processEngineServices.repositoryService,
      commandExecutor = commandExecutor
    )
    deploymentApi = DeploymentApiImpl(
      repositoryService = processEngineServices.repositoryService,
      commandExecutor = commandExecutor
    )
    userTaskCompletionApi = Cib7UserTaskCompletionApiImpl(processEngineServices.taskService, subscriptionRepository)
    serviceTaskCompletionApi = Cib7ServiceTaskCompletionApiImpl(
      workerId, processEngineServices.externalTaskService, subscriptionRepository, LinearMemoryFailureRetrySupplier(3, 3L)
    )
    embeddedPullUserTaskDelivery = EmbeddedPullUserTaskDelivery(
      taskService = processEngineServices.taskService,
      processDefinitionMetaDataResolver = CachingProcessDefinitionMetaDataResolver(repositoryService = processEngineServices.repositoryService),
      subscriptionRepository = subscriptionRepository,
      executorService = Executors.newFixedThreadPool(1)
    )

    embeddedPullServiceTaskDelivery = EmbeddedPullServiceTaskDelivery(
      processEngineServices.externalTaskService,
      workerId, subscriptionRepository,
      Int.MAX_VALUE,
      1000,
      1000,
      1,
      Executors.newFixedThreadPool(1)
    )

    taskSubscriptionApi = Cib7TaskSubscriptionApiImpl(
      subscriptionRepository
    )

    userTaskModificationApi = Cib7UserTaskModificationApiImpl(processEngineServices.taskService)

    this.userTaskSupport = UserTaskSupport()
    userTaskSupport.subscribe(
      taskSubscriptionApi, restrictions, null, null
    )

    signalApi = SignalApiImpl(
      runtimeService = processEngineServices.runtimeService,
      commandExecutor = commandExecutor
    )
    correlationApi = CorrelationApiImpl(
      runtimeService = processEngineServices.runtimeService,
      commandExecutor = commandExecutor
    )

    initialize()

    // activate delivery
    embeddedPullUserTaskDelivery.refresh()
    embeddedPullServiceTaskDelivery.refresh()
    return self()
  }


  /**
   * Called after Process Engine and API are initialized.
   * This method is intended to be overwritten by the implementers of the stage in order to put stage initialization code in.
   */
  open fun initialize() {
  }

  /**
   * Asserts that a process instance is started.
   * @param processInstanceId instance id.
   * @return stage instance.
   */
  @As("process instance is stared $")
  open fun processIsStarted(processInstanceId: String) {
    this.processInstanceId = processInstanceId
    BpmnAwareTests.assertThat(getProcessInstance()).isStarted
  }

  @As("external task of type \$topicName exists")
  open fun externalTaskExists(@Quoted topicName: String, activityId: String?): SUBTYPE {
    taskSubscriptionApi.subscribeForTask(
      SubscribeForTaskCmd(
        restrictions,
        TaskType.EXTERNAL,
        topicName,
        null,
        { ti, _ ->
          run {
            topicToExternalTaskId[topicName] = ti.taskId
            topicToElementId[topicName] = ti.meta[CommonRestrictions.ACTIVITY_ID] as String
          }
        },
        { _ ->
          run {
            topicToExternalTaskId.remove(topicName)
            topicToElementId.remove(topicName)
          }
        })
    )
    Awaitility.await().untilAsserted {
      embeddedPullServiceTaskDelivery.refresh()
      Assertions.assertThat(topicToExternalTaskId.containsKey(topicName)).isTrue()
      if (activityId != null) {
        Assertions.assertThat(topicToElementId).containsEntry(topicName, activityId)
      }
    }
    return self()
  }

  @As("external task of type \$topicName is completed")
  open fun externalTaskIsCompleted(@Quoted topicName: String, variables: VariableMap): SUBTYPE {
    Objects.requireNonNull(
      topicToExternalTaskId[topicName], "No active external service task found, consider to assert using external_task_exists"
    )
    serviceTaskCompletionApi.completeTask(
      CompleteTaskCmd(
        topicToExternalTaskId.getValue(topicName)
      ) { variables }).get()
    return self()
  }

  @As("external task of type \$jobType is completed with error \$errorMessage")
  open fun externalTaskIsCompletedWithError(@Quoted topicName: String, errorMessage: String, variables: VariableMap): SUBTYPE {
    Objects.requireNonNull(
      topicToExternalTaskId[topicName], "No active external service task found, consider to assert using external_task_exists"
    )
    serviceTaskCompletionApi.completeTaskByError(
      CompleteTaskByErrorCmd(
        topicToExternalTaskId.getValue(topicName), errorMessage
      ) { variables }).get()
    return self()
  }

  @As("user tasks is assigned to user $")
  open fun taskIsAssignedToUser(@Quoted assignee: String): SUBTYPE {
    Assertions.assertThat(task().meta["assignee"]).isEqualTo(assignee)
    return self()
  }

  @As("process waits in $")
  open fun processWaitsIn(@Quoted taskDescriptionKey: String): SUBTYPE {
    // try to get the task
    Awaitility.await().untilAsserted {
      val taskIdOption = findTaskByActivityId(taskDescriptionKey)
      Assertions.assertThat(taskIdOption).describedAs("Process is not waiting in user task $taskDescriptionKey", taskDescriptionKey).isNotEmpty()
      taskIdOption.ifPresent { taskId -> this.taskInformation = userTaskSupport.getTaskInformation(taskId) }
    }
    return self()
  }

  @As("process waits in element $")
  open fun processWaitsInElement(@Quoted activityId: String): SUBTYPE {
    Awaitility.await().untilAsserted {
      val activeActivityIds = processEngineServices.runtimeService.getActiveActivityIds(this.processInstanceId)
      Assertions.assertThat(activeActivityIds)
        .describedAs("Process is not waiting in element $activityId", activityId)
        .contains(activityId)
    }
    return self()
  }

  open fun processContinues(elementId: String): SUBTYPE {
    Awaitility.await().untilAsserted {
      val job = BpmnAwareTests.job(elementId, getProcessInstance())
      Assertions.assertThat(job).isNotNull()
      BpmnAwareTests.execute(job)
    }
    return self()
  }

  open fun processIsDeployed(resource: String): SUBTYPE {
    deploymentApi.deploy(DeployBundleCommand(listOf(fromClasspath(resource)), null)).get()
    return self()
  }

  open fun processIsFinished(): SUBTYPE {
    val message = "Expecting %s to be ended, but it is not!. (Please " +
      "make sure you have set the history service of the engine to at least " +
      "'activity' or a higher level before making use of this assertion!)"
    Assertions.assertThat(processEngineServices.runtimeService.createProcessInstanceQuery().processInstanceId(this.processInstanceId).singleResult())
      .overridingErrorMessage(message, this.processInstanceId)
      .isNull()
    Assertions.assertThat(processEngineServices.historyService.createHistoricProcessInstanceQuery().processInstanceId(this.processInstanceId).singleResult())
      .overridingErrorMessage(message, this.processInstanceId)
      .isNotNull()
    return self()
  }

  /**
   * Checks if the process has passed a series of activities.
   * @param elementIds activities to pass.
   * @return stage.
   */
  open fun processHasPassed(vararg elementIds: String): SUBTYPE {
    hasPassed(activityIds = elementIds, inOrder = false, hasPassed = true)
    return self()
  }

  fun hasPassed(vararg activityIds: String, inOrder: Boolean, hasPassed: Boolean) {
    require(this::processInstanceId.isInitialized) { "You must initialize with process_is_started() before accessing it." }
    Assertions.assertThat(activityIds)
      .overridingErrorMessage(
        "Expecting list of activityIds not to be null, not to be empty and not to contain null values: %s.",
        Lists.newArrayList(*activityIds)
      )
      .isNotNull().isNotEmpty().doesNotContainNull()
    val finishedInstances: List<HistoricActivityInstance> = processEngineServices.historyService.createHistoricActivityInstanceQuery()
      .processInstanceId(this.processInstanceId)
      .finished()
      .orderByHistoricActivityInstanceEndTime().asc()
      .orderPartiallyByOccurrence().asc()
      .list()
    val finished: MutableList<String> = ArrayList(finishedInstances.size)
    for (instance in finishedInstances) {
      finished.add(instance.activityId)
    }
    val message = "Expecting %s " +
      (if (hasPassed)
        ("to have passed activities %s at least once" + (if (inOrder) " and in order" else "") + ", ")
      else
        "NOT to have passed activities %s, "
        ) +
      "but actually we found that it passed %s. (Please make sure you have set the history " +
      "service of the engine to at least 'activity' or a higher level before making use of this assertion!)"

    val assertion = Assertions.assertThat(finished)
      .overridingErrorMessage(
        message,
        this.processInstanceId,
        Lists.newArrayList(*activityIds),
        Lists.newArrayList(finished)
      )
    if (hasPassed) {
      assertion.contains(*activityIds)
      if (inOrder) {
        var remainingFinished: List<String> = finished
        for (activityId in activityIds) {
          Assertions.assertThat(remainingFinished)
            .overridingErrorMessage(
              message,
              this.processInstanceId,
              Lists.newArrayList(*activityIds),
              Lists.newArrayList(finished)
            )
            .contains(activityId)
          remainingFinished = remainingFinished.subList(remainingFinished.indexOf(activityId) + 1, remainingFinished.size)
        }
      }
    } else {
      assertion.doesNotContain(*activityIds)
    }
  }

  open fun task(): TaskInformation {
    return Objects.requireNonNull(taskInformation, "No task found, consider to assert using process_waits_in")
  }

  private fun getProcessInstance(): ProcessInstance {
    require(this::processInstanceId.isInitialized) { "You must initialize with process_is_started() before accessing it." }
    return BpmnAwareTests.processInstanceQuery().processInstanceId(processInstanceId).singleResult()
  }

  private fun findTaskByActivityId(activityId: String): Optional<String> {
    embeddedPullUserTaskDelivery.refresh()
    return Optional.ofNullable(
      userTaskSupport
        .getAllTasks()
        .find { ti: TaskInformation -> ti.meta[CommonRestrictions.ACTIVITY_ID] == activityId }?.taskId
    )
  }
}
