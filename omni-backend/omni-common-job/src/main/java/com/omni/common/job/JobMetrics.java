package com.omni.common.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XXL-JOB 低基数运行指标。
 *
 * <p>指标只使用固定 result 标签，不包含任务 ID、Handler、租户或用户等高基数字段。
 * Spring Boot 启用全局注册表桥接时会自动将这些计数暴露给 Prometheus；未启用观测时
 * 写入空全局注册表，不改变任务执行结果。</p>
 */
public final class JobMetrics {

    private static final Map<String, Counter> REGISTRATION_COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Counter> EXECUTION_COUNTERS = new ConcurrentHashMap<>();

    private JobMetrics() {
    }

    /**
     * 记录注册结果。
     *
     * @param result success/failure
     */
    public static void recordRegistration(String result) {
        REGISTRATION_COUNTERS.computeIfAbsent(result, key -> Counter.builder("omni.job.registrations")
                        .description("XXL-JOB registration outcomes")
                        .tag("result", key)
                        .register(Metrics.globalRegistry))
                .increment();
    }

    /**
     * 记录任务执行结果。
     *
     * @param result success/failure
     */
    public static void recordExecution(String result) {
        EXECUTION_COUNTERS.computeIfAbsent(result, key -> Counter.builder("omni.job.executions")
                        .description("XXL-JOB execution outcomes")
                        .tag("result", key)
                        .register(Metrics.globalRegistry))
                .increment();
    }
}
