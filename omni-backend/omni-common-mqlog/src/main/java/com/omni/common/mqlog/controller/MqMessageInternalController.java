package com.omni.common.mqlog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * MQ 消息记录内部查询 API。
 * <p>
 * 供 {@code omni-base} 服务通过 Feign 聚合调用，不走网关路由。
 * 提供分页查询、详情查看、手动重发和标记忽略等操作。
 * </p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/internal/mq-message")
@RequiredArgsConstructor
public class MqMessageInternalController {

    private final SysMqMessageMapper sysMqMessageMapper;

    /**
     * 分页查询消息记录（供 Feign 聚合）。
     *
     * @param tenantId    租户 ID
     * @param status      状态过滤（可选）
     * @param topic       Topic 模糊匹配（可选）
     * @param msgKey      业务键模糊匹配（可选）
     * @param serviceName 来源服务名过滤（可选）
     * @param beginTime   起始时间（可选）
     * @param endTime     结束时间（可选）
     * @param page        页码
     * @param size        每页大小
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<SysMqMessage>> list(
            @RequestParam Long tenantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String msgKey,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        LambdaQueryWrapper<SysMqMessage> wrapper = new LambdaQueryWrapper<SysMqMessage>()
                .eq(SysMqMessage::getTenantId, tenantId)
                .eq(status != null, SysMqMessage::getStatus, status)
                .like(topic != null && !topic.isBlank(), SysMqMessage::getTopic, topic)
                .like(msgKey != null && !msgKey.isBlank(), SysMqMessage::getMsgKey, msgKey)
                .eq(serviceName != null && !serviceName.isBlank(), SysMqMessage::getServiceName, serviceName)
                .ge(beginTime != null && !beginTime.isBlank(), SysMqMessage::getCreateTime, beginTime)
                .le(endTime != null && !endTime.isBlank(), SysMqMessage::getCreateTime, endTime)
                .orderByDesc(SysMqMessage::getId);

        Page<SysMqMessage> pageResult = sysMqMessageMapper.selectPage(new Page<>(page, size), wrapper);
        return R.ok(new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent()));
    }

    /**
     * 查询消息详情。
     *
     * @param tenantId 租户 ID
     * @param msgId    消息ID
     * @return 消息记录
     */
    @GetMapping("/{msgId}")
    public R<SysMqMessage> getByMsgId(
            @RequestParam Long tenantId,
            @PathVariable String msgId) {
        SysMqMessage message = sysMqMessageMapper.selectOne(
                new LambdaQueryWrapper<SysMqMessage>()
                        .eq(SysMqMessage::getTenantId, tenantId)
                        .eq(SysMqMessage::getMsgId, msgId)
                        .last("LIMIT 1"));
        return R.ok(message);
    }

    /**
     * 手动重发消息（将状态改回 PENDING）。
     *
     * @param tenantId 租户 ID
     * @param msgId    消息ID
     * @return 操作结果
     */
    @PostMapping("/{msgId}/resend")
    public R<Void> resend(
            @RequestParam Long tenantId,
            @PathVariable String msgId) {
        SysMqMessage message = sysMqMessageMapper.selectOne(
                new LambdaQueryWrapper<SysMqMessage>()
                        .eq(SysMqMessage::getTenantId, tenantId)
                        .eq(SysMqMessage::getMsgId, msgId)
                        .last("LIMIT 1"));
        if (message == null) {
            return R.fail("消息不存在");
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
     *
     * @param tenantId 租户 ID
     * @param msgId    消息ID
     * @return 操作结果
     */
    @PostMapping("/{msgId}/skip")
    public R<Void> skip(
            @RequestParam Long tenantId,
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
