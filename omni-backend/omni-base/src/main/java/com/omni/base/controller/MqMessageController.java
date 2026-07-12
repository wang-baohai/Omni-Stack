package com.omni.base.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import lombok.RequiredArgsConstructor;
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
 * 当前查询 omni-base 本地库中的消息记录，后续可通过 Feign 聚合各服务数据。
 * </p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/base/mq-message")
@RequiredArgsConstructor
public class MqMessageController {

    private final SysMqMessageMapper sysMqMessageMapper;

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

        LambdaQueryWrapper<SysMqMessage> wrapper = new LambdaQueryWrapper<SysMqMessage>()
                .eq(SysMqMessage::getTenantId, tenantId)
                .eq(status != null, SysMqMessage::getStatus, status)
                .like(topic != null && !topic.isBlank(), SysMqMessage::getTopic, topic)
                .like(msgKey != null && !msgKey.isBlank(), SysMqMessage::getMsgKey, msgKey)
                .eq(serviceName != null && !serviceName.isBlank(), SysMqMessage::getServiceName, serviceName)
                .ge(beginTime != null, SysMqMessage::getCreateTime, beginTime)
                .le(endTime != null, SysMqMessage::getCreateTime, endTime)
                .orderByDesc(SysMqMessage::getId);

        Page<SysMqMessage> pageResult = sysMqMessageMapper.selectPage(new Page<>(page, size), wrapper);
        return R.ok(new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent()));
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
        SysMqMessage message = sysMqMessageMapper.selectOne(
                new LambdaQueryWrapper<SysMqMessage>()
                        .eq(SysMqMessage::getTenantId, tenantId)
                        .eq(SysMqMessage::getMsgId, msgId)
                        .last("LIMIT 1"));
        if (message == null) {
            return R.fail("消息不存在");
        }
        return R.ok(message);
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
        SysMqMessage message = sysMqMessageMapper.selectOne(
                new LambdaQueryWrapper<SysMqMessage>()
                        .eq(SysMqMessage::getTenantId, tenantId)
                        .eq(SysMqMessage::getMsgId, msgId)
                        .last("LIMIT 1"));
        if (message == null) {
            return R.fail("消息不存在");
        }
        int st = message.getStatus();
        if (st != SysMqMessage.STATUS_PENDING
                && st != SysMqMessage.STATUS_FAILED
                && st != SysMqMessage.STATUS_DEAD_LETTER) {
            return R.fail("仅 PENDING/FAILED/DEAD_LETTER 状态的消息可重发");
        }
        message.setStatus(SysMqMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setNextRetryTime(null);
        message.setErrorMsg(null);
        message.setUpdateTime(LocalDateTime.now());
        sysMqMessageMapper.updateById(message);
        return R.ok(null);
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
        SysMqMessage message = sysMqMessageMapper.selectOne(
                new LambdaQueryWrapper<SysMqMessage>()
                        .eq(SysMqMessage::getTenantId, tenantId)
                        .eq(SysMqMessage::getMsgId, msgId)
                        .last("LIMIT 1"));
        if (message == null) {
            return R.fail("消息不存在");
        }
        if (message.getStatus() != SysMqMessage.STATUS_DEAD_LETTER) {
            return R.fail("仅死信状态的消息可标记忽略");
        }
        message.setStatus(SysMqMessage.STATUS_SKIPPED);
        message.setUpdateTime(LocalDateTime.now());
        sysMqMessageMapper.updateById(message);
        return R.ok(null);
    }
}
