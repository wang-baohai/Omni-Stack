package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.omni.common.workflow.approval.ApprovalService;
import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.ApprovalRequest;
import com.omni.workflow.dto.WorkflowCompletionResult;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.service.WorkflowCompletionEventService;
import com.omni.workflow.service.WorkflowTodoSyncService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工作流审批完成事件编排单元测试。
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class WorkflowApprovalServiceImplTest {

    @Mock
    private ApprovalService approvalService;
    @Mock
    private TaskService taskService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private WfProcessInstanceExtMapper processInstanceExtMapper;
    @Mock
    private WorkflowCompletionEventService completionEventService;
    @Mock
    private WorkflowTodoSyncService todoSyncService;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private ProcessInstanceQuery processInstanceQuery;
    @Mock
    private Task task;
    @Mock
    private ProcessInstance runningProcess;

    private WorkflowApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowApprovalServiceImpl(
                approvalService, taskService, runtimeService, processInstanceExtMapper,
                completionEventService, todoSyncService);
        TransactionSynchronizationManager.initSynchronization();
        mockCurrentTask();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldPublishApprovedEventWhenCrossServiceProcessEnds() {
        mockTaskDetails();
        mockProcessRunningState(null);
        WfProcessInstanceExt ext = new WfProcessInstanceExt();
        ext.setBusinessType("PROCUREMENT_REQUISITION");
        when(processInstanceExtMapper.selectOne(
                org.mockito.ArgumentMatchers.<Wrapper<WfProcessInstanceExt>>any())).thenReturn(ext);

        service.complete("task-1", approvalRequest(true), 21L, 7L);

        verify(completionEventService).publishCompletionEvent(
                eq(7L), eq("process-1"), eq(WorkflowCompletionResult.APPROVED), any(LocalDateTime.class));
    }

    @Test
    void shouldNotPublishWhileProcessStillRunning() {
        mockTaskDetails();
        mockProcessRunningState(runningProcess);

        service.complete("task-1", approvalRequest(false), 21L, 7L);

        verify(processInstanceExtMapper, never()).selectOne(any());
        verify(completionEventService, never()).publishCompletionEvent(any(), any(), any(), any());
    }

    @Test
    void shouldNotPublishForLegacyInAppProcess() {
        mockTaskDetails();
        mockProcessRunningState(null);
        when(processInstanceExtMapper.selectOne(
                org.mockito.ArgumentMatchers.<Wrapper<WfProcessInstanceExt>>any()))
                .thenReturn(new WfProcessInstanceExt());

        service.complete("task-1", approvalRequest(false), 21L, 7L);

        verify(completionEventService, never()).publishCompletionEvent(any(), any(), any(), any());
    }

    /** 已结束或并发抢先完成的任务应返回可恢复的 409。 */
    @Test
    void shouldReturnConflictWhenTaskHasAlreadyEnded() {
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> service.complete("task-1", approvalRequest(true), 21L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(approvalService, never()).complete(any(), anyBoolean(), any(), any());
    }

    private void mockCurrentTask() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId("7")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
    }

    private void mockTaskDetails() {
        when(task.getAssignee()).thenReturn("21");
        when(task.getProcessInstanceId()).thenReturn("process-1");
    }

    private void mockProcessRunningState(ProcessInstance processInstance) {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("process-1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceTenantId("7")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    }

    private ApprovalRequest approvalRequest(boolean approved) {
        ApprovalRequest request = new ApprovalRequest();
        request.setApproved(approved);
        request.setComment("审批测试");
        return request;
    }
}
