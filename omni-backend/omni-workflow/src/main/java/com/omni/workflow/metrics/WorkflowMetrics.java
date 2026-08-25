package com.omni.workflow.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Workflow 低基数业务观测指标。
 *
 * <p>只使用固定 result 标签；流程实例、业务键、租户和处理人仅允许进入日志，
 * 不进入 Metrics 标签。</p>
 */
@Component
public class WorkflowMetrics {

    private static final Map<String, Counter> START_COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Counter> APPROVAL_COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Timer> APPROVAL_TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, Timer> PROCESS_TIMERS = new ConcurrentHashMap<>();

    /**
     * 注册当前全部活跃审批任务积压量。
     *
     * @param registry 指标注册表
     * @param taskService Flowable 任务服务
     */
    public WorkflowMetrics(MeterRegistry registry, TaskService taskService) {
        Gauge.builder("omni.workflow.approval.backlog", taskService, WorkflowMetrics::activeTaskCount)
                .description("Current active Workflow approval task backlog")
                .register(registry);
    }

    /** 记录流程启动结果。 */
    public static void recordStart(String result) {
        START_COUNTERS.computeIfAbsent(result, key -> Counter.builder("omni.workflow.start.operations")
                        .description("Workflow process start outcomes")
                        .tag("result", key)
                        .register(Metrics.globalRegistry))
                .increment();
    }

    /** 记录一次审批操作及服务端处理耗时。 */
    public static void recordApproval(String result, long elapsedNanos) {
        APPROVAL_COUNTERS.computeIfAbsent(result, key -> Counter.builder("omni.workflow.approval.operations")
                        .description("Workflow approval operation outcomes")
                        .tag("result", key)
                        .register(Metrics.globalRegistry))
                .increment();
        APPROVAL_TIMERS.computeIfAbsent(result, key -> Timer.builder("omni.workflow.approval.duration")
                        .description("Workflow approval operation duration")
                        .tag("result", key)
                        .publishPercentileHistogram()
                        .register(Metrics.globalRegistry))
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /** 记录从流程创建到完成的端到端耗时。 */
    public static void recordProcessCompletion(String result, Duration duration) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        PROCESS_TIMERS.computeIfAbsent(result, key -> Timer.builder("omni.workflow.process.duration")
                        .description("Workflow process end-to-end duration")
                        .tag("result", key)
                        .publishPercentileHistogram()
                        .register(Metrics.globalRegistry))
                .record(duration);
    }

    private static double activeTaskCount(TaskService taskService) {
        try {
            return taskService.createTaskQuery().active().count();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }
}
