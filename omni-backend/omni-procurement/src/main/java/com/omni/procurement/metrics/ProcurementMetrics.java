package com.omni.procurement.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采购模块低基数业务指标。
 */
public final class ProcurementMetrics {

    private static final Map<String, Counter> WORKFLOW_RETRY_COUNTERS = new ConcurrentHashMap<>();

    private ProcurementMetrics() {
    }

    /**
     * 记录请购 Workflow 启动重试结果。
     *
     * @param result success/failure
     */
    public static void recordWorkflowStartRetry(String result) {
        WORKFLOW_RETRY_COUNTERS.computeIfAbsent(result,
                        key -> Counter.builder("omni.procurement.workflow.start.retries")
                                .description("Procurement Workflow start retry outcomes")
                                .tag("result", key)
                                .register(Metrics.globalRegistry))
                .increment();
    }
}
