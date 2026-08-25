package com.omni.common.mqlog.relay;

import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import com.omni.common.mqlog.metrics.MqLogMetrics;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** MQ 中继追踪器延迟解析测试。 */
class MqMessageRelayServiceTest {

    /** 构造中继服务时不得抢先初始化追踪导出链。 */
    @Test
    void shouldNotResolveTracerDuringConstruction() {
        AtomicInteger resolutions = new AtomicInteger();
        Supplier<Tracer> tracerSupplier = () -> {
            resolutions.incrementAndGet();
            return mock(Tracer.class);
        };

        new MqMessageRelayService(mock(SysMqMessageMapper.class), List.of(),
                new MqLogMetrics(), tracerSupplier);

        assertThat(resolutions).hasValue(0);
    }
}
