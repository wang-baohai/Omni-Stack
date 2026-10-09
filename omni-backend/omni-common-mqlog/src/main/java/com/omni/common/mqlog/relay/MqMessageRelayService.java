package com.omni.common.mqlog.relay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import com.omni.common.mqlog.metrics.MqLogMetrics;
import com.omni.common.mqlog.sender.MessageSender;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * MQ 消息投递中继服务。
 * <p>
 * 核心投递逻辑：轮询 PENDING/FAILED 状态的待投递消息候选集，先通过单语句原子 UPDATE
 * 将消息抢占（claim）为 {@code PROCESSING} 租约态，抢占成功的执行流才调用对应
 * {@link MessageSender} 策略实现发送到 MQ；发送终态写同样以 {@code status=PROCESSING}
 * 为 CAS 前置条件，保证多 executor 并行时不重复发送、不互相覆盖状态。
 * 抢占后发送失败则指数退避重试（retry_count 数据库端原子自增），超过最大次数标记 DEAD_LETTER。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class MqMessageRelayService {

    /** 每批拉取消息数量上限 */
    private static final int BATCH_SIZE = 100;

    /** 退避基数（秒） */
    private static final int BACKOFF_BASE_SECONDS = 10;

    /** relay 原子抢占（claim）租约时长（秒）：抢占后允许其他 executor 回收的时间窗口 */
    private static final int CLAIM_LEASE_SECONDS = 120;

    /** error_msg 最大长度，与 sys_mq_message.error_msg 列宽一致 */
    private static final int MAX_ERROR_LENGTH = 512;

    private final SysMqMessageMapper sysMqMessageMapper;
    private final Map<String, MessageSender> senderMap;
    private final MqLogMetrics mqLogMetrics;
    private final Supplier<Tracer> tracerSupplier;

    public MqMessageRelayService(SysMqMessageMapper sysMqMessageMapper,
                                  List<MessageSender> senders,
                                  MqLogMetrics mqLogMetrics,
                                  Supplier<Tracer> tracerSupplier) {
        this.sysMqMessageMapper = sysMqMessageMapper;
        this.mqLogMetrics = mqLogMetrics;
        this.tracerSupplier = tracerSupplier;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(MessageSender::brokerType, Function.identity()));
        log.info("MQ 消息中继服务初始化，已注册 sender: {}", senderMap.keySet());
    }

    /**
     * 执行一轮消息投递。
     * <p>由 XXL-JOB {@link MqMessageRelayJob} 定时调用。</p>
     */
    public void relayPendingMessages() {
        List<SysMqMessage> messages = fetchPendingMessages();
        if (messages.isEmpty()) {
            return;
        }
        log.info("MQ 中继：本轮拉取 {} 条待投递消息", messages.size());

        for (SysMqMessage msg : messages) {
            relayOne(msg);
        }
    }

    /**
     * 查询待投递消息候选集（仅扫描，真正投递前的并发保护由 {@code claimForRelay} 完成）：
     * <ul>
     *   <li>PENDING/FAILED 且已过退避等待时间；</li>
     *   <li>PROCESSING 但租约已到期（持有 executor 崩溃），允许其他执行流回收，避免永久卡投递中。</li>
     * </ul>
     * <p>后台扫描跨租户，故意不加 tenantId 过滤。</p>
     */
    private List<SysMqMessage> fetchPendingMessages() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SysMqMessage> wrapper = new LambdaQueryWrapper<SysMqMessage>()
                .and(w -> w
                        .and(pending -> pending
                                .in(SysMqMessage::getStatus, Arrays.asList(
                                        SysMqMessage.STATUS_PENDING, SysMqMessage.STATUS_FAILED))
                                .and(due -> due
                                        .isNull(SysMqMessage::getNextRetryTime)
                                        .or()
                                        .le(SysMqMessage::getNextRetryTime, now)))
                        .or(lease -> lease
                                .eq(SysMqMessage::getStatus, SysMqMessage.STATUS_PROCESSING)
                                .lt(SysMqMessage::getNextRetryTime, now)))
                .orderByAsc(SysMqMessage::getCreateTime)
                .last("LIMIT " + BATCH_SIZE);
        return sysMqMessageMapper.selectList(wrapper);
    }

    /**
     * 投递单条消息。
     */
    private void relayOne(SysMqMessage msg) {
        Tracer tracer = tracerSupplier.get();
        Span relaySpan = tracer == null ? null : tracer.nextSpan().name("mq relay").start();
        try (Tracer.SpanInScope ignored = relaySpan == null ? null : tracer.withSpan(relaySpan)) {
            relayOneWithinSpan(msg);
        } finally {
            if (relaySpan != null) {
                relaySpan.end();
            }
        }
    }

    /** 在独立消费 span 中投递一条消息，日志通过 msgId 关联生产与消费 trace。 */
    private void relayOneWithinSpan(SysMqMessage msg) {
        // 先原子抢占（claim）：只有把状态从 PENDING/FAILED 改成 PROCESSING 租约态成功（受影响行数==1）
        // 的执行流才允许真正发送，杜绝双 executor 并行拉取同一条消息导致的重复投递。
        // claim 为单语句自动提交 UPDATE，不包裹跨网络发送的长事务，避免长时间锁表。
        int claimed = sysMqMessageMapper.claimForRelay(msg.getId(), LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(CLAIM_LEASE_SECONDS));
        if (claimed == 0) {
            // 抢占失败：消息已被其他 executor 持有（租约未到期）或状态已变化，本轮直接跳过。
            log.debug("MQ 中继：抢占失败（已被其他执行流持有或状态已变），跳过 msgId={}", msg.getMsgId());
            return;
        }

        MessageSender sender = senderMap.get(msg.getBrokerType());
        if (sender == null) {
            // 未知 brokerType 属永久性路由错误：禁止仅告警后返回造成假 PENDING 长驻与告警盲区，
            // 统一走失败状态机（计 retryCount + 退避 + 达上限转 DEAD_LETTER + 记指标），错误原因使用固定文本并脱敏。
            log.warn("MQ 中继：未找到 brokerType={} 对应的 sender，msgId={}，转入失败状态机", msg.getBrokerType(), msg.getMsgId());
            handleSendFailure(msg, "未找到 brokerType 对应的 sender: " + msg.getBrokerType());
            return;
        }

        try {
            sender.send(msg);
            // 投递成功：以 PROCESSING 租约态为 CAS 前置条件写 SENT 终态。
            int sent = sysMqMessageMapper.casMarkSent(msg.getId(), LocalDateTime.now());
            if (sent == 0) {
                // 租约期间已被其他执行流接管（状态已非 PROCESSING），放弃写入，不得覆盖新状态。
                log.warn("MQ 中继：消息已发送但 CAS 写 SENT 未命中（租约期间被接管），放弃写入 msgId={}", msg.getMsgId());
                return;
            }
            mqLogMetrics.recordOperation(msg.getBindingName(), "sent");
            log.info("MQ 中继：消息投递成功 msgId={}, producerTraceId={}, relayTraceId={}",
                    msg.getMsgId(), msg.getProducerTraceId(), currentTraceId());
        } catch (Exception e) {
            handleSendFailure(msg, e.getMessage());
        }
    }

    /**
     * 统一失败处理：以 PROCESSING 租约态为 CAS 前置条件，写入 retry_count 原子自增、脱敏错误、
     * 指数退避的下次重试时间；达到 maxRetry 则转入 DEAD_LETTER 终态，并记录 retry/dead_letter 指标。
     * <p>供发送异常与未知 brokerType 两条失败路径复用，确保不吞异常、不无限 PENDING、无告警盲区。
     * retry_count 的自增由数据库端 {@code retry_count = retry_count + 1} 完成，不依赖读出的旧值；
     * 目标状态（FAILED/DEAD_LETTER）由本轮读出的 retryCount 判定。</p>
     *
     * @param msg       待处理消息
     * @param rawError  原始错误信息（可能含连接串/凭据，写入前必须脱敏）
     */
    private void handleSendFailure(SysMqMessage msg, String rawError) {
        int newRetryCount = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
        int maxRetry = msg.getMaxRetry() == null ? 0 : msg.getMaxRetry();
        String sanitized = sanitizeError(rawError);
        LocalDateTime now = LocalDateTime.now();

        if (newRetryCount >= maxRetry) {
            // 超过最大重试次数，标记死信：CAS 命中失败写（清空退避时间），retry_count 数据库端自增。
            sysMqMessageMapper.casApplyFailure(msg.getId(), SysMqMessage.STATUS_DEAD_LETTER,
                    null, sanitized, now);
            mqLogMetrics.recordOperation(msg.getBindingName(), "dead_letter");
            log.warn("MQ 中继：消息进入死信 msgId={}, producerTraceId={}, relayTraceId={}, 已重试 {} 次: {}",
                    msg.getMsgId(), msg.getProducerTraceId(), currentTraceId(), newRetryCount, sanitized);
        } else {
            mqLogMetrics.recordOperation(msg.getBindingName(), "retry");
            // 计算下次重试时间（指数退避：2^retryCount * 基数秒）
            int backoffSeconds = (int) Math.pow(2, newRetryCount) * BACKOFF_BASE_SECONDS;
            LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
            sysMqMessageMapper.casApplyFailure(msg.getId(), SysMqMessage.STATUS_FAILED,
                    nextRetryTime, sanitized, now);
            log.info("MQ 中继：消息投递失败 msgId={}, producerTraceId={}, relayTraceId={}, 第 {} 次重试将在 {} 秒后",
                    msg.getMsgId(), msg.getProducerTraceId(), currentTraceId(), newRetryCount, backoffSeconds);
        }
    }

    /** 返回当前中继 span 的 traceId。 */
    private String currentTraceId() {
        Tracer tracer = tracerSupplier.get();
        Span current = tracer == null ? null : tracer.currentSpan();
        return current == null ? null : current.context().traceId();
    }

    /**
     * 截断字符串到指定长度。
     */
    private static String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    /**
     * 脱敏错误信息后再落库：折叠控制字符、屏蔽 JDBC 连接串与凭据键值，最后按列宽截断。
     * <p>与 {@code TenantProvisionRequestHandler.sanitizeError} 保持一致的脱敏口径，
     * 防止完整连接串/明文口令经 error_msg 通过查询接口泄露。</p>
     */
    private static String sanitizeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未知投递错误";
        }
        String sanitized = raw.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)jdbc:[^\\s]+", "[REDACTED_DB]")
                .replaceAll(
                        "(?i)(password|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+",
                        "$1=[REDACTED]")
                .trim();
        return truncate(sanitized, MAX_ERROR_LENGTH);
    }
}
