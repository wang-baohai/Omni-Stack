package com.omni.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.service.CandidateResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 候选人解析服务任务代理。
 * <p>
 * 在多实例 UserTask 之前的 ServiceTask 中调用，负责：
 * <ol>
 *   <li>从原始 BPMN XML 读取下一个 UserTask 的 {@code omni:assignment} 扩展元素</li>
 *   <li>委托 {@link CandidateResolutionService} 解析候选人列表</li>
 *   <li>将 {@code candidateUserIds} 写入流程变量，供多实例 collection 使用</li>
 * </ol>
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("candidateResolverDelegate")
@RequiredArgsConstructor
public class CandidateResolverDelegate implements JavaDelegate {

    private static final long serialVersionUID = 1L;

    private final CandidateResolutionService candidateResolutionService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            // 1. 确定下一个 UserTask 的 activityId（ServiceTask 的 outgoing 连线指向的目标）
            String targetActivityId = candidateResolutionService.resolveTargetActivityId(
                    execution.getProcessDefinitionId(), execution.getCurrentActivityId());
            if (targetActivityId == null) {
                log.error("无法解析目标 UserTask: serviceTaskId={}", execution.getCurrentActivityId());
                return;
            }

            // 2. 从原始 BPMN XML 读取 omni:assignment 配置
            String assignmentJson = candidateResolutionService.readAssignmentFromBpmnXml(
                    execution.getProcessDefinitionId(), targetActivityId);
            if (assignmentJson == null || assignmentJson.isBlank()) {
                log.warn("目标 UserTask 未配置 omni:assignment: targetActivityId={}", targetActivityId);
                List<String> fallbackIds = candidateResolutionService.handleFallback("ERROR",
                        "目标 UserTask 未配置 omni:assignment", resolveTenantId(execution));
                execution.setVariable("candidateUserIds", fallbackIds);
                candidateResolutionService.initApprovalCounters(execution, "ALL", fallbackIds.size());
                return;
            }

            // 3. 解析配置
            JsonNode config = objectMapper.readTree(assignmentJson);
            String roleCode = candidateResolutionService.getTextValue(config, "roleCode");
            String anchorType = candidateResolutionService.getTextValue(config, "anchorType");
            String scopeMode = candidateResolutionService.getTextValue(config, "scopeMode");
            JsonNode anchorParams = config.get("anchorParams");
            String fallbackStrategy = candidateResolutionService.getTextValue(config, "fallbackStrategy");
            String approvalMode = candidateResolutionService.getTextValue(config, "approvalMode");
            if (approvalMode == null) approvalMode = "ALL";

            if (roleCode == null || roleCode.isBlank()) {
                log.warn("omni:assignment 缺少 roleCode: targetActivityId={}", targetActivityId);
                List<String> fallbackIds = candidateResolutionService.handleFallback("ERROR",
                        "omni:assignment 缺少 roleCode", resolveTenantId(execution));
                execution.setVariable("candidateUserIds", fallbackIds);
                candidateResolutionService.initApprovalCounters(execution, "ALL", fallbackIds.size());
                return;
            }

            Long tenantId = resolveTenantId(execution);
            Long startUserId = candidateResolutionService.resolveStartUserId(execution);

            // 4. 解析候选人
            List<Long> candidateUserIds = candidateResolutionService.doResolveCandidates(
                    roleCode, anchorType, scopeMode, anchorParams, startUserId, tenantId);

            if (candidateUserIds.isEmpty()) {
                List<String> fallbackIds = candidateResolutionService.handleFallback(
                        fallbackStrategy,
                        "未找到候选人: roleCode=" + roleCode,
                        tenantId);
                execution.setVariable("candidateUserIds", fallbackIds);
                candidateResolutionService.initApprovalCounters(execution, "ALL", fallbackIds.size());
                return;
            }

            // 5. 写入流程变量（必须在多实例 UserTask 解析 collection 之前完成）
            execution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));
            candidateResolutionService.initApprovalCounters(execution, approvalMode, candidateUserIds.size());

            log.info("候选人解析完成: targetActivityId={}, roleCode={}, candidates={}",
                    targetActivityId, roleCode, candidateUserIds);

        } catch (Exception e) {
            log.error("CandidateResolverDelegate 执行异常: serviceTaskId={}",
                    execution.getCurrentActivityId(), e);
        }
    }

    private Long resolveTenantId(DelegateExecution execution) {
        try {
            String tenantId = execution.getTenantId();
            return tenantId != null ? Long.valueOf(tenantId) : 1L;
        } catch (NumberFormatException e) {
            return 1L;
        }
    }
}
