package com.omni.common.mqlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ 消息发送记录实体。
 * <p>
 * 对应 {@code sys_mq_message} 表，记录每条通过 {@link com.omni.common.mqlog.template.ReliableMessageTemplate}
 * 发送的可靠消息。状态流转：PENDING(0) → SENT(1) / FAILED(2) → DEAD_LETTER(3) / SKIPPED(4)。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("sys_mq_message")
public class SysMqMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务消息ID（UUID），防重投递的唯一键 */
    private String msgId;

    /** MQ Topic */
    private String topic;

    /** Spring Cloud Stream binding name */
    private String bindingName;

    /** MQ Tag（可选） */
    private String tag;

    /** 业务键（可选，如 order:12345） */
    private String msgKey;

    /** 消息体 JSON */
    private String payload;

    /** 消息中间件类型：rocketmq / kafka（预留多MQ扩展） */
    private String brokerType;

    /** 状态：0=PENDING, 1=SENT, 2=FAILED, 3=DEAD_LETTER, 4=SKIPPED */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryTime;

    /** 最后一次失败错误信息 */
    private String errorMsg;

    /** 来源服务名（spring.application.name） */
    private String serviceName;

    /** Outbox 创建时的 W3C traceId */
    private String producerTraceId;

    /** 租户ID */
    private Long tenantId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ──────────────── 状态常量 ────────────────

    /** 待投递 */
    public static final int STATUS_PENDING = 0;
    /** 已发送 */
    public static final int STATUS_SENT = 1;
    /** 发送失败 */
    public static final int STATUS_FAILED = 2;
    /** 死信（超过最大重试次数） */
    public static final int STATUS_DEAD_LETTER = 3;
    /** 已忽略（人工标记跳过） */
    public static final int STATUS_SKIPPED = 4;
}
