package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.entity.WfTodoTask;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.mapper.WfTodoTaskMapper;
import com.omni.workflow.service.WorkflowStatsService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流统计服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class WorkflowStatsServiceImpl implements WorkflowStatsService {

    private final WfTodoTaskMapper todoTaskMapper;
    private final WfProcessInstanceExtMapper extMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> workspaceStats(Long userId, Long tenantId) {
        Map<String, Object> stats = new HashMap<>();

        // 待办数量
        long todoCount = todoTaskMapper.selectCount(new LambdaQueryWrapper<WfTodoTask>()
                .eq(WfTodoTask::getTenantId, tenantId)
                .eq(WfTodoTask::getAssigneeId, userId));
        stats.put("todoCount", todoCount);

        // 我发起的（进行中）
        long myInitiatedRunning = extMapper.selectCount(new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getTenantId, tenantId)
                .eq(WfProcessInstanceExt::getStartUserId, userId)
                .eq(WfProcessInstanceExt::getStatus, 1));
        stats.put("myInitiatedRunning", myInitiatedRunning);

        // 我发起的（总数）
        long myInitiatedTotal = extMapper.selectCount(new LambdaQueryWrapper<WfProcessInstanceExt>()
                .eq(WfProcessInstanceExt::getTenantId, tenantId)
                .eq(WfProcessInstanceExt::getStartUserId, userId));
        stats.put("myInitiatedTotal", myInitiatedTotal);

        return stats;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> adminStats(Long tenantId) {
        Map<String, Object> stats = new HashMap<>();
        String tenantIdStr = TenantInfoHolder.getTenantId();

        // 流程定义数量
        ProcessDefinitionQuery defQuery = repositoryService.createProcessDefinitionQuery()
                .latestVersion();
        if (tenantIdStr != null) {
            defQuery.processDefinitionTenantId(tenantIdStr);
        }
        stats.put("definitionCount", defQuery.count());

        // 运行中的流程实例数量
        ProcessInstanceQuery runningQuery = runtimeService.createProcessInstanceQuery();
        if (tenantIdStr != null) {
            runningQuery.processInstanceTenantId(tenantIdStr);
        }
        stats.put("runningInstanceCount", runningQuery.count());

        // 历史完成的流程实例数量
        HistoricProcessInstanceQuery completedQuery = historyService.createHistoricProcessInstanceQuery()
                .finished();
        if (tenantIdStr != null) {
            completedQuery.processInstanceTenantId(tenantIdStr);
        }
        stats.put("completedInstanceCount", completedQuery.count());

        // 历史总数
        HistoricProcessInstanceQuery totalQuery = historyService.createHistoricProcessInstanceQuery();
        if (tenantIdStr != null) {
            totalQuery.processInstanceTenantId(tenantIdStr);
        }
        long totalCount = totalQuery.count();
        stats.put("totalInstanceCount", totalCount);

        return stats;
    }
}
