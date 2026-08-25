package com.omni.base.controller;

import com.omni.base.service.MqMessageAggregationService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.common.mqlog.entity.SysMqMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * MQ 消息记录管理控制器。
 * <p>
 * 提供消息记录的查询、重发和忽略操作，面向运维人员。
 * 当前统一聚合 omni-base 与 omni-crm 各自数据库中的本地 Outbox 记录。
 * </p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/base/mq-message")
@RequiredArgsConstructor
public class MqMessageController {

    private final MqMessageAggregationService mqMessageAggregationService;
    private final Environment environment;

    /**
     * 查询当前消息投递运行状态。
     *
     * @return Outbox 写入与异步投递能力状态
     */
    @GetMapping("/runtime")
    @PreAuthorize("hasAuthority('base:mqmessage:list')")
    public R<MqRelayRuntimeStatus> runtime() {
        boolean relayEnabled = environment.getProperty(
                "omni.mqlog.relay.enabled", Boolean.class, true);
        boolean executorEnabled = environment.getProperty(
                "xxl.job.executor.enabled", Boolean.class, true);
        boolean deliveryEnabled = relayEnabled && executorEnabled;
        return R.ok(new MqRelayRuntimeStatus(
                true,
                deliveryEnabled,
                deliveryEnabled ? "FULL" : "OUTBOX_ONLY"));
    }

    /**
     * 分页查询消息记录。
     *
     * @param tenantId    租户 ID
     * @param status      状态过滤（可选）
     * @param topic       Topic 模糊匹配（可选）
     * @param msgKey      业务键模糊匹配（可选）
     * @param serviceName 来源服务名（可选）
     * @param beginTime   起始时间（可选）
     * @param endTime     结束时间（可选）
     * @param page        页码
     * @param size        每页大小
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('base:mqmessage:list')")
    public R<PageResult<SysMqMessage>> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String msgKey,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return R.ok(mqMessageAggregationService.list(tenantId, status, topic, msgKey,
                serviceName, beginTime, endTime, page, size));
    }

    /**
     * 查询消息详情。
     *
     * @param tenantId 租户 ID
     * @param msgId    消息ID（UUID）
     * @return 消息记录详情
     */
    @GetMapping("/{msgId}")
    @PreAuthorize("hasAuthority('base:mqmessage:list')")
    public R<SysMqMessage> getByMsgId(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String msgId) {
        return R.ok(mqMessageAggregationService.getByMsgId(tenantId, msgId));
    }

    /**
     * 手动重发消息。
     * <p>将 PENDING/FAILED/DEAD_LETTER 状态的消息重置为 PENDING，relay 任务下次轮询时重新投递。</p>
     *
     * @param tenantId 租户 ID
     * @param msgId    消息ID
     * @return 操作结果
     */
    @PostMapping("/{msgId}/resend")
    @PreAuthorize("hasAuthority('base:mqmessage:resend')")
    public R<Void> resend(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String msgId) {
        mqMessageAggregationService.resend(tenantId, msgId);
        return R.ok();
    }

    /**
     * 标记忽略（DEAD_LETTER -> SKIPPED）。
     * <p>确认该消息无需再投递，标记为已忽略终态。</p>
     *
     * @param tenantId 租户 ID
     * @param msgId    消息ID
     * @return 操作结果
     */
    @PostMapping("/{msgId}/skip")
    @PreAuthorize("hasAuthority('base:mqmessage:skip')")
    public R<Void> skip(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String msgId) {
        mqMessageAggregationService.skip(tenantId, msgId);
        return R.ok();
    }

    /**
     * 消息投递运行状态。
     *
     * @param outboxWriteEnabled 本地事务 Outbox 是否可写
     * @param deliveryEnabled    后台异步投递是否启用
     * @param mode               运行模式
     */
    public record MqRelayRuntimeStatus(
            boolean outboxWriteEnabled,
            boolean deliveryEnabled,
            String mode) {
    }
}
