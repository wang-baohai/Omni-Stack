package com.omni.common.service.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务事件 Inbox 低基数指标。
 *
 * <p>destination 必须由代码中的固定绑定名提供，result 只允许 success/retry/dead_letter。
 * 事件、租户、用户、业务键和载荷不得作为标签。</p>
 */
public final class InboxMetrics {

    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();

    private InboxMetrics() {
    }

    /**
     * 记录 Inbox 消费结果。
     *
     * @param destination 固定消息目的地
     * @param result success/retry/dead_letter
     */
    public static void record(String destination, String result) {
        String key = destination + ':' + result;
        COUNTERS.computeIfAbsent(key, ignored -> Counter.builder("omni.inbox.operations")
                        .description("Business inbox processing outcomes")
                        .tag("destination", destination)
                        .tag("result", result)
                        .register(Metrics.globalRegistry))
                .increment();
    }
}
