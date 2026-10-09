package com.omni.common.mqlog.relay;

import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import com.omni.common.mqlog.metrics.MqLogMetrics;
import com.omni.common.mqlog.sender.MessageSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC04 定向单测：未知 brokerType 不得长驻假 PENDING，且失败信息必须脱敏。
 *
 * <p>纠偏后 relay 采用「先原子 claim 再发送、终态以 status=PROCESSING 为 CAS 前置条件」模型，
 * 因此本用例将 mock 桩适配到 claim/CAS 语义：claim 返回 1（抢占成功）后进入相应状态机分支，
 * 断言语义保持不变——未知 brokerType 进入失败状态机（FAILED/DEAD_LETTER）、error 脱敏、
 * 成功投递写 SENT，且绝不因写失败而残留假 PENDING。</p>
 */
class MqMessageRelayServiceUnknownBrokerTest {

    /** 未注册对应 sender 的 brokerType 必须按状态机转入 FAILED 并计 retryCount。 */
    @Test
    void unknownBrokerTypeShouldTransitionToFailedAndCountRetryNotStayPending() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("kafka", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        stubClaim(mapper, 1);
        // 仅注册 rocketmq sender，brokerType=kafka 命中未知分支。
        MqMessageRelayService service = newService(mapper, senderOf("rocketmq"));

        service.relayPendingMessages();

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(1)).casApplyFailure(eq(1L), eq(SysMqMessage.STATUS_FAILED),
                nextRetryCaptor.capture(), errorCaptor.capture(), any(LocalDateTime.class));
        assertThat(errorCaptor.getValue())
                .as("必须写入可追踪错误原因")
                .isNotBlank();
        assertThat(nextRetryCaptor.getValue())
                .as("FAILED 分支应安排退避重试时间")
                .isNotNull();
        // 不得走 SENT 终态，也不得无条件 updateById
        verify(mapper, never()).casMarkSent(any(), any());
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** retryCount 已达 maxRetry 边界时，未知 brokerType 必须直接进入 DEAD_LETTER 终态。 */
    @Test
    void unknownBrokerTypeAtRetryCeilingShouldGoDeadLetter() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("kafka", 2, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        stubClaim(mapper, 1);
        MqMessageRelayService service = newService(mapper, senderOf("rocketmq"));

        service.relayPendingMessages();

        // 达上限转 DEAD_LETTER 且清空退避时间（nextRetryTime 传 null）
        verify(mapper, times(1)).casApplyFailure(eq(1L), eq(SysMqMessage.STATUS_DEAD_LETTER),
                eq(null), any(String.class), any(LocalDateTime.class));
        verify(mapper, never()).casMarkSent(any(), any());
    }

    /** 发送失败时 error_msg 必须脱敏连接串与凭据，禁止原文泄露。 */
    @Test
    void sendFailureShouldRedactConnectionAndCredentialsInErrorMsg() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        stubClaim(mapper, 1);
        MessageSender failing = mock(MessageSender.class);
        when(failing.brokerType()).thenReturn("rocketmq");
        doThrow(new RuntimeException(
                "connect failed jdbc:mysql://db.internal:3306/omni?u=root password=S3cr3t!\nstack"))
                .when(failing).send(any());
        MqMessageRelayService service = newService(mapper, failing);

        service.relayPendingMessages();

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(1)).casApplyFailure(eq(1L), eq(SysMqMessage.STATUS_FAILED),
                any(LocalDateTime.class), errorCaptor.capture(), any(LocalDateTime.class));
        assertThat(errorCaptor.getValue())
                .as("连接串与明文口令必须被脱敏")
                .contains("[REDACTED_DB]", "password=[REDACTED]")
                .doesNotContain("S3cr3t!", "mysql://", "\n");
    }

    /** 正常发送成功时经 CAS 写 SENT 终态，不得触发任何失败状态流转。 */
    @Test
    void knownSenderSuccessShouldMarkSent() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        stubClaim(mapper, 1);
        when(mapper.casMarkSent(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        MessageSender ok = mock(MessageSender.class);
        when(ok.brokerType()).thenReturn("rocketmq");
        MqMessageRelayService service = newService(mapper, ok);

        service.relayPendingMessages();

        verify(ok, times(1)).send(message);
        verify(mapper, times(1)).casMarkSent(eq(1L), any(LocalDateTime.class));
        verify(mapper, never()).casApplyFailure(any(), any(Integer.class).intValue(), any(), any(), any());
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** 中继在发送前必须先 claim；此处统一桩为抢占成功。 */
    private void stubClaim(SysMqMessageMapper mapper, int result) {
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(result);
    }

    private MqMessageRelayService newService(SysMqMessageMapper mapper, MessageSender... senders) {
        return new MqMessageRelayService(mapper, List.of(senders),
                new MqLogMetrics(), () -> null);
    }

    private MessageSender senderOf(String brokerType) {
        MessageSender sender = mock(MessageSender.class);
        when(sender.brokerType()).thenReturn(brokerType);
        // 未知 brokerType 用例中此 sender 不应被调用。
        return sender;
    }

    private SysMqMessage pendingMessage(String brokerType, int retryCount, int maxRetry) {
        SysMqMessage message = new SysMqMessage();
        message.setId(1L);
        message.setMsgId("msg-1");
        message.setBindingName("order-out-0");
        message.setTopic("order-out-0");
        message.setPayload("{}");
        message.setBrokerType(brokerType);
        message.setStatus(SysMqMessage.STATUS_PENDING);
        message.setRetryCount(retryCount);
        message.setMaxRetry(maxRetry);
        message.setTenantId(41L);
        message.setCreateTime(LocalDateTime.now().minusSeconds(30));
        return message;
    }
}
