package com.omni.workflow.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.workflow.entity.WfProcessStartRequest;
import com.omni.workflow.mapper.WfProcessStartRequestMapper;
import com.omni.workflow.service.WorkflowProcessStartRequestService.Reservation;
import com.omni.workflow.service.WorkflowProcessStartRequestService.StartReservationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程启动幂等请求服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowProcessStartRequestServiceImplTest {

    private static final Long TENANT_ID = 12L;
    private static final Long MODEL_VERSION_ID = 23L;
    private static final Long START_USER_ID = 34L;
    private static final String REQUEST_ID = "request-1";
    private static final String BUSINESS_TYPE = "PROCUREMENT_REQUISITION";
    private static final String BUSINESS_KEY = "1001";

    @Mock
    private WfProcessStartRequestMapper requestMapper;

    @InjectMocks
    private WorkflowProcessStartRequestServiceImpl requestService;

    /** 首次插入成功时当前调用获得启动资格。 */
    @Test
    void shouldAcquireNewReservation() {
        when(requestMapper.insertIgnore(any(WfProcessStartRequest.class))).thenAnswer(invocation -> {
            WfProcessStartRequest request = invocation.getArgument(0);
            request.setId(101L);
            return 1;
        });

        Reservation result = reserve(REQUEST_ID);

        assertThat(result.acquired()).isTrue();
        assertThat(result.created()).isTrue();
        assertThat(result.request().getId()).isEqualTo(101L);
        assertThat(result.request().getStatus()).isEqualTo(WfProcessStartRequest.STATUS_RESERVED);
        assertThat(result.request().getRetryCount()).isZero();
        ArgumentCaptor<WfProcessStartRequest> captor =
                ArgumentCaptor.forClass(WfProcessStartRequest.class);
        verify(requestMapper).insertIgnore(captor.capture());
        assertThat(captor.getValue().getRequestId()).isEqualTo(REQUEST_ID);
    }

    /** 同一请求已启动时返回原记录，不再获得执行资格。 */
    @Test
    void shouldReplayStartedRequestWithoutAcquiring() {
        WfProcessStartRequest existing = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_STARTED);
        existing.setProcessInstanceId("process-1");
        when(requestMapper.insertIgnore(any(WfProcessStartRequest.class))).thenReturn(0);
        when(requestMapper.selectByRequestId(TENANT_ID, REQUEST_ID)).thenReturn(existing);
        when(requestMapper.selectByBusinessKey(TENANT_ID, BUSINESS_TYPE, BUSINESS_KEY))
                .thenReturn(existing);

        Reservation result = reserve(REQUEST_ID);

        assertThat(result.acquired()).isFalse();
        assertThat(result.created()).isFalse();
        assertThat(result.request().getProcessInstanceId()).isEqualTo("process-1");
    }

    /** 同一请求的失败记录只能被一个重试调用原子抢占。 */
    @Test
    void shouldAcquireFailedReplayForRetry() {
        WfProcessStartRequest failed = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_FAILED);
        WfProcessStartRequest reserved = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_RESERVED);
        reserved.setRetryCount(1);
        when(requestMapper.insertIgnore(any(WfProcessStartRequest.class))).thenReturn(0);
        when(requestMapper.selectByRequestId(TENANT_ID, REQUEST_ID)).thenReturn(failed);
        when(requestMapper.selectByBusinessKey(TENANT_ID, BUSINESS_TYPE, BUSINESS_KEY))
                .thenReturn(failed);
        when(requestMapper.reserveRetry(anyLong(), anyLong(), any())).thenReturn(1);
        when(requestMapper.selectByTenantAndId(TENANT_ID, 101L)).thenReturn(reserved);

        Reservation result = reserve(REQUEST_ID);

        assertThat(result.acquired()).isTrue();
        assertThat(result.created()).isFalse();
        assertThat(result.request().getStatus()).isEqualTo(WfProcessStartRequest.STATUS_RESERVED);
        assertThat(result.request().getRetryCount()).isEqualTo(1);
    }

    /** 业务键相同但请求 ID 不同时复用已有流程记录。 */
    @Test
    void shouldReplayExistingBusinessWithDifferentRequestId() {
        WfProcessStartRequest existing = request(101L, "original-request",
                WfProcessStartRequest.STATUS_STARTED);
        existing.setProcessInstanceId("process-1");
        when(requestMapper.insertIgnore(any(WfProcessStartRequest.class))).thenReturn(0);
        when(requestMapper.selectByRequestId(TENANT_ID, "new-request")).thenReturn(null);
        when(requestMapper.selectByBusinessKey(TENANT_ID, BUSINESS_TYPE, BUSINESS_KEY))
                .thenReturn(existing);

        Reservation result = reserve("new-request");

        assertThat(result.acquired()).isFalse();
        assertThat(result.request().getProcessInstanceId()).isEqualTo("process-1");
    }

    /** 请求 ID 与业务键命中不同记录时拒绝组合两个幂等身份。 */
    @Test
    void shouldRejectCrossedUniqueKeyCollision() {
        WfProcessStartRequest byRequest = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_STARTED);
        WfProcessStartRequest byBusiness = request(202L, "other-request",
                WfProcessStartRequest.STATUS_STARTED);
        when(requestMapper.insertIgnore(any(WfProcessStartRequest.class))).thenReturn(0);
        when(requestMapper.selectByRequestId(TENANT_ID, REQUEST_ID)).thenReturn(byRequest);
        when(requestMapper.selectByBusinessKey(TENANT_ID, BUSINESS_TYPE, BUSINESS_KEY))
                .thenReturn(byBusiness);

        assertThatThrownBy(() -> reserve(REQUEST_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));
    }

    /** 显式重试通过状态条件更新保证并发只有一个赢家。 */
    @Test
    void shouldReportLostConcurrentRetryWithoutAcquiring() {
        WfProcessStartRequest failed = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_FAILED);
        WfProcessStartRequest started = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_STARTED);
        started.setProcessInstanceId("process-1");
        when(requestMapper.selectByTenantAndId(TENANT_ID, 101L)).thenReturn(failed, started);
        when(requestMapper.reserveRetry(anyLong(), anyLong(), any())).thenReturn(0);

        Reservation result = requestService.retry(TENANT_ID, 101L);

        assertThat(result.acquired()).isFalse();
        assertThat(result.request().getStatus()).isEqualTo(WfProcessStartRequest.STATUS_STARTED);
    }

    /** 重复写入同一流程实例 ID 视为成功，写入其他实例则冲突。 */
    @Test
    void shouldMakeMarkStartedIdempotent() {
        WfProcessStartRequest started = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_STARTED);
        started.setProcessInstanceId("process-1");
        when(requestMapper.markStarted(anyLong(), anyLong(), anyString(), any())).thenReturn(0);
        when(requestMapper.selectByTenantAndId(TENANT_ID, 101L)).thenReturn(started);

        assertThatCode(() -> requestService.markStarted(TENANT_ID, 101L, "process-1"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> requestService.markStarted(TENANT_ID, 101L, "process-2"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));
    }

    /** 迟到的失败标记不得把已启动记录降级为失败。 */
    @Test
    void shouldNotDowngradeStartedRequestToFailed() {
        WfProcessStartRequest started = request(101L, REQUEST_ID,
                WfProcessStartRequest.STATUS_STARTED);
        started.setProcessInstanceId("process-1");
        when(requestMapper.markFailed(anyLong(), anyLong(), anyString(), any())).thenReturn(0);
        when(requestMapper.selectByTenantAndId(TENANT_ID, 101L)).thenReturn(started);

        assertThatCode(() -> requestService.markFailed(TENANT_ID, 101L, "timeout"))
                .doesNotThrowAnyException();
    }

    private Reservation reserve(String requestId) {
        return requestService.reserve(new StartReservationRequest(
                TENANT_ID, requestId, BUSINESS_TYPE, BUSINESS_KEY,
                MODEL_VERSION_ID, START_USER_ID));
    }

    private WfProcessStartRequest request(Long id, String requestId, String status) {
        WfProcessStartRequest request = new WfProcessStartRequest();
        request.setId(id);
        request.setTenantId(TENANT_ID);
        request.setRequestId(requestId);
        request.setBusinessType(BUSINESS_TYPE);
        request.setBusinessKey(BUSINESS_KEY);
        request.setModelVersionId(MODEL_VERSION_ID);
        request.setStartUserId(START_USER_ID);
        request.setStatus(status);
        request.setRetryCount(0);
        return request;
    }
}
