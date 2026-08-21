package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.dto.internal.InternalApprovalPreviewResponse;
import com.omni.workflow.dto.internal.InternalModelVersionProjection;
import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.dto.internal.InternalPublishedModelVersionResponse;
import com.omni.workflow.dto.internal.InternalStartProcessRequest;
import com.omni.workflow.dto.internal.InternalStartProcessResponse;
import com.omni.workflow.dto.internal.InternalTaskAssignmentRequest;
import com.omni.workflow.dto.internal.InternalTaskAssignmentResponse;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.entity.WfProcessStartRequest;
import com.omni.workflow.engine.ApprovalPreviewParser;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.InternalWorkflowService;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowProcessStartRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Workflow 内部服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalWorkflowServiceImpl implements InternalWorkflowService {

    private static final String ASSIGNMENT_ASSIGNEE = "ASSIGNEE";
    private static final String ASSIGNMENT_CANDIDATE = "CANDIDATE";
    private static final String ASSIGNMENT_NONE = "NONE";
    private static final int APPROVAL_PREVIEW_VERSION = 1;
    private static final int MAX_RESOLVE_SIZE = 200;
    /** 启动时必须与 Workflow 模型分类精确绑定的跨服务业务类型。 */
    private static final Set<String> CATEGORY_BOUND_BUSINESS_TYPES =
            Set.of("ASSET_TRANSFER", "ASSET_DISPOSAL", "SRM_SUPPLIER_ONBOARDING");

    private final ProcessInstanceService processInstanceService;
    private final WorkflowProcessStartRequestService processStartRequestService;
    private final TaskService taskService;
    private final WfProcessInstanceExtMapper processInstanceExtMapper;
    private final WfProcessModelVersionMapper processModelVersionMapper;
    private final ApprovalPreviewParser approvalPreviewParser;

    /** {@inheritDoc} */
    @Override
    public InternalModelVersionResponse getModelVersion(Long tenantId, Long modelVersionId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(modelVersionId, "modelVersionId");

        InternalModelVersionResponse modelVersion =
                processModelVersionMapper.selectInternalDetails(tenantId, modelVersionId);
        if (modelVersion == null
                || !"PUBLISHED".equals(modelVersion.getStatus())
                || modelVersion.getProcessDefinitionId() == null
                || modelVersion.getProcessDefinitionId().isBlank()) {
            throw new BusinessException(404, "流程模型版本不存在或尚未发布");
        }

        return modelVersion;
    }

    /** {@inheritDoc} */
    @Override
    public InternalModelVersionResponse getCurrentPublishedModelVersion(Long tenantId, String category) {
        requirePositive(tenantId, "tenantId");
        if (category == null || category.isBlank()) {
            throw new BusinessException(400, "category 不能为空");
        }
        InternalModelVersionResponse modelVersion =
                processModelVersionMapper.selectCurrentPublishedByCategory(tenantId, category);
        if (modelVersion == null
                || modelVersion.getProcessDefinitionId() == null
                || modelVersion.getProcessDefinitionId().isBlank()) {
            throw new BusinessException(404, "未找到当前业务分类的已发布流程模型");
        }
        return modelVersion;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<InternalPublishedModelVersionResponse> listPublishedModelVersions(
            Long tenantId, String category) {
        requirePositive(tenantId, "tenantId");
        String normalizedCategory = normalizeCategory(category);
        return processModelVersionMapper.selectPublishedByCategory(tenantId, normalizedCategory).stream()
                .map(projection -> toResponse(projection, "AVAILABLE"))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<InternalPublishedModelVersionResponse> resolveModelVersions(
            Long tenantId, List<Long> modelVersionIds) {
        requirePositive(tenantId, "tenantId");
        if (modelVersionIds == null || modelVersionIds.isEmpty()) {
            throw new BusinessException(400, "模型版本 ID 不能为空");
        }
        if (modelVersionIds.size() > MAX_RESOLVE_SIZE) {
            throw new BusinessException(400, "模型版本 ID 单次最多 200 个");
        }
        if (modelVersionIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(400, "模型版本 ID 必须为正整数");
        }
        List<Long> uniqueIds = List.copyOf(new LinkedHashSet<>(modelVersionIds));
        Map<Long, InternalModelVersionProjection> found =
                processModelVersionMapper.selectInternalBatch(tenantId, uniqueIds).stream()
                        .collect(Collectors.toMap(
                                InternalModelVersionProjection::getId,
                                Function.identity()));
        return uniqueIds.stream()
                .map(id -> {
                    InternalModelVersionProjection projection = found.get(id);
                    return projection == null
                            ? InternalPublishedModelVersionResponse.builder()
                                    .id(id)
                                    .approvalPreviewVersion(APPROVAL_PREVIEW_VERSION)
                                    .availability("NOT_FOUND")
                                    .build()
                            : toResponse(projection, availability(projection));
                })
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InternalApprovalPreviewResponse previewModelVersion(Long tenantId, Long modelVersionId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(modelVersionId, "modelVersionId");
        InternalModelVersionProjection projection =
                processModelVersionMapper.selectPreviewDetails(tenantId, modelVersionId);
        if (projection == null) {
            throw new BusinessException(404, "流程模型版本不存在");
        }
        ApprovalPreviewParser.PreviewGraph graph;
        try {
            graph = approvalPreviewParser.parse(projection.getBpmnXml());
        } catch (IllegalArgumentException exception) {
            log.warn("安全审批图解析失败: tenantId={}, modelVersionId={}, reason={}",
                    tenantId, modelVersionId, exception.getMessage());
            throw new BusinessException(500, "流程预览暂时无法生成");
        }
        return InternalApprovalPreviewResponse.builder()
                .approvalPreviewVersion(APPROVAL_PREVIEW_VERSION)
                .modelVersion(toResponse(projection, availability(projection)))
                .nodes(graph.nodes())
                .edges(graph.edges())
                .hasBranches(graph.hasBranches())
                .linearSummary(graph.linearSummary())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternalStartProcessResponse start(Long headerTenantId, InternalStartProcessRequest request) {
        requireSameTenant(headerTenantId, request.getTenantId());

        WorkflowProcessStartRequestService.StartReservationRequest reservationRequest =
                new WorkflowProcessStartRequestService.StartReservationRequest(
                        request.getTenantId(), request.getRequestId(), request.getBusinessType(),
                        request.getBusinessKey(), request.getModelVersionId(),
                        request.getStartUserId());
        WorkflowProcessStartRequestService.Reservation reservation =
                processStartRequestService.reserve(reservationRequest);
        WfProcessStartRequest startRecord = reservation.request();
        if (!reservation.acquired()) {
            if (WfProcessStartRequest.STATUS_STARTED.equals(startRecord.getStatus())
                    && startRecord.getProcessInstanceId() != null
                    && !startRecord.getProcessInstanceId().isBlank()) {
                return response(request, startRecord.getProcessInstanceId(), true);
            }
            throw new BusinessException(409, "流程启动请求正在处理中，请稍后使用同一 requestId 重试");
        }

        // 路由保存时的校验不能替代启动时校验，避免已撤销发布的版本继续创建新实例。
        InternalModelVersionResponse modelVersion =
                getModelVersion(request.getTenantId(), request.getModelVersionId());
        if (CATEGORY_BOUND_BUSINESS_TYPES.contains(request.getBusinessType())
                && !request.getBusinessType().equals(modelVersion.getCategory())) {
            throw new BusinessException(404, "流程模型分类与业务类型不匹配");
        }

        Map<String, Object> variables = request.getVariables() == null
                ? new HashMap<>() : new HashMap<>(request.getVariables());
        variables.put("requestId", request.getRequestId());
        variables.put("businessType", request.getBusinessType());
        variables.put("businessKey", request.getBusinessKey());

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            title = request.getBusinessType() + ":" + request.getBusinessKey();
        }

        StartProcessRequest startRequest = StartProcessRequest.builder()
                .modelVersionId(request.getModelVersionId())
                .title(title)
                .businessKey(request.getBusinessKey())
                .requestId(request.getRequestId())
                .businessType(request.getBusinessType())
                .category(request.getBusinessType())
                .variables(variables)
                .build();
        String processInstanceId = processInstanceService.start(
                startRequest,
                request.getStartUserId(),
                request.getStartUserName(),
                request.getTenantId());

        processStartRequestService.markStarted(
                request.getTenantId(), startRecord.getId(), processInstanceId);

        return response(request, processInstanceId, false);
    }

    private InternalStartProcessResponse response(InternalStartProcessRequest request,
                                                  String processInstanceId,
                                                  boolean replayed) {
        return InternalStartProcessResponse.builder()
                .requestId(request.getRequestId())
                .businessType(request.getBusinessType())
                .businessKey(request.getBusinessKey())
                .processInstanceId(processInstanceId)
                .replayed(replayed)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public InternalTaskAssignmentResponse validateAssignment(
            Long headerTenantId, InternalTaskAssignmentRequest request) {
        requireSameTenant(headerTenantId, request.getTenantId());

        Task task = taskService.createTaskQuery()
                .taskId(request.getTaskId())
                .taskTenantId(String.valueOf(request.getTenantId()))
                .singleResult();
        if (task == null) {
            return invalid(null, "审批任务不存在");
        }

        WfProcessInstanceExt processExt = processInstanceExtMapper.selectOne(
                new LambdaQueryWrapper<WfProcessInstanceExt>()
                        .eq(WfProcessInstanceExt::getTenantId, request.getTenantId())
                        .eq(WfProcessInstanceExt::getProcessInstanceId, task.getProcessInstanceId())
                        .eq(WfProcessInstanceExt::getBusinessKey, request.getBusinessKey())
                        .eq(WfProcessInstanceExt::getBusinessType, request.getBusinessType()));
        if (processExt == null) {
            return invalid(task.getProcessInstanceId(), "任务与指定业务不匹配");
        }

        String userId = String.valueOf(request.getUserId());
        if (userId.equals(task.getAssignee())) {
            return valid(task.getProcessInstanceId(), ASSIGNMENT_ASSIGNEE);
        }
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            return invalid(task.getProcessInstanceId(), "任务已分配给其他用户");
        }

        Task candidateTask = taskService.createTaskQuery()
                .taskId(request.getTaskId())
                .taskTenantId(String.valueOf(request.getTenantId()))
                .taskCandidateUser(userId)
                .singleResult();
        if (candidateTask != null) {
            return valid(task.getProcessInstanceId(), ASSIGNMENT_CANDIDATE);
        }
        return invalid(task.getProcessInstanceId(), "当前用户不是任务处理人或候选人");
    }

    /**
     * 强制请求头租户与请求体租户一致。
     *
     * @param headerTenantId 请求头租户 ID
     * @param bodyTenantId   请求体租户 ID
     */
    private void requireSameTenant(Long headerTenantId, Long bodyTenantId) {
        if (headerTenantId == null || !headerTenantId.equals(bodyTenantId)) {
            throw new BusinessException(403, "请求头租户与请求体租户不一致");
        }
    }

    /**
     * 校验内部接口正整数参数。
     *
     * @param value     参数值
     * @param fieldName 参数名
     */
    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BusinessException(400, fieldName + " 必须为正整数");
        }
    }

    private InternalTaskAssignmentResponse valid(String processInstanceId, String assignmentType) {
        return InternalTaskAssignmentResponse.builder()
                .valid(true)
                .processInstanceId(processInstanceId)
                .assignmentType(assignmentType)
                .message("校验通过")
                .build();
    }

    private InternalTaskAssignmentResponse invalid(String processInstanceId, String message) {
        return InternalTaskAssignmentResponse.builder()
                .valid(false)
                .processInstanceId(processInstanceId)
                .assignmentType(ASSIGNMENT_NONE)
                .message(message)
                .build();
    }

    private InternalPublishedModelVersionResponse toResponse(
            InternalModelVersionProjection projection,
            String availability) {
        return InternalPublishedModelVersionResponse.builder()
                .id(projection.getId())
                .modelId(projection.getModelId())
                .modelKey(projection.getModelKey())
                .modelName(projection.getModelName())
                .category(projection.getCategory())
                .version(projection.getVersion())
                .publishTime(projection.getPublishTime())
                .processDefinitionId(projection.getProcessDefinitionId())
                .status(projection.getStatus())
                .approvalPreviewVersion(APPROVAL_PREVIEW_VERSION)
                .availability(availability)
                .build();
    }

    private String availability(InternalModelVersionProjection projection) {
        if (!Integer.valueOf(1).equals(projection.getModelStatus())) {
            return "MODEL_ARCHIVED";
        }
        if (!"PUBLISHED".equals(projection.getStatus())
                || projection.getProcessDefinitionId() == null
                || projection.getProcessDefinitionId().isBlank()) {
            return "UNAVAILABLE";
        }
        return projection.getId().equals(projection.getCurrentPublishedVersionId())
                ? "AVAILABLE" : "NOT_CURRENT";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException(400, "category 不能为空");
        }
        String normalized = category.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(400, "category 长度不能超过 100");
        }
        return normalized;
    }
}
