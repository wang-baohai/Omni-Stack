package com.omni.workflow.service.impl;

import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.WorkflowCompletionResult;
import com.omni.workflow.dto.WorkflowProcessCompletedEvent;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流完成事件服务测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowCompletionEventServiceImplTest {

    @Mock
    private WfProcessInstanceExtMapper processInstanceExtMapper;
    @Mock
    private ReliableMessageRelay reliableMessageRelay;

    @Test
    void shouldMarkCompletionAndWriteApprovedEvent() {
        WorkflowCompletionEventServiceImpl service = service();
        WfProcessInstanceExt ext = ext();
        LocalDateTime completedTime = LocalDateTime.of(2026, 7, 21, 10, 30);
        when(processInstanceExtMapper.selectOne(any())).thenReturn(ext);
        when(processInstanceExtMapper.markCompletionForEvent(any())).thenReturn(1);

        boolean published = service.publishCompletionEvent(
                1L, "proc-1", WorkflowCompletionResult.APPROVED, completedTime);

        assertTrue(published);
        ArgumentCaptor<WfProcessInstanceExtMapper.CompletionEventUpdate> updateCaptor =
                ArgumentCaptor.forClass(WfProcessInstanceExtMapper.CompletionEventUpdate.class);
        verify(processInstanceExtMapper).markCompletionForEvent(updateCaptor.capture());
        assertEquals(1L, updateCaptor.getValue().tenantId());
        assertEquals("proc-1", updateCaptor.getValue().processInstanceId());
        assertEquals("APPROVED", updateCaptor.getValue().completionResult());
        assertEquals(2, updateCaptor.getValue().status());
        assertEquals(completedTime, updateCaptor.getValue().completedTime());
        ArgumentCaptor<WorkflowProcessCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(WorkflowProcessCompletedEvent.class);
        ArgumentCaptor<String> messageKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(reliableMessageRelay).send(eq("workflow-domain-out-0"), eventCaptor.capture(),
                eq(1L), messageKeyCaptor.capture());
        WorkflowProcessCompletedEvent event = eventCaptor.getValue();
        assertEquals(event.getEventId(), messageKeyCaptor.getValue());
        assertEquals("workflow.process.completed.v1", event.getEventType());
        assertEquals("omni-workflow", event.getProducer());
        assertEquals("PROCUREMENT_REQUISITION", event.getBusinessType());
        assertEquals("101", event.getBusinessKey());
        assertEquals("proc-1", event.getProcessInstanceId());
        assertEquals(WorkflowCompletionResult.APPROVED, event.getResult());
        assertEquals(completedTime, event.getCompletedTime());
    }

    @Test
    void shouldSkipWhenEventWasAlreadyPublished() {
        WorkflowCompletionEventServiceImpl service = service();
        WfProcessInstanceExt ext = ext();
        ext.setCompletionEventId("existing-event");
        when(processInstanceExtMapper.selectOne(any())).thenReturn(ext);

        boolean published = service.publishCompletionEvent(
                1L, "proc-1", WorkflowCompletionResult.REJECTED, LocalDateTime.now());

        assertFalse(published);
        verify(processInstanceExtMapper, never()).markCompletionForEvent(any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    @Test
    void shouldSkipWhenConcurrentCallerWonTheDatabaseGate() {
        WorkflowCompletionEventServiceImpl service = service();
        when(processInstanceExtMapper.selectOne(any())).thenReturn(ext());
        when(processInstanceExtMapper.markCompletionForEvent(any())).thenReturn(0);

        boolean published = service.publishCompletionEvent(
                1L, "proc-1", WorkflowCompletionResult.CANCELLED, LocalDateTime.now());

        assertFalse(published);
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    @Test
    void shouldFailWhenProcessExtensionDoesNotExist() {
        WorkflowCompletionEventServiceImpl service = service();
        when(processInstanceExtMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.publishCompletionEvent(
                        1L, "missing", WorkflowCompletionResult.APPROVED, LocalDateTime.now()));

        assertEquals(404, exception.getCode());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    @Test
    void shouldFailWhenBusinessTypeIsMissing() {
        WorkflowCompletionEventServiceImpl service = service();
        WfProcessInstanceExt ext = ext();
        ext.setBusinessType(null);
        when(processInstanceExtMapper.selectOne(any())).thenReturn(ext);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.publishCompletionEvent(
                        1L, "proc-1", WorkflowCompletionResult.APPROVED, LocalDateTime.now()));

        assertEquals(500, exception.getCode());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    private WorkflowCompletionEventServiceImpl service() {
        return new WorkflowCompletionEventServiceImpl(processInstanceExtMapper, reliableMessageRelay);
    }

    private WfProcessInstanceExt ext() {
        WfProcessInstanceExt ext = new WfProcessInstanceExt();
        ext.setId(10L);
        ext.setTenantId(1L);
        ext.setProcessInstanceId("proc-1");
        ext.setProcessKey("procurement-approval");
        ext.setBusinessType("PROCUREMENT_REQUISITION");
        ext.setCategory("PROCUREMENT_REQUISITION");
        ext.setBusinessKey("101");
        ext.setStatus(2);
        return ext;
    }
}
