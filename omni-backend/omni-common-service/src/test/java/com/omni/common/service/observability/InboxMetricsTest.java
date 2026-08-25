package com.omni.common.service.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Inbox 指标禁止高基数标签的契约测试。 */
class InboxMetricsTest {

    @Test
    void shouldExposeOnlyDestinationAndResultTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            InboxMetrics.record("workflow-completed", "retry");
            Meter meter = registry.get("omni.inbox.operations")
                    .tags("destination", "workflow-completed", "result", "retry").meter();

            assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                    .containsExactly("destination", "result");
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }
}
