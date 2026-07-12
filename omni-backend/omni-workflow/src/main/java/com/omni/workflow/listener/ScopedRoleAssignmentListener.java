package com.omni.workflow.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.service.CandidateResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于"角色 + 组织作用域"的动态审批候选人解析监听器。
 * <p>
 * 作为 Flowable {@link TaskListener}，在 UserTask 的 {@code create} 事件触发时：
 * <ol>
 *   <li>从 BPMN 扩展元素 {@code omni:assignment} 读取 JSON 配置</li>
 *   <li>委托 {@link CandidateResolutionService} 解析候选人列表</li>
 *   <li>将候选人设置到 Flowable Task 的 candidateUsers</li>
 *   <li>如无候选人，按 fallback 策略处理（抛出异常中断流程）</li>
 * </ol>
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("scopedRoleAssignmentListener")
@RequiredArgsConstructor
public class ScopedRoleAssignmentListener implements TaskListener, ExecutionListener {

    private static final long serialVersionUID = 1L;

    private final CandidateResolutionService candidateResolutionService;
    private final ObjectMapper objectMapper;

    /**
     * ExecutionListener 入口：在 UserTask 执行启动时触发（多实例解析之前）。
     * <p>负责解析候选人并写入流程变量 {@code candidateUserIds}，供 multiInstance collection 使用。</p>
     *
     * @param delegateExecution 执行委托
     */
    @Override
    public void notify(DelegateExecution delegateExecution) {
        try {
            // 1. 从 BPMN 模型读取当前节点的 omni:assignment 配置
            String assignmentJson = candidateResolutionService.readAssignmentFromBpmnXml(
                    delegateExecution.getProcessDefinitionId(),
                    delegateExecution.getCurrentActivityId());
            if (assignmentJson == null || assignmentJson.isBlank()) {
                log.debug("UserTask 未配置 omni:assignment，跳过动态解析: activityId={}",
                        delegateExecution.getCurrentActivityId());
                return;
            }

            JsonNode config = objectMapper.readTree(assignmentJson);
            String roleCode = candidateResolutionService.getTextValue(config, "roleCode");
            String anchorType = candidateResolutionService.getTextValue(config, "anchorType");
            String scopeMode = candidateResolutionService.getTextValue(config, "scopeMode");
            JsonNode anchorParams = config.get("anchorParams");
            String fallbackStrategy = candidateResolutionService.getTextValue(config, "fallbackStrategy");
            String approvalMode = candidateResolutionService.getTextValue(config, "approvalMode");

            if (roleCode == null || roleCode.isBlank()) {
                log.warn("omni:assignment 缺少 roleCode: activityId={}",
                        delegateExecution.getCurrentActivityId());
                return;
            }

            Long tenantId = candidateResolutionService.resolveTenantId(delegateExecution);
            Long startUserId = candidateResolutionService.resolveStartUserId(delegateExecution);

            // 2. 解析候选人
            List<Long> candidateUserIds = candidateResolutionService.doResolveCandidates(
                    roleCode, anchorType, scopeMode, anchorParams, startUserId, tenantId);

            if (candidateUserIds.isEmpty()) {
                List<String> fallbackIds = candidateResolutionService.handleFallback(
                        fallbackStrategy,
                        "未找到候选人: roleCode=" + roleCode,
                        tenantId);
                delegateExecution.setVariable("candidateUserIds", fallbackIds);
                candidateResolutionService.initApprovalCounters(
                        delegateExecution, approvalMode != null ? approvalMode : "ALL", fallbackIds.size());
                return;
            }

            // 3. 将候选人列表写入流程变量，供多实例 collection 使用
            delegateExecution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));

            candidateResolutionService.initApprovalCounters(
                    delegateExecution, approvalMode, candidateUserIds.size());

            log.info("ExecutionListener 候选人解析完成: activityId={}, roleCode={}, candidates={}",
                    delegateExecution.getCurrentActivityId(), roleCode, candidateUserIds);

        } catch (Exception e) {
            log.error("ScopedRoleAssignmentListener(ExecutionListener) 执行异常: activityId={}",
                    delegateExecution.getCurrentActivityId(), e);
        }
    }

    /**
     * TaskListener 入口（保留向后兼容，当前 BPMN 已改用 ExecutionListener）。
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        // 当前 BPMN 已改用 ExecutionListener(event=start)，此方法保留用于兼容旧版本
        log.debug("TaskListener 已废弃，请使用 ExecutionListener: taskId={}", delegateTask.getId());
    }
}
