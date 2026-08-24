package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.RetryableQuotationEventException;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.RfqContracts;
import com.omni.procurement.entity.ProcEventInbox;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqSupplier;
import com.omni.procurement.mapper.ProcEventInboxMapper;
import com.omni.procurement.mapper.ProcRfqMapper;
import com.omni.procurement.mapper.ProcRfqSupplierMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SRM 报价提交事件租户、Inbox 幂等和乱序保护测试。 */
@ExtendWith(MockitoExtension.class)
class QuotationSubmittedServiceImplTest {

    @Mock private ProcEventInboxMapper inboxMapper;
    @Mock private ProcRfqMapper rfqMapper;
    @Mock private ProcRfqSupplierMapper supplierMapper;

    private ObjectMapper objectMapper;
    private QuotationSubmittedServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcEventInbox.class, "ProcEventInboxMapper");
        initialize(ProcRfq.class, "ProcRfqMapper");
        initialize(ProcRfqSupplier.class, "ProcRfqSupplierMapper");
    }

    /** 初始化服务和消息租户上下文。 */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new QuotationSubmittedServiceImpl(
                inboxMapper, rfqMapper, supplierMapper, objectMapper);
        ServiceIdentityContext.set(new ServiceRequestIdentity(
                0L, 41L, "srm-quotation-event"));
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                0L, 41L, "srm-quotation-event", null, "TENANT", Set.of(), null));
    }

    /** 清理消息线程上下文。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 完全匹配的 typed payload 必须推进 QUOTED 并完成 Inbox。 */
    @Test
    void shouldAdvanceMatchingQuotationAndCompleteInbox() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-1", 1);
        mockInbox(event, "RECEIVED");
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(rfq(RfqStateMachine.SENT));
        when(supplierMapper.selectForUpdate(41L, 100L, 501L)).thenReturn(invitation(null));
        when(supplierMapper.update(any(), any())).thenReturn(1);
        when(inboxMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(event)).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ProcRfqSupplier>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(supplierMapper).update(any(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet())
                .contains("status", "quotation_id", "quotation_version",
                        "quotation_request_id", "quotation_time");
        assertThat(updateCaptor.getValue().getParamNameValuePairs())
                .containsValue(RfqStateMachine.QUOTED)
                .containsValue(901L)
                .containsValue(1)
                .containsValue("quote-request-1")
                .containsValue(event.getOccurredAt());
        verify(inboxMapper).update(any(), any());
    }

    /** 消费延迟到本地截止时间之后时仍应接收 SRM 已严格校验并提交的事件。 */
    @Test
    void shouldAcceptDelayedEventAfterLocalDeadline() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-delayed", 1);
        mockInbox(event, "RECEIVED");
        ProcRfq expiredLocally = rfq(RfqStateMachine.SENT);
        expiredLocally.setQuotationDeadline(LocalDateTime.now().minusDays(1));
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(expiredLocally);
        when(supplierMapper.selectForUpdate(41L, 100L, 501L)).thenReturn(invitation(null));
        when(supplierMapper.update(any(), any())).thenReturn(1);
        when(inboxMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(event)).isTrue();

        verify(supplierMapper).update(any(), any());
    }

    /** 已处理 eventId 重放必须直接幂等返回且不再锁定 RFQ。 */
    @Test
    void shouldIgnoreProcessedEventIdReplay() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-processed", 1);
        mockInbox(event, "PROCESSED");

        assertThat(service.handle(event)).isFalse();

        verify(rfqMapper, never()).selectForUpdate(any(), any());
        verify(supplierMapper, never()).update(any(), any());
    }

    /** 比本地已接收版本更旧的不同 eventId 必须标记忽略且不能回退报价版本。 */
    @Test
    void shouldIgnoreOlderQuotationVersion() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-old", 4);
        mockInbox(event, "RECEIVED");
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(rfq(RfqStateMachine.SENT));
        when(supplierMapper.selectForUpdate(41L, 100L, 501L)).thenReturn(invitation(5));
        when(inboxMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(event)).isFalse();

        verify(supplierMapper, never()).update(any(), any());
        verify(inboxMapper).update(any(), any());
    }

    /** 报价事件早于 RFQ 发送确认时必须抛可重试异常且不能完成 Inbox。 */
    @Test
    void shouldRetryWhenQuotationArrivesBeforeRfqSent() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-early", 1);
        mockInbox(event, "RECEIVED");
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(rfq(RfqStateMachine.DRAFT));
        when(supplierMapper.selectForUpdate(41L, 100L, 501L)).thenReturn(invitation(null));

        assertThatThrownBy(() -> service.handle(event))
                .isInstanceOf(RetryableQuotationEventException.class);

        verify(supplierMapper, never()).update(any(), any());
        verify(inboxMapper, never()).update(any(), any());
    }

    /** 信封租户与当前消费上下文不一致时必须失败关闭。 */
    @Test
    void shouldRejectCrossTenantEnvelope() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-cross", 1);
        event.setTenantId(42L);

        assertThatThrownBy(() -> service.handle(event))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
        verify(inboxMapper, never()).insertIgnore(any());
    }

    /** 旧式 flat event 没有 payload 时必须拒绝，不能静默消费。 */
    @Test
    void shouldRejectFlatEventWithoutTypedPayload() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-flat", 1);
        event.setPayload(null);

        assertThatThrownBy(() -> service.handle(event))
                .isInstanceOf(IllegalArgumentException.class);
        verify(inboxMapper, never()).insertIgnore(any());
    }

    /** 非 ISO 4217 三位大写币种必须在写 Inbox 前拒绝。 */
    @Test
    void shouldRejectInvalidCurrencyContract() {
        RfqContracts.QuotationSubmittedEvent event = event("evt-currency", 1);
        event.getPayload().setCurrencyCode("cny");

        assertThatThrownBy(() -> service.handle(event))
                .isInstanceOf(IllegalArgumentException.class);

        verify(inboxMapper, never()).insertIgnore(any());
    }

    private void mockInbox(RfqContracts.QuotationSubmittedEvent event, String status) {
        when(inboxMapper.selectForUpdate(event.getTenantId(), event.getEventId()))
                .thenReturn(inbox(event, status));
    }

    private ProcEventInbox inbox(RfqContracts.QuotationSubmittedEvent event, String status) {
        ProcEventInbox inbox = new ProcEventInbox();
        inbox.setId(1L);
        inbox.setTenantId(event.getTenantId());
        inbox.setEventId(event.getEventId());
        inbox.setEventType(event.getEventType());
        inbox.setSourceService(event.getProducer());
        inbox.setAggregateType(event.getAggregateType());
        inbox.setAggregateId(String.valueOf(event.getAggregateId()));
        try {
            inbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        inbox.setStatus(status);
        return inbox;
    }

    private RfqContracts.QuotationSubmittedEvent event(String eventId, int version) {
        RfqContracts.QuotationSubmittedPayload payload =
                new RfqContracts.QuotationSubmittedPayload();
        payload.setRequestId("quote-request-" + version);
        payload.setQuotationId(901L);
        payload.setQuotationVersion(version);
        payload.setRfqId(100L);
        payload.setRfqNo("RFQ-41-100");
        payload.setSupplierId(501L);
        payload.setStatus("SUBMITTED");
        payload.setTotalAmount(new BigDecimal("100.0000"));
        payload.setCurrencyCode("CNY");
        payload.setValidUntil(LocalDateTime.now().plusDays(30));

        RfqContracts.QuotationSubmittedEvent event =
                new RfqContracts.QuotationSubmittedEvent();
        event.setEventId(eventId);
        event.setEventType("srm.quotation.submitted.v1");
        event.setOccurredAt(LocalDateTime.now().minusMinutes(1));
        event.setTenantId(41L);
        event.setProducer("omni-srm");
        event.setAggregateType("QUOTATION");
        event.setAggregateId(901L);
        event.setAggregateVersion(version);
        event.setActorUserId(77L);
        event.setCorrelationId(payload.getRequestId());
        event.setPayload(payload);
        return event;
    }

    private ProcRfq rfq(String status) {
        ProcRfq rfq = new ProcRfq();
        rfq.setId(100L);
        rfq.setTenantId(41L);
        rfq.setRfqNo("RFQ-41-100");
        rfq.setCurrencyCode("CNY");
        rfq.setStatus(status);
        rfq.setQuotationDeadline(LocalDateTime.now().plusDays(10));
        rfq.setDeleted(0);
        return rfq;
    }

    private ProcRfqSupplier invitation(Integer quotationVersion) {
        ProcRfqSupplier invitation = new ProcRfqSupplier();
        invitation.setId(301L);
        invitation.setTenantId(41L);
        invitation.setRfqId(100L);
        invitation.setSupplierId(501L);
        invitation.setStatus(quotationVersion == null
                ? RfqStateMachine.INVITED : RfqStateMachine.QUOTED);
        invitation.setQuotationId(quotationVersion == null ? null : 901L);
        invitation.setQuotationVersion(quotationVersion);
        invitation.setVersion(2);
        invitation.setDeleted(0);
        return invitation;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.test." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
