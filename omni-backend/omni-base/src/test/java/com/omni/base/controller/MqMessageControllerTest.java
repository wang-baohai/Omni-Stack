package com.omni.base.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.omni.base.service.MqMessageAggregationService;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * MQ 消息运行状态控制器测试。
 */
class MqMessageControllerTest {

    /** 轻量模式必须明确报告 Outbox 可写但异步投递关闭。 */
    @Test
    void shouldReportOutboxOnlyWhenRelayIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("omni.mqlog.relay.enabled", "false")
                .withProperty("xxl.job.executor.enabled", "false");
        MqMessageController controller = new MqMessageController(
                mock(MqMessageAggregationService.class), environment);

        R<MqMessageController.MqRelayRuntimeStatus> response = controller.runtime();

        assertThat(response.getData().outboxWriteEnabled()).isTrue();
        assertThat(response.getData().deliveryEnabled()).isFalse();
        assertThat(response.getData().mode()).isEqualTo("OUTBOX_ONLY");
    }
}
