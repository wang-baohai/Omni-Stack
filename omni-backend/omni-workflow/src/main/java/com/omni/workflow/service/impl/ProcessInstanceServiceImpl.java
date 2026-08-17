package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.ActivityInfo;
import com.omni.workflow.dto.ApprovalRecord;
import com.omni.workflow.dto.AssigneeStatus;
import com.omni.workflow.dto.ProcessProgressResponse;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.dto.WorkflowCompletionResult;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.delegate.CandidateResolverBean;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowCompletionEventService;
import com.omni.workflow.service.WorkflowTodoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程实例服务实现。
 * <p>
 * 封装 Flowable {@link RuntimeService} 和 {@link HistoryService}，
 * 同时维护 {@link WfProcessInstanceExt} 扩展表以支持业务字段查询。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final WfProcessInstanceExtMapper extMapper;
    private final WfProcessModelVersionMapper versionMapper;
    private final WorkflowTodoSyncService workflowTodoSyncService;
    private final WorkflowCompletionEventService workflowCompletionEventService;
    private final JdbcTemplate jdbcTemplate;
    private final CandidateResolverBean candidateResolverBean;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String start(StartProcessRequest request, Long userId, String userName, Long tenantId) {
        // 支持模拟发起人：如果提供了 simulateUserId，则替换发起人身份
        Long effectiveUserId = request.getSimulateUserId() != null ? request.getSimulateUserId() : userId;
        String effectiveUserName = request.getSimulateUserName() != null && !request.getSimulateUserName().isBlank()
                ? request.getSimulateUserName() : userName;

        String tenantIdStr = TenantInfoHolder.getTenantId();
        String effectiveTenantId = tenantIdStr != null ? tenantIdStr : String.valueOf(tenantId);

        // 设置流程发起人（Flowable 通过 Authentication 获取发起人信息）
        Authentication.setAuthenticatedUserId(String.valueOf(effectiveUserId));

        try {
            ProcessInstance instance;
            WfProcessModelVersion modelVersion = null;

            if (request.getModelVersionId() != null) {
                // 新路径：通过模型版本 ID 查找 processDefinitionId，使用 startProcessInstanceById
                modelVersion = versionMapper.selectById(request.getModelVersionId());
                if (modelVersion == null
                        || !"PUBLISHED".equals(modelVersion.getStatus())
                        || modelVersion.getProcessDefinitionId() == null
                        || modelVersion.getProcessDefinitionId().isBlank()) {
                    throw new BusinessException("模型版本不存在或尚未发布");
                }
                if (!tenantId.equals(modelVersion.getTenantId())) {
                    throw new BusinessException(403, "无权使用其他租户的流程模型版本");
                }
                instance = runtimeService.startProcessInstanceById(
                        modelVersion.getProcessDefinitionId(),
                        request.getBusinessKey(),
                        request.getVariables() != null ? request.getVariables() : Collections.emptyMap());
            } else {
                // 向后兼容：使用 processKey 启动
                if (request.getProcessKey() == null || request.getProcessKey().isBlank()) {
                    throw new BusinessException("流程定义 Key 或模型版本 ID 至少提供一个");
                }
                instance = runtimeService.startProcessInstanceByKeyAndTenantId(
                        request.getProcessKey(),
                        request.getBusinessKey(),
                        request.getVariables() != null ? request.getVariables() : Collections.emptyMap(),
                        effectiveTenantId);
            }

            // 写入扩展表
            WfProcessInstanceExt ext = new WfProcessInstanceExt();
            ext.setTenantId(tenantId);
            ext.setProcessInstanceId(instance.getProcessInstanceId());
            ext.setProcessKey(request.getProcessKey());

            // 自动填充 businessKey（模拟场景自动生成）
            String businessKey = request.getBusinessKey();
            if (businessKey == null || businessKey.isBlank()) {
                businessKey = "SIM-" + instance.getProcessInstanceId();
            }
            ext.setBusinessKey(businessKey);
            ext.setRequestId(request.getRequestId());
            ext.setBusinessType(request.getBusinessType());

            ext.setTitle(request.getTitle());
            ext.setStartUserId(effectiveUserId);
            ext.setStartUserName(effectiveUserName);

            // 自动填充 category（从流程定义继承）
            String category = request.getCategory();
            ProcessDefinition def = repositoryService.getProcessDefinition(instance.getProcessDefinitionId());
            if (category == null || category.isBlank()) {
                if (def != null) {
                    category = def.getCategory();
                }
            }
            ext.setCategory(category);

            // 始终填充流程定义 ID 和部署 ID（从 Flowable 实例获取）
            ext.setProcessDefinitionId(instance.getProcessDefinitionId());
            if (def != null) {
                ext.setDeploymentId(def.getDeploymentId());
            }

            ext.setStatus(1); // 进行中
            ext.setCreateTime(LocalDateTime.now());
            ext.setUpdateTime(LocalDateTime.now());

            // 填充模型关联字段
            if (modelVersion != null) {
                ext.setModelId(modelVersion.getModelId());
                ext.setModelVersionId(modelVersion.getId());
                ext.setBusinessVersion(modelVersion.getVersion());
                ext.setEngineVersion(modelVersion.getEngineVersion());
                // 自动填充 processKey
                if (ext.getProcessKey() == null) {
                    ext.setProcessKey(modelVersion.getEngineProcessKey());
                }
            }

            extMapper.insert(ext);

            // 将待办同步推迟到事务提交后，确保 Flowable 新任务已持久化
            String procInstId = instance.getProcessInstanceId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        workflowTodoSyncService.syncProcessTodos(procInstId);
                    } catch (Exception e) {
                        log.error("事务提交后同步待办失败: processInstanceId={}", procInstId, e);
                    }
                }
            });

            log.info("流程实例发起: processInstanceId={}, processKey={}, modelVersionId={}, userId={}",
                    instance.getProcessInstanceId(),
                    request.getProcessKey(),
                    request.getModelVersionId(),
                    userId);
            return instance.getProcessInstanceId();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<WfProcessInstanceExt> myInitiated(Long userId, Long tenantId,
                                                         String title, Integer status,
                                                         int page, int size) {
        LambdaQueryWrapper<WfProcessInstanceExt> wrapper = new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getTenantId, tenantId)
                .eq(WfProcessInstanceExt::getStartUserId, userId)
                .like(title != null && !title.isBlank(), WfProcessInstanceExt::getTitle, title)
                .eq(status != null, WfProcessInstanceExt::getStatus, status)
                .orderByDesc(WfProcessInstanceExt::getCreateTime);

        Page<WfProcessInstanceExt> pageResult = extMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<WfProcessInstanceExt> myCompleted(Long userId, Long tenantId,
                                                         String title,
                                                         int page, int size) {
        // 通过历史任务表查询用户已完成的任务，反查关联的流程实例
        // 不使用 HistoricProcessInstance.finished()，因为流程可能仍在运行中
        String tenantIdStr = TenantInfoHolder.getTenantId();

        List<HistoricTaskInstance> completedTasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .list();

        // 收集去重的流程实例 ID
        List<String> processInstanceIds = completedTasks.stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .distinct()
                .collect(Collectors.toList());

        if (processInstanceIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, size, page);
        }

        // 从扩展表查询业务信息，支持分页和标题过滤
        LambdaQueryWrapper<WfProcessInstanceExt> wrapper = new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getTenantId, tenantId)
                .in(WfProcessInstanceExt::getProcessInstanceId, processInstanceIds)
                .like(title != null && !title.isBlank(), WfProcessInstanceExt::getTitle, title)
                .orderByDesc(WfProcessInstanceExt::getUpdateTime);

        Page<WfProcessInstanceExt> pageResult = extMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(String processInstanceId, String reason, Long userId, Long tenantId) {
        // 查询扩展表确认发起人
        LambdaQueryWrapper<WfProcessInstanceExt> wrapper = new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getTenantId, tenantId)
                .eq(WfProcessInstanceExt::getProcessInstanceId, processInstanceId);
        WfProcessInstanceExt ext = extMapper.selectOne(wrapper);
        if (ext == null) {
            throw new BusinessException("流程实例不存在");
        }
        if (!userId.equals(ext.getStartUserId())) {
            throw new BusinessException(403, "无权终止该流程实例");
        }
        if (ext.getStatus() != 1) {
            throw new BusinessException("流程已结束，无法终止");
        }

        // 删除 Flowable 流程实例
        runtimeService.deleteProcessInstance(processInstanceId, "用户终止: " + reason);

        // 跨服务流程需要在同一事务内原子更新状态并写入完成事件；站内流程保持原有状态更新。
        if (ext.getBusinessType() != null && !ext.getBusinessType().isBlank()) {
            workflowCompletionEventService.publishCompletionEvent(
                    tenantId, processInstanceId, WorkflowCompletionResult.CANCELLED, LocalDateTime.now());
        } else {
            ext.setStatus(0); // 已终止
            ext.setUpdateTime(LocalDateTime.now());
            extMapper.updateById(ext);
        }
        workflowTodoSyncService.deleteProcessTodos(processInstanceId);

        log.info("流程实例已终止: processInstanceId={}, reason={}", processInstanceId, reason);
    }

    /** 不需要在流程全景图中展示的活动类型（startEvent 保留以展示申请人） */
    private static final Set<String> FILTERED_ACTIVITY_TYPES = Set.of(
            "boundaryEvent", "sequenceFlow",
            "intermediateCatchEvent", "intermediateThrowEvent", "compensateBoundaryEvent"
    );

    /** {@inheritDoc} */
    @Override
    public ProcessProgressResponse getProgress(String processInstanceId) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 1. 查询所有历史活动实例
        List<HistoricActivityInstance> historicActivities =
                historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .orderByHistoricActivityInstanceStartTime().asc()
                        .list();

        // 2. 安全获取当前活跃活动 ID（已完成的流程无法查询）
        List<String> activeIds;
        try {
            activeIds = runtimeService.getActiveActivityIds(processInstanceId);
        } catch (Exception e) {
            activeIds = Collections.emptyList();
        }
        Set<String> activeIdSet = new HashSet<>(activeIds);

        // 2.5 查询流程发起人，用于 startEvent 节点的申请人展示
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        String initiatorId = hpi != null ? hpi.getStartUserId() : null;

        // 2.6 查询历史任务实例获取真实 assignee（多实例场景下 HistoricActivityInstance.getAssignee() 不准确）
        List<HistoricTaskInstance> taskInstances = historyService
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId).list();
        Map<String, List<String>> taskAssigneesMap = new HashMap<>();
        Map<String, List<TaskPersonStatus>> taskPersonMap = new HashMap<>();
        for (HistoricTaskInstance task : taskInstances) {
            if (task.getTaskDefinitionKey() != null && task.getAssignee() != null
                    && !task.getAssignee().isBlank()) {
                taskAssigneesMap.computeIfAbsent(task.getTaskDefinitionKey(),
                        k -> new ArrayList<>()).add(task.getAssignee());
                boolean taskCompleted = task.getEndTime() != null && task.getDeleteReason() == null;
                boolean autoCompleted = task.getEndTime() != null && task.getDeleteReason() != null;
                taskPersonMap.computeIfAbsent(task.getTaskDefinitionKey(),
                        k -> new ArrayList<>()).add(new TaskPersonStatus(task.getAssignee(), taskCompleted, autoCompleted));
            }
        }

        // 3. 过滤掉 endEvent 等无展示意义的节点
        List<HistoricActivityInstance> filtered = historicActivities.stream()
                .filter(a -> !FILTERED_ACTIVITY_TYPES.contains(a.getActivityType()))
                .toList();

        // 4. 按 activityId 聚合（多实例活动可能有多个条目）
        Map<String, HistoricActivityInstance> firstSeen = new LinkedHashMap<>();
        Map<String, Boolean> allCompletedMap = new HashMap<>();
        Map<String, Boolean> hasActiveMap = new HashMap<>();
        Map<String, List<String>> assigneesMap = new HashMap<>();
        Map<String, String> startTimesMap = new HashMap<>();
        Map<String, String> endTimesMap = new HashMap<>();

        for (HistoricActivityInstance act : filtered) {
            String aid = act.getActivityId();
            firstSeen.putIfAbsent(aid, act);

            boolean completed = act.getEndTime() != null;
            allCompletedMap.merge(aid, completed, (a, b) -> a && b);
            hasActiveMap.merge(aid, !completed, (a, b) -> a || b);

            // startEvent 节点的 assignee 强制设为流程发起人
            if ("startEvent".equals(act.getActivityType()) && initiatorId != null) {
                assigneesMap.put(aid, new ArrayList<>(List.of(initiatorId)));
            }

            if (act.getStartTime() != null) {
                String st = act.getStartTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(dtf);
                startTimesMap.merge(aid, st, (a, b) -> a.compareTo(b) < 0 ? a : b);
            }
            if (completed && act.getEndTime() != null) {
                String et = act.getEndTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(dtf);
                endTimesMap.merge(aid, et, (a, b) -> a.compareTo(b) > 0 ? a : b);
            }
        }

        // 4.5 用历史任务实例的真实 assignee 覆盖（解决多实例场景下父 execution assignee 不准确的问题）
        for (var entry : taskAssigneesMap.entrySet()) {
            assigneesMap.put(entry.getKey(), entry.getValue());
        }

        // 4.6 为 pending 的 userTask 节点预解析候选人
        String processDefinitionId = hpi != null ? hpi.getProcessDefinitionId() : null;
        Long startUserIdLong = null;
        if (initiatorId != null) {
            try { startUserIdLong = Long.valueOf(initiatorId); } catch (NumberFormatException ignored) {}
        }
        Long tenantIdLong = null;
        if (hpi != null && hpi.getTenantId() != null) {
            try { tenantIdLong = Long.valueOf(hpi.getTenantId()); } catch (NumberFormatException ignored) {}
        }
        if (tenantIdLong == null) tenantIdLong = 1L;

        Map<String, String> userTaskNodes = Collections.emptyMap();
        if (processDefinitionId != null && startUserIdLong != null) {
            userTaskNodes = candidateResolverBean.extractUserTaskNodes(processDefinitionId);
            for (String taskId : userTaskNodes.keySet()) {
                if (!firstSeen.containsKey(taskId)) {
                    List<Long> candidates = candidateResolverBean.resolveCandidates(
                            processDefinitionId, taskId, startUserIdLong, tenantIdLong);
                    if (!candidates.isEmpty()) {
                        assigneesMap.put(taskId,
                                candidates.stream().map(String::valueOf).collect(Collectors.toList()));
                    }
                }
            }
        }

        // 5. 批量解析用户 ID → 昵称
        Set<String> userIds = assigneesMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<String, String> nameMap = resolveUserNames(userIds);

        // 6. 构建 ActivityInfo 列表
        List<ActivityInfo> allActivities = new ArrayList<>();
        for (Map.Entry<String, HistoricActivityInstance> entry : firstSeen.entrySet()) {
            String aid = entry.getKey();
            HistoricActivityInstance act = entry.getValue();

            String status;
            if (allCompletedMap.getOrDefault(aid, false)) {
                status = "completed";
            } else if (hasActiveMap.getOrDefault(aid, false) || activeIdSet.contains(aid)) {
                status = "active";
            } else {
                status = "pending";
            }

            List<String> assignees = assigneesMap.getOrDefault(aid, Collections.emptyList());
            String assignee = assignees.isEmpty() ? null : assignees.get(0);
            String assigneeNames = assignees.stream()
                    .map(id -> nameMap.getOrDefault(id, id))
                    .distinct()
                    .collect(Collectors.joining("、"));

            // 构建逐人审批状态（仅会签节点，即 taskPersonMap 中有 >1 条记录时）
            List<AssigneeStatus> assigneeStatuses = null;
            Integer completedCount = null;
            Integer totalCount = null;
            List<TaskPersonStatus> persons = taskPersonMap.get(aid);
            if (persons != null && persons.size() > 1) {
                assigneeStatuses = new ArrayList<>();
                int compCount = 0;
                for (TaskPersonStatus p : persons) {
                    String name = nameMap.getOrDefault(p.userId(), p.userId());
                    String pStatus;
                    if (p.completed()) {
                        pStatus = "completed";
                    } else if (p.autoCompleted()) {
                        pStatus = "auto-completed";
                    } else {
                        pStatus = "active";
                    }
                    assigneeStatuses.add(new AssigneeStatus(p.userId(), name, pStatus));
                    if (p.completed()) compCount++;
                }
                // 仅 active 节点需要进度徽章数据
                if ("active".equals(status)) {
                    completedCount = compCount;
                    totalCount = persons.size();
                }
            }

            allActivities.add(new ActivityInfo(
                    aid,
                    act.getActivityName(),
                    act.getActivityType(),
                    assignee,
                    assigneeNames.isEmpty() ? null : assigneeNames,
                    startTimesMap.get(aid),
                    endTimesMap.get(aid),
                    status,
                    assigneeStatuses,
                    completedCount,
                    totalCount
            ));
        }

        // 6.5 追加 pending userTask 节点到 allActivities（未到达的节点不在 firstSeen 中）
        for (Map.Entry<String, String> utEntry : userTaskNodes.entrySet()) {
            String taskId = utEntry.getKey();
            boolean exists = allActivities.stream().anyMatch(a -> taskId.equals(a.activityId()));
            if (exists) continue;

            List<String> candidateIds = assigneesMap.getOrDefault(taskId, Collections.emptyList());
            String assigneeNames = candidateIds.stream()
                    .map(id -> nameMap.getOrDefault(id, id))
                    .distinct()
                    .collect(Collectors.joining("、"));

            allActivities.add(new ActivityInfo(
                    taskId,
                    utEntry.getValue(),
                    "userTask",
                    candidateIds.isEmpty() ? null : candidateIds.get(0),
                    assigneeNames.isEmpty() ? null : assigneeNames,
                    null,
                    null,
                    "pending",
                    null,
                    null,
                    null
            ));
        }

        List<ActivityInfo> completed = allActivities.stream()
                .filter(a -> "completed".equals(a.status()))
                .toList();

        return new ProcessProgressResponse(completed, activeIds, allActivities);
    }

    /** 逐人任务状态辅助记录（仅用于 getProgress 内部构建数据） */
    private record TaskPersonStatus(String userId, boolean completed, boolean autoCompleted) {}

    /** {@inheritDoc} */
    @Override
    public List<ApprovalRecord> getApprovalRecords(String processInstanceId) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 1. 查询所有历史任务实例（按创建时间升序）
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list();

        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 查询所有历史活动实例，构建 userTask 节点状态 Map（activityId → HistoricActivityInstance）
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        Map<String, HistoricActivityInstance> parentNodeMap = new HashMap<>();
        for (HistoricActivityInstance act : activities) {
            if ("userTask".equals(act.getActivityType())) {
                parentNodeMap.putIfAbsent(act.getActivityId(), act);
            }
        }

        // 3. 查询审批意见（Comment），按 taskId 分组
        List<Comment> comments = taskService.getProcessInstanceComments(processInstanceId);
        Map<String, List<Comment>> commentMap = comments.stream()
                .filter(c -> c.getTaskId() != null)
                .collect(Collectors.groupingBy(Comment::getTaskId));

        // 4. 查询每个任务的 approved 变量，构建 approvedMap
        Map<String, Boolean> approvedMap = new HashMap<>();
        for (HistoricTaskInstance task : tasks) {
            try {
                HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
                        .taskId(task.getId())
                        .variableName("approved")
                        .singleResult();
                if (var != null && var.getValue() != null) {
                    approvedMap.put(task.getId(), (Boolean) var.getValue());
                }
            } catch (Exception e) {
                log.debug("查询任务变量失败: taskId={}", task.getId(), e);
            }
        }

        // 5. 解析用户昵称
        Set<String> userIds = tasks.stream()
                .map(HistoricTaskInstance::getAssignee)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> nameMap = resolveUserNames(userIds);

        // 6. 构建审批记录列表
        List<ApprovalRecord> records = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            if (task.getAssignee() == null || task.getAssignee().isBlank()) {
                continue;
            }

            HistoricActivityInstance parent = parentNodeMap.get(task.getTaskDefinitionKey());
            String nodeName = parent != null ? parent.getActivityName() : task.getName();

            String result;
            if (task.getEndTime() != null && task.getDeleteReason() == null) {
                // 任务正常完成，查 approved 变量判断通过还是驳回
                Boolean approved = approvedMap.get(task.getId());
                if (approved != null) {
                    result = approved ? "approved" : "rejected";
                } else {
                    result = "approved"; // 无变量时默认通过
                }
            } else if (task.getEndTime() != null) {
                // 任务被引擎删除（deleteReason != null）
                // MI completionCondition 触发时，task 的 deleteReason 为 "MI_END" → 自动通过
                // 流程被终止/驳回时，task 的 deleteReason 为 "deleted" → 已取消
                String taskDeleteReason = task.getDeleteReason();
                result = (taskDeleteReason != null && taskDeleteReason.startsWith("MI"))
                        ? "auto-approved" : "cancelled";
            } else {
                // 任务尚未结束
                result = "pending";
            }

            // 获取审批意见
            String comment = null;
            List<Comment> taskComments = commentMap.get(task.getId());
            if (taskComments != null && !taskComments.isEmpty()) {
                comment = taskComments.get(0).getFullMessage();
            }

            // 格式化审批时间
            String approvalTime = null;
            if (task.getEndTime() != null) {
                approvalTime = task.getEndTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime().format(dtf);
            }

            records.add(new ApprovalRecord(
                    nodeName,
                    task.getAssignee(),
                    nameMap.getOrDefault(task.getAssignee(), task.getAssignee()),
                    result,
                    comment,
                    approvalTime
            ));
        }

        return records;
    }

    /**
     * 批量查询用户 ID → 昵称映射。
     *
     * @param userIds 用户 ID 集合（字符串类型）
     * @return userId → nickname 映射
     */
    private Map<String, String> resolveUserNames(Set<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
            String sql = "SELECT id, nickname FROM omni_auth.sys_user WHERE id IN (" + placeholders + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userIds.toArray());
            Map<String, String> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                map.put(String.valueOf(row.get("id")), (String) row.get("nickname"));
            }
            return map;
        } catch (DataAccessException e) {
            log.warn("批量解析用户名失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<WfProcessInstanceExt> listAll(Long tenantId, String title,
                                                     Integer status, int page, int size) {
        LambdaQueryWrapper<WfProcessInstanceExt> wrapper = new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getTenantId, tenantId)
                .like(title != null && !title.isBlank(), WfProcessInstanceExt::getTitle, title)
                .eq(status != null, WfProcessInstanceExt::getStatus, status)
                .orderByDesc(WfProcessInstanceExt::getCreateTime);

        Page<WfProcessInstanceExt> pageResult = extMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }
}
