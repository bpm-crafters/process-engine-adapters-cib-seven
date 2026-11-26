package dev.bpmcrafters.example.cibseven;

import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.springboot.CibSevenRemoteAdapterAutoConfiguration;
import dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.delivery.pull.PullServiceTaskDeliveryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTaskDeliveryMetricsTest {

  @Test
  void remoteMetricsAreRecordedInTheAutoConfiguredRegistry() {
    try (var context = metricsContext()) {
      context.getBean(PullServiceTaskDeliveryMetrics.class).incrementFetchedAndLockedTasksCounter("example", 2);

      assertThat(context.getBean(MeterRegistry.class)
        .get("bpmcrafters.c7.remote.scheduled.external.tasks.fetched.and.locked")
        .tag("topic", "example").counter().count()).isEqualTo(2);
    }
  }

  private AnnotationConfigApplicationContext metricsContext() {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("adapter-metrics", Map.of(
      "dev.bpm-crafters.process-api.adapter.cib-seven-remote.enabled", true
    )));
    // Evaluate the real auto-configuration conditions without connecting to a CIB seven engine.
    context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
    context.register(MetricsConfiguration.class);
    context.refresh();
    return context;
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({
    CibSevenRemoteAdapterAutoConfiguration.class,
    MetricsAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class,
    CompositeMeterRegistryAutoConfiguration.class
  })
  static class MetricsConfiguration {
  }
}
