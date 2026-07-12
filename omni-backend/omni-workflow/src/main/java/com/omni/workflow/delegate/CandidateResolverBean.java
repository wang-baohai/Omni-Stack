package com.omni.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.service.CandidateResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 候选人解析 Bean，供 BPMN 中 {@code flowable:collection} UEL 表达式调用。
 * <p>
 * 用法：{@code flowable:collection="${candidateResolver.resolve(execution)}"}
 * </p>
 * <p>
 * 在 Flowable 解析多实例集合时调用，委托 {@link CandidateResolutionService}
 * 从原始 BPMN XML 读取当前节点的 {@code omni:assignment} 配置，
 * 根据角色编码 + 组织锚点 + 作用域模式解析候选人列表。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("candidateResolver")
@RequiredArgsConstructor
public class CandidateResolverBean {

    private final CandidateResolutionService candidateResolutionService;
    private final ObjectMapper objectMapper;

    /**
     * 解析当前活动的候选人列表。
     * <p>由 Flowable UEL 表达式在多实例集合解析时调用。</p>
     *
     * @param execution 当前执行实例
     * @return 候选人用户 ID 字符串列表
     */
    public List<String> resolve(DelegateExecution execution) {
        try {
            String activityId = execution.getCurrentActivityId();
            Long tenantId = candidateResolutionService.resolveTenantId(execution);
            Long startUserId = candidateResolutionService.resolveStartUserId(execution);

            // 委托给公共服务
            List<Long> candidateUserIds = candidateResolutionService.resolveCandidates(
                    execution.getProcessDefinitionId(), activityId, startUserId, tenantId);

            if (candidateUserIds.isEmpty()) {
                // 读取 omni:assignment 配置（含 fallbackStrategy 和 approvalMode）
                String assignmentJson = candidateResolutionService.readAssignmentFromBpmnXml(
                        execution.getProcessDefinitionId(), activityId);
                String fallbackStrategy = "ERROR";
                String approvalMode = "ALL";
                if (assignmentJson != null && !assignmentJson.isBlank()) {
                    JsonNode config = objectMapper.readTree(assignmentJson);
                    String fb = candidateResolutionService.getTextValue(config, "fallbackStrategy");
                    if (fb != null) fallbackStrategy = fb;
                    String mode = candidateResolutionService.getTextValue(config, "approvalMode");
                    if (mode != null) approvalMode = mode;
                }
                List<String> result = candidateResolutionService.handleFallback(
                        fallbackStrategy, "未找到候选人", tenantId);
                candidateResolutionService.initApprovalCounters(execution, approvalMode, result.size());
                return result;
            }

            // 读取 approvalMode 并设置流程变量
            String assignmentJson = candidateResolutionService.readAssignmentFromBpmnXml(
                    execution.getProcessDefinitionId(), activityId);
            String approvalMode = "ALL";
            if (assignmentJson != null && !assignmentJson.isBlank()) {
                try {
                    JsonNode config = objectMapper.readTree(assignmentJson);
                    String mode = candidateResolutionService.getTextValue(config, "approvalMode");
                    if (mode != null) approvalMode = mode;
                } catch (Exception e) {
                    log.warn("解析 approvalMode 失败: activityId={}", activityId);
                }
            }
            candidateResolutionService.initApprovalCounters(execution, approvalMode, candidateUserIds.size());

            log.info("候选人解析完成: activityId={}, approvalMode={}, candidates={}",
                    activityId, approvalMode, candidateUserIds);
            return candidateUserIds.stream().map(String::valueOf).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CandidateResolverBean 执行异常: activityId={}",
                    execution.getCurrentActivityId(), e);
            candidateResolutionService.initApprovalCounters(execution, "ALL", 0);
            throw new RuntimeException("候选人解析失败", e);
        }
    }

    /**
     * 根据流程定义和活动 ID 预解析候选人（不依赖运行时的 DelegateExecution）。
     * <p>供 getProgress() 等离线场景使用。</p>
     *
     * @param processDefinitionId 流程定义 ID
     * @param activityId          BPMN 活动元素 ID
     * @param startUserId         流程发起人用户 ID
     * @param tenantId            租户 ID
     * @return 候选人用户 ID 列表，解析失败返回空列表
     */
    public List<Long> resolveCandidates(String processDefinitionId, String activityId,
                                        Long startUserId, Long tenantId) {
        return candidateResolutionService.resolveCandidates(processDefinitionId, activityId, startUserId, tenantId);
    }

    /**
     * 从 BPMN XML 中提取所有 userTask 节点的 ID 和名称。
     * <p>供 getProgress() 枚举所有可能的审批节点。</p>
     *
     * @param processDefinitionId 流程定义 ID
     * @return activityId → activityName 映射
     */
    public Map<String, String> extractUserTaskNodes(String processDefinitionId) {
        return candidateResolutionService.extractUserTaskNodes(processDefinitionId);
    }
}
