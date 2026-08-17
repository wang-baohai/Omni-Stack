package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.ProcurementInternalClient;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationDetail;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationLine;
import com.omni.srm.dto.quotation.QuotationLineRequest;
import com.omni.srm.dto.quotation.QuotationSubmitRequest;
import com.omni.srm.dto.quotation.QuotationVO;
import com.omni.srm.entity.SrmQuotation;
import com.omni.srm.entity.SrmQuotationLine;
import com.omni.srm.entity.SrmQuotationRequest;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmQuotationLineMapper;
import com.omni.srm.mapper.SrmQuotationMapper;
import com.omni.srm.mapper.SrmQuotationRequestMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.SupplierPortalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SRM 供应商报价权威校验、幂等与 Outbox 测试。 */
@ExtendWith(MockitoExtension.class)
class QuotationServiceImplTest {

    @Mock
    private SrmQuotationMapper quotationMapper;

    @Mock
    private SrmQuotationLineMapper quotationLineMapper;

    @Mock
    private SrmQuotationRequestMapper quotationRequestMapper;

    @Mock
    private SrmSupplierMapper supplierMapper;

    @Mock
    private SupplierPortalService supplierPortalService;

    @Mock
    private ProcurementInternalClient procurementInternalClient;

    @Mock
    private ReliableMessageRelay reliableMessageRelay;

    private QuotationServiceImpl service;

    /** 初始化租户身份和 MyBatis 实体元数据。 */
    @BeforeEach
    void setUp() {
        initTableInfo(SrmSupplier.class);
        initTableInfo(SrmQuotation.class);
        initTableInfo(SrmQuotationLine.class);
        initTableInfo(SrmQuotationRequest.class);
        service = new QuotationServiceImpl(quotationMapper, quotationLineMapper,
                quotationRequestMapper, supplierMapper, supplierPortalService,
                procurementInternalClient, reliableMessageRelay);
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(20L, 1L, "supplier-user"));
    }

    /** 清理线程上下文。 */
    @AfterEach
    void tearDown() {
        SrmDataScopeContext.clear();
        SrmTenantContext.clear();
    }

    /** 数量、币种、供应商和金额必须全部使用服务端权威数据。 */
    @Test
    void shouldCreateQuotationFromAuthoritativeInvitationAndSendOutbox() {
        QuotationSubmitRequest request = submitRequest();
        stubApprovedSupplier();
        stubNewRequestAndInvitation(invitation(List.of(invitationLine(101L, "3.000000"))));

        QuotationVO result = service.submit(request);

        assertThat(result.getSupplierId()).isEqualTo(10L);
        assertThat(result.getCurrencyCode()).isEqualTo("CNY");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("6.3704");
        assertThat(result.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualByComparingTo("3.000000");
            assertThat(line.getUnitPrice()).isEqualByComparingTo("2.123456");
            assertThat(line.getLineAmount()).isEqualByComparingTo("6.3704");
            assertThat(line.getMaterialCode()).isEqualTo("MAT-101");
        });
        ArgumentCaptor<SrmQuotation> quotationCaptor = ArgumentCaptor.forClass(SrmQuotation.class);
        verify(quotationMapper).insert(quotationCaptor.capture());
        assertThat(quotationCaptor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(quotationCaptor.getValue().getSupplierNameSnapshot()).isEqualTo("Approved Supplier");
        verify(supplierMapper).selectVisibleForUpdate(10L);
        ArgumentCaptor<DomainEventEnvelope> eventCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(reliableMessageRelay).send(eq("srm-domain-out-0"), eventCaptor.capture(), eq(1L), anyString());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("srm.quotation.submitted.v1");
        assertThat(eventCaptor.getValue().getPayload()).containsEntry("requestId", "quote-request-1")
                .containsEntry("quotationId", 40L)
                .containsEntry("quotationVersion", 1)
                .containsEntry("totalAmount", "6.3704");
    }

    /** 非 APPROVED 供应商必须在访问 Procurement 前失败关闭。 */
    @Test
    void shouldRejectSupplierThatIsNotApproved() {
        when(supplierPortalService.getCurrentSupplierId()).thenReturn(10L);
        SrmSupplier supplier = supplier("SUSPENDED");
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(supplier);

        assertThatThrownBy(() -> service.submit(submitRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));

        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
        verify(quotationMapper, never()).insert(any(SrmQuotation.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 报价行必须与 Procurement 返回的 RFQ 行集合完全一致。 */
    @Test
    void shouldRejectIncompleteRfqLines() {
        stubApprovedSupplier();
        stubRequestReservationAndInvitation(invitation(List.of(
                invitationLine(101L, "3.000000"), invitationLine(102L, "2.000000"))));

        assertThatThrownBy(() -> service.submit(submitRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));

        verify(quotationMapper, never()).insert(any(SrmQuotation.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 完成的相同 requestId 与相同请求体只能读取结果，不能再次写库或发事件。 */
    @Test
    void shouldReplayCompletedRequestWithoutDuplicateSideEffects() {
        QuotationSubmitRequest request = submitRequest();
        stubPortalSupplier();
        SrmQuotationRequest history = completedRequest(request, 1);
        when(quotationRequestMapper.selectOne(any())).thenReturn(history);
        SrmQuotation quotation = quotation(1);
        when(quotationMapper.selectOne(any())).thenReturn(quotation);
        when(quotationLineMapper.selectList(any())).thenReturn(List.of(savedLine()));

        QuotationVO result = service.submit(request);

        assertThat(result.getVersion()).isEqualTo(1);
        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
        verify(quotationRequestMapper, never()).insert(any(SrmQuotationRequest.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** requestId 相同但请求体不同必须返回冲突。 */
    @Test
    void shouldRejectReusedRequestIdWithDifferentPayload() {
        QuotationSubmitRequest request = submitRequest();
        stubPortalSupplier();
        SrmQuotationRequest history = completedRequest(request, 1);
        history.setRequestHash("different-hash");
        when(quotationRequestMapper.selectOne(any())).thenReturn(history);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));

        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 历史幂等结果被后续版本更新后返回当前快照，且不重复产生副作用。 */
    @Test
    void shouldReturnCurrentSnapshotWhenCompletedVersionWasSuperseded() {
        QuotationSubmitRequest request = submitRequest();
        stubPortalSupplier();
        when(quotationRequestMapper.selectOne(any())).thenReturn(completedRequest(request, 1));
        when(quotationMapper.selectOne(any())).thenReturn(quotation(2));
        when(quotationLineMapper.selectList(any())).thenReturn(List.of(savedLine()));

        QuotationVO result = service.submit(request);

        assertThat(result.getVersion()).isEqualTo(2);
        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 历史请求即使有效期已过，也必须先按幂等历史处理而不是当成新请求校验。 */
    @Test
    void shouldReplayExpiredHistoricalRequestBeforeFreshValidityValidation() {
        QuotationSubmitRequest request = submitRequest();
        request.setValidUntil(LocalDateTime.now().minusDays(1));
        stubPortalSupplier();
        when(quotationRequestMapper.selectOne(any())).thenReturn(completedRequest(request, 1));
        when(quotationMapper.selectOne(any())).thenReturn(quotation(1));
        when(quotationLineMapper.selectList(any())).thenReturn(List.of(savedLine()));

        QuotationVO result = service.submit(request);

        assertThat(result.getId()).isEqualTo(40L);
        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 单价必须严格大于零。 */
    @Test
    void shouldRejectZeroUnitPrice() {
        QuotationSubmitRequest request = submitRequest();
        request.getLines().getFirst().setUnitPrice(BigDecimal.ZERO);
        stubPortalSupplier();

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));

        verify(quotationRequestMapper, never()).insert(any(SrmQuotationRequest.class));
        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
    }

    /** 行金额按四位小数舍入为零时必须在写库前拒绝，避免触发数据库约束异常。 */
    @Test
    void shouldRejectLineAmountRoundedToZero() {
        QuotationSubmitRequest request = submitRequest();
        request.getLines().getFirst().setUnitPrice(new BigDecimal("0.000001"));
        stubApprovedSupplier();
        stubRequestReservationAndInvitation(invitation(List.of(invitationLine(101L, "0.000001"))));

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));

        verify(quotationMapper, never()).insert(any(SrmQuotation.class));
        verify(quotationLineMapper, never()).insert(any(SrmQuotationLine.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 报价必须至少持续到 RFQ 截止时间，避免开标时报价已经失效。 */
    @Test
    void shouldRejectValidityBeforeQuotationDeadline() {
        QuotationSubmitRequest request = submitRequest();
        request.setValidUntil(LocalDateTime.now().plusDays(2));
        stubApprovedSupplier();
        stubRequestReservationAndInvitation(invitation(List.of(invitationLine(101L, "3.000000"))));

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));

        verify(quotationMapper, never()).insert(any(SrmQuotation.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 内部比价批次不得返回报价后已被暂停或淘汰的供应商。 */
    @Test
    void shouldExcludeNonApprovedSupplierFromValidQuotationBatch() {
        when(quotationMapper.selectList(any())).thenReturn(List.of(quotation(1)));
        when(supplierMapper.selectList(any())).thenReturn(List.of());

        List<QuotationVO> result = service.listValidByRfq(1L, 100L);

        assertThat(result).isEmpty();
        verify(quotationLineMapper, never()).selectList(any());
    }

    /** 已完成请求在供应商状态变化后仍应无副作用重放，便于解析丢失的原响应。 */
    @Test
    void shouldReplayCompletedRequestAfterSupplierWasSuspended() {
        QuotationSubmitRequest request = submitRequest();
        stubPortalSupplier();
        when(quotationRequestMapper.selectOne(any())).thenReturn(completedRequest(request, 1));
        when(quotationMapper.selectOne(any())).thenReturn(quotation(1));
        when(quotationLineMapper.selectList(any())).thenReturn(List.of(savedLine()));

        QuotationVO result = service.submit(request);

        assertThat(result.getVersion()).isEqualTo(1);
        verify(supplierMapper, never()).selectVisibleForUpdate(any());
        verify(procurementInternalClient, never()).getInvitation(any(), any(), any());
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    /** 已存在首版报价时再次携带创建哨兵 version=0 必须冲突，不能覆盖首个请求。 */
    @Test
    void shouldRejectCreateSentinelWhenInitialQuotationAlreadyExists() {
        QuotationSubmitRequest request = submitRequest();
        stubApprovedSupplier();
        stubRequestReservationAndInvitation(invitation(List.of(invitationLine(101L, "3.000000"))));
        when(quotationMapper.selectForUpdate(1L, 100L, 10L)).thenReturn(quotation(1));

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));

        verify(quotationMapper, never()).update(any(), any());
        verify(quotationLineMapper, never()).insert(any(SrmQuotationLine.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), any(), anyString());
    }

    private void stubApprovedSupplier() {
        stubPortalSupplier();
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(supplier("APPROVED"));
    }

    private void stubPortalSupplier() {
        when(supplierPortalService.getCurrentSupplierId()).thenReturn(10L);
    }

    private void stubNewRequestAndInvitation(ProcurementRfqInvitationDetail invitation) {
        stubRequestReservationAndInvitation(invitation);
        when(quotationMapper.selectForUpdate(1L, 100L, 10L)).thenReturn(null);
        when(quotationMapper.insert(any(SrmQuotation.class))).thenAnswer(invocation -> {
            SrmQuotation entity = invocation.getArgument(0);
            entity.setId(40L);
            return 1;
        });
        AtomicLong lineId = new AtomicLong(50L);
        when(quotationLineMapper.insert(any(SrmQuotationLine.class))).thenAnswer(invocation -> {
            SrmQuotationLine entity = invocation.getArgument(0);
            entity.setId(lineId.getAndIncrement());
            return 1;
        });
        when(quotationRequestMapper.update(eq(null), any())).thenReturn(1);
    }

    private void stubRequestReservationAndInvitation(ProcurementRfqInvitationDetail invitation) {
        when(quotationRequestMapper.selectOne(any())).thenReturn(null);
        when(quotationRequestMapper.insert(any(SrmQuotationRequest.class))).thenAnswer(invocation -> {
            SrmQuotationRequest entity = invocation.getArgument(0);
            entity.setId(30L);
            return 1;
        });
        when(procurementInternalClient.getInvitation(1L, 100L, 10L)).thenReturn(R.ok(invitation));
    }

    private QuotationSubmitRequest submitRequest() {
        QuotationLineRequest line = new QuotationLineRequest();
        line.setRfqLineId(101L);
        line.setUnitPrice(new BigDecimal("2.123456"));
        line.setDeliveryDays(7);
        line.setRemark("standard delivery");
        QuotationSubmitRequest request = new QuotationSubmitRequest();
        request.setRequestId("quote-request-1");
        request.setRfqId(100L);
        request.setVersion(0);
        request.setValidUntil(LocalDateTime.now().plusDays(4));
        request.setLines(List.of(line));
        return request;
    }

    private ProcurementRfqInvitationDetail invitation(List<ProcurementRfqInvitationLine> lines) {
        ProcurementRfqInvitationDetail detail = new ProcurementRfqInvitationDetail();
        detail.setTenantId(1L);
        detail.setSupplierId(10L);
        detail.setRfqId(100L);
        detail.setRfqNo("RFQ-100");
        detail.setTitle("Office equipment");
        detail.setStatus("SENT");
        detail.setInvitationStatus("INVITED");
        detail.setQuotationDeadline(LocalDateTime.now().plusDays(3));
        detail.setCurrencyCode("cny");
        detail.setInvitedTime(LocalDateTime.now().minusHours(1));
        detail.setLines(lines);
        return detail;
    }

    private ProcurementRfqInvitationLine invitationLine(Long id, String quantity) {
        ProcurementRfqInvitationLine line = new ProcurementRfqInvitationLine();
        line.setRfqLineId(id);
        line.setMaterialCode("MAT-" + id);
        line.setMaterialName("Material " + id);
        line.setUnit("EA");
        line.setQuantity(new BigDecimal(quantity));
        line.setRemark("buyer remark");
        return line;
    }

    private SrmSupplier supplier(String status) {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setId(10L);
        supplier.setTenantId(1L);
        supplier.setName("Approved Supplier");
        supplier.setStatus(status);
        supplier.setVersion(0);
        supplier.setDeleted(0);
        return supplier;
    }

    private SrmQuotationRequest completedRequest(QuotationSubmitRequest request, int targetVersion) {
        SrmQuotationRequest history = new SrmQuotationRequest();
        history.setId(30L);
        history.setTenantId(1L);
        history.setRequestId(request.getRequestId());
        history.setQuotationId(40L);
        history.setRfqId(100L);
        history.setSupplierId(10L);
        history.setRequestHash(service.hashRequest(request));
        history.setTargetVersion(targetVersion);
        history.setStatus("COMPLETED");
        return history;
    }

    private SrmQuotation quotation(int version) {
        SrmQuotation quotation = new SrmQuotation();
        quotation.setId(40L);
        quotation.setTenantId(1L);
        quotation.setSupplierId(10L);
        quotation.setSupplierNameSnapshot("Approved Supplier");
        quotation.setRfqId(100L);
        quotation.setRfqNo("RFQ-100");
        quotation.setRequestId("quote-request-1");
        quotation.setQuotationTime(LocalDateTime.now().minusHours(1));
        quotation.setValidUntil(LocalDateTime.now().plusDays(1));
        quotation.setTotalAmount(new BigDecimal("6.3704"));
        quotation.setCurrencyCode("CNY");
        quotation.setStatus("SUBMITTED");
        quotation.setVersion(version);
        quotation.setDeleted(0);
        return quotation;
    }

    private SrmQuotationLine savedLine() {
        SrmQuotationLine line = new SrmQuotationLine();
        line.setId(50L);
        line.setTenantId(1L);
        line.setQuotationId(40L);
        line.setRfqLineId(101L);
        line.setMaterialCode("MAT-101");
        line.setMaterialName("Material 101");
        line.setUnit("EA");
        line.setUnitPrice(new BigDecimal("2.123456"));
        line.setQuantity(new BigDecimal("3.000000"));
        line.setLineAmount(new BigDecimal("6.3704"));
        line.setDeliveryDays(7);
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "quotation-service-test-" + entityClass.getSimpleName());
        assistant.setCurrentNamespace("quotation-service-test-" + entityClass.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
