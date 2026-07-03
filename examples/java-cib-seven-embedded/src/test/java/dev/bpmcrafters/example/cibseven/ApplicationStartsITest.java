package dev.bpmcrafters.example.cibseven;

import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test booting the full Spring Boot application including the embedded CIB seven engine
 * and the adapter auto-configuration.
 */
@SpringBootTest
class ApplicationStartsITest {

  @Autowired
  private StartProcessApi startProcessApi;

  @Autowired
  private TaskSubscriptionApi taskSubscriptionApi;

  @Test
  void adapter_apis_are_auto_configured() {
    assertThat(startProcessApi).isNotNull();
    assertThat(taskSubscriptionApi).isNotNull();
  }
}
