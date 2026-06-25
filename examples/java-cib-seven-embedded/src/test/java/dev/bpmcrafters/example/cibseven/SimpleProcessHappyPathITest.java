package dev.bpmcrafters.example.cibseven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.bpmcrafters.example.common.adapter.shared.SimpleProcessWorkflowConst.Elements;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cibseven.bpm.engine.ProcessEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * End-to-end regression test for the embedded CIB seven adapter on Spring Boot 4.
 *
 * <p>Boots the full example application (embedded engine + webapp + adapter auto-configuration) on
 * a random port and drives the <em>real</em> happy path over HTTP — the same calls as the
 * {@code simple-process-demo.http} / Bruno demo: deploy → start → wait for the user task delivered
 * by the scheduled-pull strategy → complete it → correlate the message → assert the instance
 * reaches the {@code finished} end event.
 *
 * <p>Unlike {@code SimpleProcessTest} (which completes the service task with a hand-built payload on
 * an in-memory engine), this test runs the production {@code ExecuteActionTaskHandler}, which emits
 * a POJO process variable. That requires a JSON dataformat on the classpath
 * ({@code cibseven-spin-dataformat-json-jackson}); without it the service task fails with
 * "Cannot find serializer" and the user task is never created — so this test guards that wiring.
 *
 * <p>The embedded delivery rate is lowered to 1s (from the application default of 10s) purely to
 * keep the test fast and deterministic.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "dev.bpm-crafters.process-api.adapter.cib-seven-embedded.service-tasks.schedule-delivery-fixed-rate-in-seconds=1",
      "dev.bpm-crafters.process-api.adapter.cib-seven-embedded.user-tasks.schedule-delivery-fixed-rate-in-seconds=1"
    })
class SimpleProcessHappyPathITest {

  private static final String BASE = "/simple-service-tasks";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Value("${local.server.port}")
  private int port;

  @Autowired private ProcessEngine processEngine;

  private RestClient client;

  @BeforeEach
  void setUp() {
    client = RestClient.create("http://localhost:" + port);
  }

  @Test
  void happy_path_runs_end_to_end_through_the_adapter() {
    // 1. deploy the process via the DeploymentApi
    ResponseEntity<Void> deploy =
        client.post().uri(BASE + "/deploy").retrieve().toBodilessEntity();
    assertThat(deploy.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // 2. start an instance via the StartProcessApi. intValue=1 takes the happy path, where the
    //    service-task handler returns a POJO variable that must be JSON-serialized by the engine.
    String correlationKey = UUID.randomUUID().toString();
    ResponseEntity<Void> start =
        client
            .post()
            .uri(BASE + "/start-process?value={v}&intValue=1", correlationKey)
            .retrieve()
            .toBodilessEntity();
    assertThat(start.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(start.getHeaders().getLocation()).isNotNull();
    String instanceId = start.getHeaders().getLocation().toString();

    // 3. the user task only appears once the service task completed successfully (= serialization
    //    worked) and the user-task pull strategy delivered it. This is the core assertion.
    String taskId = await().atMost(TIMEOUT).until(this::firstUserTaskId, id -> id != null);

    // 4. complete the user task via the UserTaskCompletionApi
    ResponseEntity<Void> complete =
        client
            .post()
            .uri(BASE + "/tasks/{id}/complete?value=done", taskId)
            .retrieve()
            .toBodilessEntity();
    assertThat(complete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 5. correlate the waiting message via the CorrelationApi. Retry until the instance has
    //    advanced (through the send-message service task) to the message catch event.
    await()
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                        .post()
                        .uri(BASE + "/correlate/{k}?value=msg", correlationKey)
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                    == HttpStatus.NO_CONTENT);

    // 6. the instance must have reached the happy-path end event "finished"
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        processEngine
                            .getHistoryService()
                            .createHistoricActivityInstanceQuery()
                            .processInstanceId(instanceId)
                            .activityId(Elements.END_EVENT)
                            .finished()
                            .count())
                    .as(
                        "process instance %s reached the '%s' end event",
                        instanceId, Elements.END_EVENT)
                    .isEqualTo(1));
  }

  private String firstUserTaskId() {
    List<Map<String, Object>> tasks =
        client
            .get()
            .uri(BASE + "/tasks")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    if (tasks == null || tasks.isEmpty()) {
      return null;
    }
    Object taskId = tasks.get(0).get("taskId");
    return taskId == null ? null : taskId.toString();
  }
}
