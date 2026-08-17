package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.dto.internal.InternalStartProcessRequest;
import com.omni.workflow.dto.internal.InternalTaskAssignmentRequest;
import com.omni.workflow.dto.internal.InternalTaskAssignmentResponse;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.entity.WfProcessStartRequest;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowProcessStartRequestService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Workflow 内部服务单元测试。
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class InternalWorkflowServiceImplTest {

    @Mock
    private ProcessInstanceService processInstanceService;

    @Mock
    private WorkflowProcessStartRequestService processStartRequestService;

    @Mock
    private TaskService taskService;

    @Mock
    private WfProcessInstanceExtMapper processInstanceExtMapper;

    @Mock
    private WfProcessModelVersionMapper processModelVersionMapper;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private TaskQuery candidateQuery;

    @Mock
    private Task task;

    private InternalWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternalWorkflowServiceImpl(
                processInstanceService, processStartRequestService, taskService,
                processInstanceExtMapper, processModelVersionMapper);
    }

    @Test
    void shouldReturnPublishedModelVersionWithinTenant() {
        InternalModelVersionResponse modelVersion =
                modelVersion("PUBLISHED", "definition-88");
        when(processModelVersionMapper.selectInternalDetails(7L, 88L))
                .thenReturn(modelVersion);

        var response = service.getModelVersion(7L, 88L);

        assertEquals(88L, response.getId());
        assertEquals(31L, response.getModelId());
        assertEquals("procurement-approval", response.getModelKey());
        assertEquals("PROCUREMENT_REQUISITION", response.getCategory());
        assertEquals(3, response.getVersion());
        assertEquals("definition-88", response.getProcessDefinitionId());
        assertEquals("PUBLISHED", response.getStatus());
    }

    @Test
    void shouldResolveCurrentPublishedModelVersionByBusinessCategory() {
        InternalModelVersionResponse modelVersion =
                modelVersion("PUBLISHED", "definition-88");
        modelVersion.setCategory("SRM_SUPPLIER_ONBOARDING");
        when(processModelVersionMapper.selectCurrentPublishedByCategory(
                7L, "SRM_SUPPLIER_ONBOARDING")).thenReturn(modelVersion);

        var response = service.getCurrentPublishedModelVersion(
                7L, "SRM_SUPPLIER_ONBOARDING");

        assertEquals(88L, response.getId());
        assertEquals("SRM_SUPPLIER_ONBOARDING", response.getCategory());
    }

    @Test
    void shouldFailWhenCurrentPublishedCategoryHasNoStartableModel() {
        when(processModelVersionMapper.selectCurrentPublishedByCategory(
                7L, "SRM_SUPPLIER_ONBOARDING")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getCurrentPublishedModelVersion(7L, "SRM_SUPPLIER_ONBOARDING"));

        assertEquals(404, exception.getCode());
    }

    @Test
    void shouldUseSameFailureForMissingAndUnpublishedModelVersion() {
        when(processModelVersionMapper.selectInternalDetails(7L, 88L))
                .thenReturn(null, modelVersion("DRAFT", null));

        BusinessException missing = assertThrows(
                BusinessException.class, () -> service.getModelVersion(7L, 88L));
        BusinessException unpublished = assertThrows(
                BusinessException.class, () -> service.getModelVersion(7L, 88L));

        assertEquals(404, missing.getCode());
        assertEquals("流程模型版本不存在或尚未发布", missing.getMessage());
        assertEquals(missing.getCode(), unpublished.getCode());
        assertEquals(missing.getMessage(), unpublished.getMessage());
    }

    @Test
    void shouldRejectPublishedModelVersionWithoutProcessDefinition() {
        when(processModelVersionMapper.selectInternalDetails(7L, 88L))
                .thenReturn(modelVersion("PUBLISHED", "  "));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.getModelVersion(7L, 88L));

        assertEquals(404, exception.getCode());
        assertEquals("流程模型版本不存在或尚未发布", exception.getMessage());
    }

    @Test
    void shouldRejectNonPositiveTenantBeforeModelVersionQuery() {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.getModelVersion(0L, 88L));

        assertEquals(400, exception.getCode());
        verify(processModelVersionMapper, never()).selectInternalDetails(any(), any());
    }

    @Test
    void shouldMapInternalStartRequestToExistingProcessService() {
        InternalStartProcessRequest request = startRequest();
        WfProcessStartRequest startRecord = startRecord(WfProcessStartRequest.STATUS_RESERVED, null);
        when(processStartRequestService.reserve(startReservationRequest()))
                .thenReturn(new WorkflowProcessStartRequestService.Reservation(startRecord, true, true));
        when(processModelVersionMapper.selectInternalDetails(7L, 88L))
                .thenReturn(modelVersion("PUBLISHED", "definition-88"));
        when(processInstanceService.start(any(StartProcessRequest.class), eq(21L), eq("buyer"), eq(7L)))
                .thenReturn("process-1");

        var response = service.start(7L, request);

        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(processInstanceService).start(captor.capture(), eq(21L), eq("buyer"), eq(7L));
        StartProcessRequest mapped = captor.getValue();
        assertEquals(88L, mapped.getModelVersionId());
        assertEquals("PROCUREMENT_REQUISITION", mapped.getCategory());
        assertEquals("PROCUREMENT_REQUISITION", mapped.getBusinessType());
        assertEquals("1001", mapped.getBusinessKey());
        assertEquals("req-1", mapped.getRequestId());
        assertEquals("req-1", mapped.getVariables().get("requestId"));
        assertEquals("process-1", response.getProcessInstanceId());
        assertFalse(response.isReplayed());
        verify(processStartRequestService).markStarted(7L, 91L, "process-1");
    }

    @Test
    void shouldRevalidatePublishedModelImmediatelyBeforeNewInternalStart() {
        InternalStartProcessRequest request = startRequest();
        WfProcessStartRequest startRecord = startRecord(WfProcessStartRequest.STATUS_RESERVED, null);
        when(processStartRequestService.reserve(startReservationRequest()))
                .thenReturn(new WorkflowProcessStartRequestService.Reservation(startRecord, true, true));
        when(processModelVersionMapper.selectInternalDetails(7L, 88L))
                .thenReturn(modelVersion("DRAFT", "definition-88"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.start(7L, request));

        assertEquals(404, exception.getCode());
        verify(processInstanceService, never()).start(any(), any(), any(), any());
        verify(processStartRequestService, never()).markStarted(any(), any(), any());
    }

    @Test
    void shouldRejectAssetStartWhenModelCategoryDoesNotMatchBusinessType() {
        InternalStartProcessRequest request = startRequest();
        request.setBusinessType("ASSET_TRANSFER");
        WfProcessStartRequest startRecord =
                startRecord(WfProcessStartRequest.STATUS_RESERVED, null);
        startRecord.setBusinessType("ASSET_TRANSFER");
        when(processStartRequestService.reserve(
                new WorkflowProcessStartRequestService.StartReservationRequest(
                        7L, "req-1", "ASSET_TRANSFER", "1001", 88L, 21L)))
                .thenReturn(new WorkflowProcessStartRequestService.Reservation(
                        startRecord, true, true));
        InternalModelVersionResponse modelVersion =
                modelVersion("PUBLISHED", "definition-88");
        modelVersion.setCategory("ASSET_DISPOSAL");
        when(processModelVersionMapper.selectInternalDetails(7L, 88L))
                .thenReturn(modelVersion);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.start(7L, request));

        assertEquals(404, exception.getCode());
        assertEquals("流程模型分类与业务类型不匹配", exception.getMessage());
        verify(processInstanceService, never()).start(any(), any(), any(), any());
        verify(processStartRequestService, never()).markStarted(any(), any(), any());
    }

    @Test
    void shouldReplayStartedProcessWithoutCreatingAnotherInstance() {
        InternalStartProcessRequest request = startRequest();
        WfProcessStartRequest startRecord = startRecord(WfProcessStartRequest.STATUS_STARTED, "process-1");
        when(processStartRequestService.reserve(startReservationRequest()))
                .thenReturn(new WorkflowProcessStartRequestService.Reservation(startRecord, false, false));

        var response = service.start(7L, request);

        assertEquals("process-1", response.getProcessInstanceId());
        assertTrue(response.isReplayed());
        verify(processInstanceService, never()).start(any(), any(), any(), any());
    }

    @Test
    void shouldRejectStartWhenHeaderTenantDiffersFromBody() {
        InternalStartProcessRequest request = startRequest();

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.start(8L, request));

        assertEquals(403, exception.getCode());
        verify(processInstanceService, never()).start(any(), any(), any(), any());
    }

    @Test
    void shouldAcceptCurrentAssigneeAfterBusinessBoundaryValidation() {
        InternalTaskAssignmentRequest request = assignmentRequest();
        mockTaskLookup("21");
        when(processInstanceExtMapper.selectOne(
                org.mockito.ArgumentMatchers.<Wrapper<WfProcessInstanceExt>>any()))
                .thenReturn(new WfProcessInstanceExt());

        InternalTaskAssignmentResponse response = service.validateAssignment(7L, request);

        assertTrue(response.isValid());
        assertEquals("ASSIGNEE", response.getAssignmentType());
        verify(taskService).createTaskQuery();
    }

    @Test
    void shouldAcceptCandidateWhenTaskHasNoAssignee() {
        InternalTaskAssignmentRequest request = assignmentRequest();
        mockTaskLookup(null);
        when(processInstanceExtMapper.selectOne(
                org.mockito.ArgumentMatchers.<Wrapper<WfProcessInstanceExt>>any()))
                .thenReturn(new WfProcessInstanceExt());
        when(taskService.createTaskQuery()).thenReturn(taskQuery, candidateQuery);
        when(candidateQuery.taskId("task-1")).thenReturn(candidateQuery);
        when(candidateQuery.taskTenantId("7")).thenReturn(candidateQuery);
        when(candidateQuery.taskCandidateUser("21")).thenReturn(candidateQuery);
        when(candidateQuery.singleResult()).thenReturn(task);

        InternalTaskAssignmentResponse response = service.validateAssignment(7L, request);

        assertTrue(response.isValid());
        assertEquals("CANDIDATE", response.getAssignmentType());
    }

    @Test
    void shouldRejectTaskWhenBusinessBoundaryDoesNotMatch() {
        InternalTaskAssignmentRequest request = assignmentRequest();
        mockTaskLookup("21");
        when(processInstanceExtMapper.selectOne(
                org.mockito.ArgumentMatchers.<Wrapper<WfProcessInstanceExt>>any())).thenReturn(null);

        InternalTaskAssignmentResponse response = service.validateAssignment(7L, request);

        assertFalse(response.isValid());
        assertEquals("NONE", response.getAssignmentType());
        assertEquals("任务与指定业务不匹配", response.getMessage());
    }

    private void mockTaskLookup(String assignee) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId("7")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("process-1");
        lenient().when(task.getAssignee()).thenReturn(assignee);
    }

    private InternalStartProcessRequest startRequest() {
        InternalStartProcessRequest request = new InternalStartProcessRequest();
        request.setRequestId("req-1");
        request.setTenantId(7L);
        request.setModelVersionId(88L);
        request.setBusinessType("PROCUREMENT_REQUISITION");
        request.setBusinessKey("1001");
        request.setStartUserId(21L);
        request.setStartUserName("buyer");
        request.setVariables(Map.of("amount", 100));
        return request;
    }

    private WorkflowProcessStartRequestService.StartReservationRequest
            startReservationRequest() {
        return new WorkflowProcessStartRequestService.StartReservationRequest(
                7L, "req-1", "PROCUREMENT_REQUISITION", "1001", 88L, 21L);
    }

    private InternalTaskAssignmentRequest assignmentRequest() {
        InternalTaskAssignmentRequest request = new InternalTaskAssignmentRequest();
        request.setTenantId(7L);
        request.setTaskId("task-1");
        request.setUserId(21L);
        request.setBusinessType("PROCUREMENT_REQUISITION");
        request.setBusinessKey("1001");
        return request;
    }

    private WfProcessStartRequest startRecord(String status, String processInstanceId) {
        WfProcessStartRequest record = new WfProcessStartRequest();
        record.setId(91L);
        record.setTenantId(7L);
        record.setRequestId("req-1");
        record.setBusinessType("PROCUREMENT_REQUISITION");
        record.setBusinessKey("1001");
        record.setModelVersionId(88L);
        record.setStartUserId(21L);
        record.setStatus(status);
        record.setProcessInstanceId(processInstanceId);
        return record;
    }

    private InternalModelVersionResponse modelVersion(
            String status, String processDefinitionId) {
        return InternalModelVersionResponse.builder()
                .id(88L)
                .modelId(31L)
                .modelKey("procurement-approval")
                .category("PROCUREMENT_REQUISITION")
                .version(3)
                .status(status)
                .processDefinitionId(processDefinitionId)
                .build();
    }
}
