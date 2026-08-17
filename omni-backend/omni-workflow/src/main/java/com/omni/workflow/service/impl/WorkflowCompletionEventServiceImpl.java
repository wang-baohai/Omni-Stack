package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.WorkflowCompletionResult;
import com.omni.workflow.dto.WorkflowProcessCompletedEvent;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.service.WorkflowCompletionEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 工作流完成事件服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCompletionEventServiceImpl implements WorkflowCompletionEventService {

    private static final String EVENT_TYPE = "workflow.process.completed.v1";
    private static final String PRODUCER = "omni-workflow";
    private static final String BINDING_NAME = "workflow-domain-out-0";

    private final WfProcessInstanceExtMapper processInstanceExtMapper;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishCompletionEvent(Long tenantId, String processInstanceId,
                                          WorkflowCompletionResult result, LocalDateTime completedTime) {
        validate(tenantId, processInstanceId, result, completedTime);
        WfProcessInstanceExt ext = processInstanceExtMapper.selectOne(
                new LambdaQueryWrapper<WfProcessInstanceExt>()
                        .eq(WfProcessInstanceExt::getTenantId, tenantId)
                        .eq(WfProcessInstanceExt::getProcessInstanceId, processInstanceId));
        if (ext == null) {
            throw new BusinessException(404, "流程实例扩展记录不存在");
        }
        if (StringUtils.hasText(ext.getCompletionEventId())) {
            return false;
        }

        String businessType = ext.getBusinessType();
        if (!StringUtils.hasText(businessType) || !StringUtils.hasText(ext.getBusinessKey())) {
            throw new BusinessException(500, "流程实例缺少跨服务业务标识");
        }

        String eventId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = LocalDateTime.now();
        WfProcessInstanceExtMapper.CompletionEventUpdate completionUpdate =
                new WfProcessInstanceExtMapper.CompletionEventUpdate(
                        tenantId, processInstanceId, result.name(), completionStatus(result),
                        completedTime, eventId, occurredAt);
        int updated = processInstanceExtMapper.markCompletionForEvent(completionUpdate);
        if (updated == 0) {
            return false;
        }
        if (updated != 1) {
            throw new BusinessException(500, "流程实例完成事件状态异常");
        }

        WorkflowProcessCompletedEvent event = WorkflowProcessCompletedEvent.builder()
                .eventId(eventId)
                .eventType(EVENT_TYPE)
                .occurredAt(occurredAt)
                .tenantId(tenantId)
                .producer(PRODUCER)
                .businessType(businessType)
                .businessKey(ext.getBusinessKey())
                .processInstanceId(processInstanceId)
                .result(result)
                .completedTime(completedTime)
                .build();
        reliableMessageRelay.send(BINDING_NAME, event, tenantId, eventId);
        log.info("工作流完成事件已写入 Outbox: tenantId={}, processInstanceId={}, result={}, eventId={}",
                tenantId, processInstanceId, result, eventId);
        return true;
    }

    private int completionStatus(WorkflowCompletionResult result) {
        return switch (result) {
            case APPROVED, REJECTED -> 2;
            case CANCELLED -> 0;
        };
    }

    private void validate(Long tenantId, String processInstanceId,
                          WorkflowCompletionResult result, LocalDateTime completedTime) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(400, "tenantId 必须为正整数");
        }
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BusinessException(400, "processInstanceId 不能为空");
        }
        if (result == null) {
            throw new BusinessException(400, "流程完成结果不能为空");
        }
        if (completedTime == null) {
            throw new BusinessException(400, "流程完成时间不能为空");
        }
    }
}
