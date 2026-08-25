package com.omni.common.mqlog.sender;

import com.omni.common.mqlog.entity.SysMqMessage;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RocketMQ 投递必须保留异步日志关联头。 */
class RocketMqMessageSenderTest {

    @Test
    void shouldSendMessageWithCorrelationHeaders() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(eq("asset-out-0"), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        RocketMqMessageSender sender = new RocketMqMessageSender(streamBridge);
        SysMqMessage message = new SysMqMessage();
        message.setBindingName("asset-out-0");
        message.setPayload("{\"eventId\":\"event-1\"}");
        message.setMsgId("message-1");
        message.setProducerTraceId("0123456789abcdef0123456789abcdef");

        sender.send(message);

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq("asset-out-0"), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Message.class);
        Message<?> outbound = (Message<?>) captor.getValue();
        assertThat(outbound.getHeaders().get(RocketMqMessageSender.MESSAGE_ID_HEADER))
                .isEqualTo("message-1");
        assertThat(outbound.getHeaders().get(RocketMqMessageSender.PRODUCER_TRACE_HEADER))
                .isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    void shouldSendLegacyMessageWithoutProducerTrace() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(eq("legacy-out-0"), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        RocketMqMessageSender sender = new RocketMqMessageSender(streamBridge);
        SysMqMessage message = new SysMqMessage();
        message.setBindingName("legacy-out-0");
        message.setPayload("{}");
        message.setMsgId("legacy-message");

        sender.send(message);

        verify(streamBridge).send(eq("legacy-out-0"), org.mockito.ArgumentMatchers.any());
    }
}
