package com.omni.common.job;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** XXL-JOB 指标标签基数契约测试。 */
class JobMetricsTest {

    @Test
    void shouldExposeOnlyFixedResultTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            JobMetrics.recordRegistration("failure");
            Meter meter = registry.get("omni.job.registrations").tag("result", "failure").meter();

            assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                    .containsExactly("result");
            assertThat(registry.get("omni.job.registrations")
                    .tag("result", "failure").counter().count()).isEqualTo(1D);
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }
}
