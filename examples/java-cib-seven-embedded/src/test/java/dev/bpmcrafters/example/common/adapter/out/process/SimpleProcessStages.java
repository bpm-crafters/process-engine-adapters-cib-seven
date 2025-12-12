package dev.bpmcrafters.example.common.adapter.out.process;

import com.tngtech.jgiven.annotation.As;
import com.tngtech.jgiven.annotation.ProvidedScenarioState;
import com.tngtech.jgiven.annotation.Quoted;
import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements;
import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Expressions;
import dev.bpmcrafters.example.common.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.example.common.application.port.out.WorkflowOutPort;
import dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.testing.AbstractCib7EmbeddedStage;
import org.cibseven.bpm.engine.variable.VariableMap;
import org.cibseven.bpm.engine.variable.Variables;

public class SimpleProcessStages {

  static class ActionStage extends AbstractCib7EmbeddedStage<ActionStage> {

    @ProvidedScenarioState
    private WorkflowOutPort workflowOutPort;
    private UserTaskOutPort userTaskOutPort;
    private String correlationKey;

    @Override
    public void initialize() {
      workflowOutPort = new WorkflowAdapter(
        startProcessApi,
        signalApi,
        correlationApi,
        deploymentApi
      );

      userTaskOutPort = new UserTaskAdapter(
        userTaskSupport,
        userTaskCompletionApi
      );
    }

    @As("simple process started with value $value and intValue $intValue")
    public ActionStage simple_process_started(@Quoted String value, @Quoted Integer intValue) {
      String instanceId = workflowOutPort.startSimpleProcess(value, intValue);
      processIsStarted(instanceId); // sets and init the process instance id or later process instance checks
      this.correlationKey = value;
      return self();
    }

    @As("service task execute action completed with $value")
    public ActionStage service_execute_action_is_completed(@Quoted String value) {
      VariableMap payload = Variables.createVariables();
      payload.put("action1", value);

      return externalTaskExists(Expressions.JOB_TYPE_EXECUTE_ACTION_EXTERNAL, Elements.SERVICE_TASK_DO_ACTION_1)
        .and()
        .externalTaskIsCompleted(Expressions.JOB_TYPE_EXECUTE_ACTION_EXTERNAL, payload);
    }

    @As("service task execute action completed with error")
    public ActionStage service_execute_action_is_completed_with_error() {
      return externalTaskExists(Expressions.JOB_TYPE_EXECUTE_ACTION_EXTERNAL, Elements.SERVICE_TASK_DO_ACTION_1)
        .and()
        .externalTaskIsCompletedWithError(Expressions.JOB_TYPE_EXECUTE_ACTION_EXTERNAL, Expressions.ERROR_ACTION_ERROR, Variables.createVariables());
    }

    @As("user task perform task completed with $value")
    public ActionStage user_task_perform_task_is_completed(String value) {
      processWaitsIn(Elements.USER_TASK_PERFORM_TASK);
      userTaskOutPort.complete(task().getTaskId(), value);
      return self();
    }

    @As("user task perform task is timed out")
    public ActionStage user_task_perform_task_is_timed_out() {
      processWaitsIn(Elements.USER_TASK_PERFORM_TASK);
      processContinues(Elements.TIMER_PASSED);
      return self();
    }

    @As("user task perform task is complete with an error with $value")
    public ActionStage user_task_perform_task_is_completed_with_error(String value) {
      processWaitsIn(Elements.USER_TASK_PERFORM_TASK);
      userTaskOutPort.completeWithError(task().getTaskId(), value);
      return self();
    }

    @As("service task message send completed")
    public ActionStage service_send_email_is_completed() {
      return externalTaskExists(Expressions.JOB_TYPE_SEND_MESSAGE_EXTERNAL, Elements.SERVICE_TASK_DO_ACTION_2)
        .and()
        .externalTaskIsCompleted(Expressions.JOB_TYPE_SEND_MESSAGE_EXTERNAL, Variables.createVariables());
    }

    @As("message received with $value")
    public ActionStage message_received(String value) {
      processWaitsInElement(Elements.EVENT_RECEIVED_MESSAGE);
      workflowOutPort.correlateMessage(correlationKey, value);
      return self();
    }

    @As("signal occurred")
    public ActionStage signal_occurred() {
      processWaitsInElement(Elements.EVENT_SIGNAL_OCCURRED);
      workflowOutPort.deliverSignal(correlationKey);
      return self();
    }
  }

  static class AssertStage extends AbstractCib7EmbeddedStage<AssertStage> {

  }
}
