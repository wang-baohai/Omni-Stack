package com.omni.workflow.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Workflow 指标结果和积压 Gauge 契约测试。 */
class WorkflowMetricsTest {

    @Test
    void shouldExposeBacklogAndFixedResultTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            TaskService taskService = mock(TaskService.class);
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.active()).thenReturn(taskQuery);
            when(taskQuery.count()).thenReturn(3L);
            new WorkflowMetrics(registry, taskService);

            WorkflowMetrics.recordStart("failure");
            WorkflowMetrics.recordApproval("success", 1_000_000L);

            assertThat(registry.get("omni.workflow.approval.backlog").gauge().value()).isEqualTo(3D);
            Meter start = registry.get("omni.workflow.start.operations")
                    .tag("result", "failure").meter();
            assertThat(start.getId().getTags()).extracting(tag -> tag.getKey())
                    .containsExactly("result");
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }
}
