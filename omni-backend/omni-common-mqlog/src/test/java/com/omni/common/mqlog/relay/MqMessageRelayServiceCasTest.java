package com.omni.common.mqlog.relay;

import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import com.omni.common.mqlog.metrics.MqLogMetrics;
import com.omni.common.mqlog.sender.MessageSender;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MQ 中继原子 claim 与 CAS 状态写单元测试（Codex 审计 MQ-02 P1 纠偏）。
 *
 * <p>核心合同：</p>
 * <ul>
 *   <li>发送前必须先通过单语句 UPDATE 抢占（claim）为 PROCESSING 租约态，
 *       抢占失败的执行器不得发送、不得写任何终态；</li>
 *   <li>成功/失败终态写均以 {@code status = PROCESSING} 为 CAS 前置条件，
 *       租约被接管时放弃写入，绝不盲目覆盖；</li>
 *   <li>retry_count 由数据库端 {@code retry_count + 1} 原子自增，不依赖读出的旧值；</li>
 *   <li>双 executor 并发拉取同一消息时，真实发送至多发生一次。</li>
 * </ul>
 */
class MqMessageRelayServiceCasTest {

    /** claim 失败（被其他 executor 持有或状态已变）：该消息既不发送也不写终态。 */
    @Test
    void should_skipMessageAndNeverSend_when_claimReturnsZero() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);
        MessageSender sender = senderOf("rocketmq");
        MqMessageRelayService service = newService(mapper, sender);

        service.relayPendingMessages();

        verify(sender, never()).send(any());
        verify(mapper, never()).casMarkSent(any(), any());
        verify(mapper, never()).casApplyFailure(any(), anyInt(), any(), any(), any());
        // 不允许残留任何"先读后无条件写"的兜底路径
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** claim 成功且发送成功：终态必须经 casMarkSent 写入，且其 SQL 以 PROCESSING 租约态为 CAS 前置条件。 */
    @Test
    void should_casMarkSentWithProcessingGuard_when_claimSucceededAndSendSucceeds() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(mapper.casMarkSent(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        MessageSender sender = senderOf("rocketmq");
        MqMessageRelayService service = newService(mapper, sender);

        service.relayPendingMessages();

        verify(sender, times(1)).send(message);
        verify(mapper, times(1)).casMarkSent(eq(1L), any(LocalDateTime.class));
        verify(mapper, never()).casApplyFailure(any(), anyInt(), any(), any(), any());
        verify(mapper, never()).updateById(any(SysMqMessage.class));
        // CAS SQL 静态断言：SENT 终态写必须以 status = 5（PROCESSING 租约）为守卫条件
        assertThat(updateSqlOf("casMarkSent"))
                .as("casMarkSent 必须以租约态 PROCESSING(5) 作为 CAS 前置条件")
                .contains("WHERE id = #{id} AND status = 5");
    }

    /** 发送成功但 CAS 写 SENT 影响 0 行（租约期间被接管）：只能放弃写入，不得盲目覆盖。 */
    @Test
    void should_giveUpTerminalWrite_when_sentCasAffectsNoRows() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        // 模拟租约到期后已被其他执行流改写：CAS 命中 0 行
        when(mapper.casMarkSent(eq(1L), any(LocalDateTime.class))).thenReturn(0);
        MqMessageRelayService service = newService(mapper, senderOf("rocketmq"));

        service.relayPendingMessages();

        verify(mapper, times(1)).casMarkSent(eq(1L), any(LocalDateTime.class));
        // 放弃写：不允许回退到 updateById / 失败写等任何覆盖路径
        verify(mapper, never()).updateById(any(SysMqMessage.class));
        verify(mapper, never()).casApplyFailure(any(), anyInt(), any(), any(), any());
    }

    /** 发送抛异常且未达重试上限：以数据库端原子自增计数并 CAS 写入 FAILED 与退避时间。 */
    @Test
    void should_atomicallyIncrementRetryAndMarkFailed_when_sendThrowsBelowRetryCeiling() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        MessageSender failing = mock(MessageSender.class);
        when(failing.brokerType()).thenReturn("rocketmq");
        doThrow(new RuntimeException("broker unreachable")).when(failing).send(any());
        MqMessageRelayService service = newService(mapper, failing);

        service.relayPendingMessages();

        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(1)).casApplyFailure(eq(1L), statusCaptor.capture(),
                nextRetryCaptor.capture(), contains("broker unreachable"), any(LocalDateTime.class));
        assertThat(statusCaptor.getValue())
                .as("未达上限应停留在 FAILED 状态等待退避重试")
                .isEqualTo(SysMqMessage.STATUS_FAILED);
        assertThat(nextRetryCaptor.getValue())
                .as("FAILED 分支必须安排下次退避时间")
                .isNotNull();
        // retry_count 必须由数据库端原子自增，禁止用读出旧值覆盖回写
        assertThat(updateSqlOf("casApplyFailure"))
                .as("retry_count 必须数据库端原子自增")
                .contains("retry_count = retry_count + 1");
    }

    /** 发送抛异常且达到重试上限：CAS 写入 DEAD_LETTER 终态并清空退避时间。 */
    @Test
    void should_markDeadLetter_when_sendThrowsAtRetryCeiling() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 2, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        MessageSender failing = mock(MessageSender.class);
        when(failing.brokerType()).thenReturn("rocketmq");
        doThrow(new RuntimeException("broker down again")).when(failing).send(any());
        MqMessageRelayService service = newService(mapper, failing);

        service.relayPendingMessages();

        verify(mapper, times(1)).casApplyFailure(eq(1L),
                eq(SysMqMessage.STATUS_DEAD_LETTER), isNull(),
                contains("broker down again"), any(LocalDateTime.class));
        verify(mapper, never()).casMarkSent(any(), any());
    }

    /** 未知 brokerType 路径同样必须先 claim：未抢占到不得写失败状态，抢占成功才进入失败状态机。 */
    @Test
    void should_routeToFailureStateMachine_when_claimedButBrokerTypeUnknown() {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("kafka", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        // 仅注册 rocketmq sender，brokerType=kafka 命中未知分支
        MqMessageRelayService service = newService(mapper, senderOf("rocketmq"));

        service.relayPendingMessages();

        // 先 claim 再进失败状态机
        verify(mapper, times(1)).claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(mapper, times(1)).casApplyFailure(eq(1L), eq(SysMqMessage.STATUS_FAILED),
                any(LocalDateTime.class), anyString(), any(LocalDateTime.class));
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** 双 executor 并发投递同一消息：claim 互斥保证真实发送至多一次。 */
    @Test
    void should_sendOnlyOnce_when_twoExecutorsClaimSameMessageConcurrently() throws Exception {
        SysMqMessageMapper mapper = mock(SysMqMessageMapper.class);
        SysMqMessage message = pendingMessage("rocketmq", 0, 3);
        when(mapper.selectList(any())).thenReturn(List.of(message));
        // 模拟数据库行锁语义：同一消息只有一个执行流能 claim 成功
        AtomicBoolean claimedBySomeone = new AtomicBoolean(false);
        when(mapper.claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> claimedBySomeone.compareAndSet(false, true) ? 1 : 0);
        MessageSender sender = senderOf("rocketmq");
        MqMessageRelayService service = newService(mapper, sender);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Runnable round = () -> {
                try {
                    startGate.await();
                    service.relayPendingMessages();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            };
            pool.submit(round);
            pool.submit(round);
            startGate.countDown();
            assertThat(doneGate.await(10, TimeUnit.SECONDS))
                    .as("两个执行流都应在超时前完成")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        verify(mapper, times(2)).claimForRelay(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(sender, times(1)).send(message);
        verify(mapper, times(1)).casMarkSent(eq(1L), any(LocalDateTime.class));
    }

    /** claim SQL 静态断言：必须同时覆盖"到期可投"与"租约超时回收"两个分支。 */
    @Test
    void should_keepLeaseReclaimBranch_when_inspectingClaimSql() {
        String sql = updateSqlOf("claimForRelay");
        assertThat(sql)
                .as("claim 必须原子置为 PROCESSING(5) 并携带租约时间")
                .contains("SET status = 5")
                .contains("next_retry_time = #{leaseUntil}");
        assertThat(sql)
                .as("claim 仅接受到期 PENDING/FAILED 或租约已过期的 PROCESSING")
                .contains("status IN (0, 2)")
                .contains("status = 5 AND next_retry_time < #{now}");
    }

    private MqMessageRelayService newService(SysMqMessageMapper mapper, MessageSender... senders) {
        return new MqMessageRelayService(mapper, List.of(senders),
                new MqLogMetrics(), () -> null);
    }

    private MessageSender senderOf(String brokerType) {
        MessageSender sender = mock(MessageSender.class);
        when(sender.brokerType()).thenReturn(brokerType);
        return sender;
    }

    /** 反射读取 Mapper 接口方法上的 @Update SQL，用于静态断言原子性条件。 */
    private static String updateSqlOf(String methodName) {
        for (Method method : SysMqMessageMapper.class.getMethods()) {
            if (method.getName().equals(methodName)) {
                Update update = method.getAnnotation(Update.class);
                assertThat(update)
                        .as("Mapper 方法 %s 必须是单语句 @Update 原子操作", methodName)
                        .isNotNull();
                return String.join(" ", update.value());
            }
        }
        throw new AssertionError("SysMqMessageMapper 缺少方法: " + methodName);
    }

    private SysMqMessage pendingMessage(String brokerType, int retryCount, int maxRetry) {
        SysMqMessage message = new SysMqMessage();
        message.setId(1L);
        message.setMsgId("msg-cas-1");
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
