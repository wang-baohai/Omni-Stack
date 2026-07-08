package com.omni.common.mqlog.relay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import com.omni.common.mqlog.sender.MessageSender;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MQ 消息投递中继服务。
 * <p>
 * 核心投递逻辑：轮询 PENDING/FAILED 状态的待投递消息，调用对应 {@link MessageSender}
 * 策略实现发送到 MQ。成功后标记 SENT，失败后指数退避重试，超过最大次数标记 DEAD_LETTER。
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

    private final SysMqMessageMapper sysMqMessageMapper;
    private final Map<String, MessageSender> senderMap;

    public MqMessageRelayService(SysMqMessageMapper sysMqMessageMapper,
                                  List<MessageSender> senders) {
        this.sysMqMessageMapper = sysMqMessageMapper;
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
     * 查询待投递消息（PENDING 或 FAILED 且已过退避等待时间）。
     */
    private List<SysMqMessage> fetchPendingMessages() {
        LambdaQueryWrapper<SysMqMessage> wrapper = new LambdaQueryWrapper<SysMqMessage>()
                .in(SysMqMessage::getStatus, Arrays.asList(
                        SysMqMessage.STATUS_PENDING, SysMqMessage.STATUS_FAILED))
                .and(w -> w
                        .isNull(SysMqMessage::getNextRetryTime)
                        .or()
                        .le(SysMqMessage::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(SysMqMessage::getCreateTime)
                .last("LIMIT " + BATCH_SIZE);
        return sysMqMessageMapper.selectList(wrapper);
    }

    /**
     * 投递单条消息。
     */
    private void relayOne(SysMqMessage msg) {
        MessageSender sender = senderMap.get(msg.getBrokerType());
        if (sender == null) {
            log.warn("MQ 中继：未找到 brokerType={} 对应的 sender，msgId={}", msg.getBrokerType(), msg.getMsgId());
            return;
        }

        try {
            sender.send(msg);
            // 投递成功
            msg.setStatus(SysMqMessage.STATUS_SENT);
            msg.setNextRetryTime(null);
            msg.setErrorMsg(null);
            msg.setUpdateTime(LocalDateTime.now());
            sysMqMessageMapper.updateById(msg);
            log.debug("MQ 中继：消息投递成功 msgId={}", msg.getMsgId());
        } catch (Exception e) {
            // 投递失败，指数退避
            int newRetryCount = msg.getRetryCount() + 1;
            msg.setRetryCount(newRetryCount);
            msg.setStatus(SysMqMessage.STATUS_FAILED);
            msg.setErrorMsg(truncate(e.getMessage(), 512));
            msg.setUpdateTime(LocalDateTime.now());

            if (newRetryCount >= msg.getMaxRetry()) {
                // 超过最大重试次数，标记死信
                msg.setStatus(SysMqMessage.STATUS_DEAD_LETTER);
                log.warn("MQ 中继：消息进入死信 msgId={}, 已重试 {} 次: {}",
                        msg.getMsgId(), newRetryCount, e.getMessage());
            } else {
                // 计算下次重试时间（指数退避：2^retryCount * 基数秒）
                int backoffSeconds = (int) Math.pow(2, newRetryCount) * BACKOFF_BASE_SECONDS;
                msg.setNextRetryTime(LocalDateTime.now().plusSeconds(backoffSeconds));
                log.info("MQ 中继：消息投递失败 msgId={}, 第 {} 次重试将在 {} 秒后",
                        msg.getMsgId(), newRetryCount, backoffSeconds);
            }
            sysMqMessageMapper.updateById(msg);
        }
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
}
