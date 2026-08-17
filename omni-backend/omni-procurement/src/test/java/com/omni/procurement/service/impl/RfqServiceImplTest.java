package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.client.SrmInternalClient;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.PurchaseOrderRequests;
import com.omni.procurement.dto.PurchaseOrderViews;
import com.omni.procurement.dto.RfqContracts;
import com.omni.procurement.dto.RfqRequests;
import com.omni.procurement.dto.RfqViews;
import com.omni.procurement.dto.SrmSupplierContracts;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;
import com.omni.procurement.entity.ProcRfqSupplier;
import com.omni.procurement.mapper.ProcRequisitionLineMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.procurement.mapper.ProcRfqLineMapper;
import com.omni.procurement.mapper.ProcRfqMapper;
import com.omni.procurement.mapper.ProcRfqSupplierMapper;
import com.omni.procurement.security.ProcDataScopeContext;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.PurchaseOrderService;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RFQ 创建快照、供应商校验及发送 Outbox 测试。 */
@ExtendWith(MockitoExtension.class)
class RfqServiceImplTest {

    @Mock private ProcRfqMapper rfqMapper;
    @Mock private ProcRfqLineMapper lineMapper;
    @Mock private ProcRfqSupplierMapper supplierMapper;
    @Mock private ProcRequisitionMapper requisitionMapper;
    @Mock private ProcRequisitionLineMapper requisitionLineMapper;
    @Mock private SrmInternalClient srmInternalClient;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private RfqServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcRfq.class, "ProcRfqMapper");
        initialize(ProcRfqLine.class, "ProcRfqLineMapper");
        initialize(ProcRfqSupplier.class, "ProcRfqSupplierMapper");
        initialize(ProcRequisition.class, "ProcRequisitionMapper");
        initialize(ProcRequisitionLine.class, "ProcRequisitionLineMapper");
    }

    /** 初始化服务和采购员租户上下文。 */
    @BeforeEach
    void setUp() {
        service = new RfqServiceImpl(rfqMapper, lineMapper, supplierMapper,
                requisitionMapper, requisitionLineMapper, srmInternalClient,
                purchaseOrderService, reliableMessageRelay, new ProcRecordAccessGuard());
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(7L, 41L, "buyer"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                7L, 41L, "procurement:rfq:create", 12L, "SELF", Set.of(12L)));
    }

    /** 清理请求线程上下文。 */
    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 创建必须复制已审批请购行、服务端负责人和合格供应商名称快照。 */
    @Test
    void shouldCreateDraftFromApprovedRequisitionSnapshots() {
        when(requisitionMapper.selectOne(any())).thenReturn(approvedRequisition());
        when(requisitionLineMapper.selectList(any())).thenReturn(List.of(requisitionLine()));
        when(srmInternalClient.batch(any(), any())).thenReturn(R.ok(List.of(approvedSupplier())));
        doAnswer(invocation -> {
            ProcRfq rfq = invocation.getArgument(0);
            rfq.setId(100L);
            return 1;
        }).when(rfqMapper).insert(any(ProcRfq.class));
        doAnswer(invocation -> {
            ProcRfqLine line = invocation.getArgument(0);
            line.setId(201L);
            return 1;
        }).when(lineMapper).insert(any(ProcRfqLine.class));
        doAnswer(invocation -> {
            ProcRfqSupplier invitation = invocation.getArgument(0);
            invitation.setId(301L);
            return 1;
        }).when(supplierMapper).insert(any(ProcRfqSupplier.class));
        when(rfqMapper.update(any(), any())).thenReturn(1);

        RfqViews.Detail result = service.create(createRequest());

        assertThat(result.getStatus()).isEqualTo(RfqStateMachine.DRAFT);
        assertThat(result.getRfqNo()).isEqualTo("RFQ-41-100");
        assertThat(result.getOwnerUserId()).isEqualTo(7L);
        assertThat(result.getOwnerUnitId()).isEqualTo(12L);
        assertThat(result.getCurrencyCode()).isEqualTo("CNY");
        assertThat(result.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getMaterialCode()).isEqualTo("NB-001");
            assertThat(line.getCategoryCode()).isEqualTo("IT");
            assertThat(line.getQuantity()).isEqualByComparingTo("2.000000");
        });
        assertThat(result.getSuppliers()).singleElement().satisfies(invitation -> {
            assertThat(invitation.getSupplierId()).isEqualTo(501L);
            assertThat(invitation.getSupplierName()).isEqualTo("合格供应商");
            assertThat(invitation.getInvitedTime()).isNull();
        });
        ArgumentCaptor<SrmSupplierContracts.BatchRequest> requestCaptor =
                ArgumentCaptor.forClass(SrmSupplierContracts.BatchRequest.class);
        verify(srmInternalClient).batch(
                org.mockito.ArgumentMatchers.eq(41L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTenantId()).isEqualTo(41L);
        assertThat(requestCaptor.getValue().getSupplierIds()).containsExactly(501L);
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 非 APPROVED 供应商必须阻断创建且不能保存任何 RFQ。 */
    @Test
    void shouldRejectNonApprovedSupplier() {
        when(requisitionMapper.selectOne(any())).thenReturn(approvedRequisition());
        when(requisitionLineMapper.selectList(any())).thenReturn(List.of(requisitionLine()));
        SrmSupplierContracts.Summary blocked = approvedSupplier();
        blocked.setStatus("SUSPENDED");
        when(srmInternalClient.batch(any(), any())).thenReturn(R.ok(List.of(blocked)));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(rfqMapper, never()).insert(any(ProcRfq.class));
        verify(lineMapper, never()).insert(any(ProcRfqLine.class));
        verify(supplierMapper, never()).insert(any(ProcRfqSupplier.class));
    }

    /** 重复供应商 ID 必须在调用 SRM 和写库前以 400 拒绝。 */
    @Test
    void shouldRejectDuplicateSupplierIdsBeforeRemoteCall() {
        when(requisitionMapper.selectOne(any())).thenReturn(approvedRequisition());
        when(requisitionLineMapper.selectList(any())).thenReturn(List.of(requisitionLine()));
        RfqRequests.CreateRequest request = createRequest();
        request.setSupplierIds(List.of(501L, 501L));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);

        verify(srmInternalClient, never()).batch(any(), any());
        verify(rfqMapper, never()).insert(any(ProcRfq.class));
    }

    /** 供应商选项必须经 SRM 当前租户 APPROVED 搜索并在本地按关键词过滤。 */
    @Test
    void shouldReturnPiiFreeApprovedSupplierOptions() {
        SrmSupplierContracts.Summary approved = approvedSupplier();
        approved.setLevelCode("A");
        approved.setCategoryCode("IT");
        SrmSupplierContracts.Summary suspended = approvedSupplier();
        suspended.setId(502L);
        suspended.setSupplierNo("SUP-502");
        suspended.setName("暂停供应商");
        suspended.setStatus("SUSPENDED");
        when(srmInternalClient.search(any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(R.ok(List.of(approved, suspended)));
        RfqRequests.SupplierOptionQuery query = new RfqRequests.SupplierOptionQuery();
        query.setKeyword("合格");
        query.setCategoryCode("it");
        query.setLimit(10);

        List<RfqViews.SupplierOption> result = service.supplierOptions(query);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.getId()).isEqualTo(501L);
            assertThat(option.getSupplierNo()).isEqualTo("SUP-501");
            assertThat(option.getName()).isEqualTo("合格供应商");
            assertThat(option.getLevelCode()).isEqualTo("A");
            assertThat(option.getCategoryCode()).isEqualTo("IT");
        });
        verify(srmInternalClient).search(41L, 41L, "APPROVED", "IT", 100);
    }

    /** SRM 搜索异常响应必须向外转换为 503，不能返回陈旧或未校验选项。 */
    @Test
    void shouldFailSupplierOptionsWhenSrmUnavailable() {
        when(srmInternalClient.search(any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.supplierOptions(
                new RfqRequests.SupplierOptionQuery()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
    }

    /** 草稿更新必须条件更新根并替换尚未发送的邀请。 */
    @Test
    void shouldUpdateDraftAndReplaceInvitations() {
        ProcRfq draft = draftRfq(RfqStateMachine.DRAFT);
        ProcRfq updated = draftRfq(RfqStateMachine.DRAFT);
        updated.setTitle("更新后的询价");
        updated.setVersion(1);
        SrmSupplierContracts.Summary supplier = approvedSupplier();
        supplier.setId(502L);
        supplier.setSupplierNo("SUP-502");
        supplier.setName("新供应商");
        ProcRfqSupplier updatedInvitation = invitation(null);
        updatedInvitation.setSupplierId(502L);
        updatedInvitation.setSupplierNameSnapshot("新供应商");
        when(rfqMapper.selectOne(any())).thenReturn(draft, updated);
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(draft);
        when(srmInternalClient.batch(any(), any())).thenReturn(R.ok(List.of(supplier)));
        when(rfqMapper.update(any(), any())).thenReturn(1);
        when(supplierMapper.update(any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            ProcRfqSupplier value = invocation.getArgument(0);
            value.setId(302L);
            return 1;
        }).when(supplierMapper).insert(any(ProcRfqSupplier.class));
        when(lineMapper.selectList(any())).thenReturn(List.of(rfqLine()));
        when(supplierMapper.selectList(any())).thenReturn(List.of(updatedInvitation));
        RfqRequests.UpdateRequest request = new RfqRequests.UpdateRequest();
        request.setVersion(0);
        request.setTitle("更新后的询价");
        request.setQuotationDeadline(LocalDateTime.now().plusDays(6));
        request.setSupplierIds(List.of(502L));

        RfqViews.Detail result = service.update(100L, request);

        assertThat(result.getTitle()).isEqualTo("更新后的询价");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getSuppliers()).singleElement()
                .extracting(RfqViews.SupplierInvitation::getSupplierId)
                .isEqualTo(502L);
        verify(supplierMapper).update(any(), any());
        verify(supplierMapper).insert(any(ProcRfqSupplier.class));
    }

    /** 删除草稿必须软删除询价行、邀请和聚合根。 */
    @Test
    void shouldSoftDeleteDraftAggregate() {
        when(rfqMapper.selectForUpdate(41L, 100L))
                .thenReturn(draftRfq(RfqStateMachine.DRAFT));
        when(lineMapper.update(any(), any())).thenReturn(1);
        when(supplierMapper.update(any(), any())).thenReturn(1);
        when(rfqMapper.update(any(), any())).thenReturn(1);

        service.delete(100L, 0);

        verify(lineMapper).update(any(), any());
        verify(supplierMapper).update(any(), any());
        verify(rfqMapper).update(any(), any());
    }

    /** 取消已发送询价必须将全部活动邀请标记失效并保留历史。 */
    @Test
    void shouldCancelSentRfqAndExpireInvitations() {
        ProcRfq sent = draftRfq(RfqStateMachine.SENT);
        sent.setSentTime(LocalDateTime.now().minusHours(1));
        ProcRfq cancelled = draftRfq(RfqStateMachine.CANCELLED);
        cancelled.setSentTime(sent.getSentTime());
        cancelled.setVersion(1);
        ProcRfqSupplier expired = invitation(sent.getSentTime());
        expired.setStatus(RfqStateMachine.EXPIRED);
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(sent);
        when(rfqMapper.update(any(), any())).thenReturn(1);
        when(supplierMapper.update(any(), any())).thenReturn(1);
        when(rfqMapper.selectOne(any())).thenReturn(cancelled);
        when(lineMapper.selectList(any())).thenReturn(List.of(rfqLine()));
        when(supplierMapper.selectList(any())).thenReturn(List.of(expired));

        RfqViews.Detail result = service.cancel(100L, 0);

        assertThat(result.getStatus()).isEqualTo(RfqStateMachine.CANCELLED);
        assertThat(result.getSuppliers()).singleElement()
                .extracting(RfqViews.SupplierInvitation::getStatus)
                .isEqualTo(RfqStateMachine.EXPIRED);
        verify(supplierMapper).update(any(), any());
    }

    /** 截止时间已经到达时不得发送，也不得调用 SRM 或写 Outbox。 */
    @Test
    void shouldRejectSendingExpiredDraft() {
        ProcRfq expired = draftRfq(RfqStateMachine.DRAFT);
        expired.setQuotationDeadline(LocalDateTime.now().minusSeconds(1));
        when(rfqMapper.selectOne(any())).thenReturn(expired);

        assertThatThrownBy(() -> service.send(100L, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(srmInternalClient, never()).batch(any(), any());
        verify(rfqMapper, never()).selectForUpdate(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 显式发送必须设置 SENT/sentTime、刷新邀请并在事务调用 Outbox relay。 */
    @Test
    void shouldSendRfqAndPublishOutbox() {
        ProcRfq draft = draftRfq(RfqStateMachine.DRAFT);
        ProcRfq sent = draftRfq(RfqStateMachine.SENT);
        sent.setVersion(1);
        sent.setSentTime(LocalDateTime.now());
        ProcRfqSupplier draftInvitation = invitation(null);
        ProcRfqSupplier sentInvitation = invitation(sent.getSentTime());
        when(rfqMapper.selectOne(any())).thenReturn(draft, sent);
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(draft);
        when(supplierMapper.selectList(any()))
                .thenReturn(List.of(draftInvitation))
                .thenReturn(List.of(draftInvitation))
                .thenReturn(List.of(sentInvitation));
        when(lineMapper.selectList(any()))
                .thenReturn(List.of(rfqLine()))
                .thenReturn(List.of(rfqLine()));
        when(srmInternalClient.batch(any(), any())).thenReturn(R.ok(List.of(approvedSupplier())));
        when(rfqMapper.update(any(), any())).thenReturn(1);
        when(supplierMapper.update(any(), any())).thenReturn(1);

        RfqViews.Detail result = service.send(100L, 0);

        assertThat(result.getStatus()).isEqualTo(RfqStateMachine.SENT);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq("procurement-domain-out-0"),
                eventCaptor.capture(), org.mockito.ArgumentMatchers.eq(41L), any());
        assertThat(eventCaptor.getValue()).isInstanceOf(RfqContracts.DomainEvent.class);
        RfqContracts.DomainEvent event = (RfqContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("procurement.rfq.sent.v1");
        assertThat(event.getTenantId()).isEqualTo(41L);
        assertThat(event.getPayload()).containsEntry("rfqId", 100L)
                .containsEntry("status", RfqStateMachine.SENT)
                .containsEntry("currencyCode", "CNY");
        assertThat(event.getPayload().get("sentTime")).isInstanceOf(LocalDateTime.class);
    }

    /** 比价必须代理 SRM 当前有效报价并校验完整 RFQ 行快照。 */
    @Test
    void shouldReturnValidatedCurrentQuotationComparison() {
        ProcRfq sent = draftRfq(RfqStateMachine.SENT);
        ProcRfqSupplier quoted = invitation(LocalDateTime.now().minusHours(1));
        quoted.setStatus(RfqStateMachine.QUOTED);
        quoted.setQuotationId(701L);
        quoted.setQuotationVersion(3);
        PurchaseOrderContracts.QuotationSnapshot quotation = quotation(701L, 501L, 3);
        when(rfqMapper.selectOne(any())).thenReturn(sent);
        when(lineMapper.selectList(any())).thenReturn(List.of(rfqLine()));
        when(supplierMapper.selectList(any())).thenReturn(List.of(quoted));
        when(srmInternalClient.listValidQuotations(41L, 41L, 100L))
                .thenReturn(R.ok(List.of(quotation)));

        List<PurchaseOrderContracts.QuotationSnapshot> result = service.comparison(100L);

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.getId()).isEqualTo(701L);
            assertThat(value.getVersion()).isEqualTo(3);
            assertThat(value.getTotalAmount()).isEqualByComparingTo("200.0000");
            assertThat(value.getLines()).singleElement()
                    .extracting(PurchaseOrderContracts.QuotationLineSnapshot::getRfqLineId)
                    .isEqualTo(201L);
        });
    }

    /** 定点必须在同一事务重拉报价、生成 PO、固化 RFQ/邀请状态并写入 Outbox。 */
    @Test
    void shouldAwardCurrentQuotationAndCreatePurchaseOrder() {
        ProcRfq sent = draftRfq(RfqStateMachine.SENT);
        ProcRfq awarded = draftRfq(RfqStateMachine.AWARDED);
        awarded.setVersion(1);
        awarded.setAwardedSupplierId(501L);
        awarded.setAwardedQuotationId(701L);
        awarded.setAwardedQuotationVersion(3);
        awarded.setAwardedTime(LocalDateTime.now());
        ProcRfqSupplier winner = invitation(LocalDateTime.now().minusHours(1));
        winner.setStatus(RfqStateMachine.QUOTED);
        winner.setQuotationId(701L);
        winner.setQuotationVersion(2);
        ProcRfqSupplier loser = invitation(LocalDateTime.now().minusHours(1));
        loser.setId(302L);
        loser.setSupplierId(502L);
        loser.setSupplierNameSnapshot("未中标供应商");
        ProcRfqSupplier awardedWinner = invitation(winner.getInvitedTime());
        awardedWinner.setStatus(RfqStateMachine.AWARDED);
        awardedWinner.setQuotationId(701L);
        awardedWinner.setQuotationVersion(3);
        ProcRfqSupplier rejectedLoser = invitation(loser.getInvitedTime());
        rejectedLoser.setId(302L);
        rejectedLoser.setSupplierId(502L);
        rejectedLoser.setSupplierNameSnapshot("未中标供应商");
        rejectedLoser.setStatus(RfqStateMachine.REJECTED);
        PurchaseOrderContracts.QuotationSnapshot quotation = quotation(701L, 501L, 3);
        PurchaseOrderViews.Detail purchaseOrder = purchaseOrder();
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(sent);
        when(lineMapper.selectList(any())).thenReturn(List.of(rfqLine()));
        when(supplierMapper.selectForUpdateByRfq(41L, 100L))
                .thenReturn(List.of(winner, loser));
        when(srmInternalClient.listValidQuotations(41L, 41L, 100L))
                .thenReturn(R.ok(List.of(quotation)));
        when(purchaseOrderService.createFromAward(any(), any(), any(), any()))
                .thenReturn(purchaseOrder);
        when(rfqMapper.update(any(), any())).thenReturn(1);
        when(supplierMapper.update(any(), any())).thenReturn(1);
        when(rfqMapper.selectOne(any())).thenReturn(awarded);
        when(supplierMapper.selectList(any())).thenReturn(List.of(awardedWinner, rejectedLoser));

        RfqViews.AwardResult result = service.award(100L, awardRequest(3));

        assertThat(result.getRfq().getStatus()).isEqualTo(RfqStateMachine.AWARDED);
        assertThat(result.getRfq().getAwardedQuotationId()).isEqualTo(701L);
        assertThat(result.getRfq().getAwardedQuotationVersion()).isEqualTo(3);
        assertThat(result.getPurchaseOrder().getId()).isEqualTo(801L);
        assertThat(result.getRfq().getSuppliers())
                .extracting(RfqViews.SupplierInvitation::getStatus)
                .containsExactly(RfqStateMachine.AWARDED, RfqStateMachine.REJECTED);
        ArgumentCaptor<PurchaseOrderRequests.AwardTerms> termsCaptor =
                ArgumentCaptor.forClass(PurchaseOrderRequests.AwardTerms.class);
        verify(purchaseOrderService).createFromAward(
                org.mockito.ArgumentMatchers.eq(sent),
                org.mockito.ArgumentMatchers.eq(List.of(rfqLine())),
                org.mockito.ArgumentMatchers.eq(quotation), termsCaptor.capture());
        assertThat(termsCaptor.getValue().getDeliveryAddress()).isEqualTo("上海市测试路 1 号");
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq("procurement-domain-out-0"),
                eventCaptor.capture(), org.mockito.ArgumentMatchers.eq(41L), any());
        RfqContracts.DomainEvent event = (RfqContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("procurement.rfq.awarded.v1");
        assertThat(event.getPayload()).containsEntry("quotationId", 701L)
                .containsEntry("quotationVersion", 3)
                .containsEntry("purchaseOrderId", 801L)
                .containsEntry("totalAmount", "200.0000");
    }

    /** 用户提交的报价版本落后时必须在生成 PO 前以 409 拒绝。 */
    @Test
    void shouldRejectStaleQuotationVersionBeforeCreatingPurchaseOrder() {
        ProcRfq sent = draftRfq(RfqStateMachine.SENT);
        ProcRfqSupplier quoted = invitation(LocalDateTime.now().minusHours(1));
        quoted.setStatus(RfqStateMachine.QUOTED);
        quoted.setQuotationId(701L);
        quoted.setQuotationVersion(4);
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(sent);
        when(lineMapper.selectList(any())).thenReturn(List.of(rfqLine()));
        when(supplierMapper.selectForUpdateByRfq(41L, 100L)).thenReturn(List.of(quoted));
        when(srmInternalClient.listValidQuotations(41L, 41L, 100L))
                .thenReturn(R.ok(List.of(quotation(701L, 501L, 4))));

        assertThatThrownBy(() -> service.award(100L, awardRequest(3)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(purchaseOrderService, never()).createFromAward(any(), any(), any(), any());
        verify(rfqMapper, never()).update(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 非受邀供应商报价即使由 SRM 返回也不得用于定点。 */
    @Test
    void shouldRejectQuotationFromUninvitedSupplier() {
        ProcRfq sent = draftRfq(RfqStateMachine.SENT);
        ProcRfqSupplier invited = invitation(LocalDateTime.now().minusHours(1));
        when(rfqMapper.selectForUpdate(41L, 100L)).thenReturn(sent);
        when(lineMapper.selectList(any())).thenReturn(List.of(rfqLine()));
        when(supplierMapper.selectForUpdateByRfq(41L, 100L)).thenReturn(List.of(invited));
        when(srmInternalClient.listValidQuotations(41L, 41L, 100L))
                .thenReturn(R.ok(List.of(quotation(702L, 999L, 1))));
        RfqRequests.AwardRequest request = awardRequest(1);
        request.setQuotationId(702L);

        assertThatThrownBy(() -> service.award(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(purchaseOrderService, never()).createFromAward(any(), any(), any(), any());
    }

    private RfqRequests.CreateRequest createRequest() {
        RfqRequests.CreateRequest request = new RfqRequests.CreateRequest();
        request.setRequisitionId(10L);
        request.setTitle("笔记本询价");
        request.setQuotationDeadline(LocalDateTime.now().plusDays(5));
        request.setSupplierIds(List.of(501L));
        return request;
    }

    private RfqRequests.AwardRequest awardRequest(int quotationVersion) {
        RfqRequests.AwardRequest request = new RfqRequests.AwardRequest();
        request.setRfqVersion(0);
        request.setQuotationId(701L);
        request.setQuotationVersion(quotationVersion);
        request.setTitle("笔记本采购订单");
        request.setExpectedDeliveryDate(LocalDate.now().plusDays(10));
        request.setDeliveryAddress("上海市测试路 1 号");
        request.setContactName("采购收货人");
        request.setContactPhone("13800000000");
        return request;
    }

    private PurchaseOrderContracts.QuotationSnapshot quotation(
            Long quotationId, Long supplierId, int version) {
        PurchaseOrderContracts.QuotationLineSnapshot line =
                new PurchaseOrderContracts.QuotationLineSnapshot();
        line.setId(711L);
        line.setRfqLineId(201L);
        line.setMaterialCode("NB-001");
        line.setMaterialName("笔记本电脑");
        line.setUnit("EA");
        line.setUnitPrice(new BigDecimal("100.000000"));
        line.setQuantity(new BigDecimal("2.000000"));
        line.setLineAmount(new BigDecimal("200.0000"));
        line.setDeliveryDays(5);
        PurchaseOrderContracts.QuotationSnapshot quotation =
                new PurchaseOrderContracts.QuotationSnapshot();
        quotation.setId(quotationId);
        quotation.setRfqId(100L);
        quotation.setRfqNo("RFQ-41-100");
        quotation.setSupplierId(supplierId);
        quotation.setSupplierNameSnapshot("报价供应商");
        quotation.setQuotationTime(LocalDateTime.now().minusMinutes(10));
        quotation.setValidUntil(LocalDateTime.now().plusDays(5));
        quotation.setTotalAmount(new BigDecimal("200.0000"));
        quotation.setCurrencyCode("CNY");
        quotation.setStatus("SUBMITTED");
        quotation.setVersion(version);
        quotation.setLines(List.of(line));
        return quotation;
    }

    private PurchaseOrderViews.Detail purchaseOrder() {
        PurchaseOrderViews.Detail detail = new PurchaseOrderViews.Detail();
        detail.setId(801L);
        detail.setPoNo("PO-41-801");
        detail.setRfqId(100L);
        detail.setQuotationId(701L);
        detail.setQuotationVersion(3);
        detail.setSupplierId(501L);
        detail.setSupplierNameSnapshot("报价供应商");
        detail.setTitle("笔记本采购订单");
        detail.setTotalAmount(new BigDecimal("200.0000"));
        detail.setCurrencyCode("CNY");
        detail.setStatus("DRAFT");
        return detail;
    }

    private ProcRequisition approvedRequisition() {
        ProcRequisition requisition = new ProcRequisition();
        requisition.setId(10L);
        requisition.setTenantId(41L);
        requisition.setRequisitionNo("PR-41-10");
        requisition.setStatus(RequisitionStateMachine.APPROVED);
        requisition.setCurrencyCode("CNY");
        requisition.setDeleted(0);
        return requisition;
    }

    private ProcRequisitionLine requisitionLine() {
        ProcRequisitionLine line = new ProcRequisitionLine();
        line.setId(11L);
        line.setTenantId(41L);
        line.setRequisitionId(10L);
        line.setLineNo(1);
        line.setMaterialId(301L);
        line.setMaterialCode("NB-001");
        line.setMaterialName("笔记本电脑");
        line.setCategoryCode("IT");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setRemark("含三年保修");
        line.setDeleted(0);
        return line;
    }

    private SrmSupplierContracts.Summary approvedSupplier() {
        SrmSupplierContracts.Summary summary = new SrmSupplierContracts.Summary();
        summary.setId(501L);
        summary.setSupplierNo("SUP-501");
        summary.setName("合格供应商");
        summary.setStatus("APPROVED");
        return summary;
    }

    private ProcRfq draftRfq(String status) {
        ProcRfq rfq = new ProcRfq();
        rfq.setId(100L);
        rfq.setTenantId(41L);
        rfq.setRfqNo("RFQ-41-100");
        rfq.setRequisitionId(10L);
        rfq.setTitle("笔记本询价");
        rfq.setQuotationDeadline(LocalDateTime.now().plusDays(5));
        rfq.setCurrencyCode("CNY");
        rfq.setStatus(status);
        rfq.setOwnerUserId(7L);
        rfq.setOwnerUnitId(12L);
        rfq.setVersion(0);
        rfq.setDeleted(0);
        return rfq;
    }

    private ProcRfqLine rfqLine() {
        ProcRfqLine line = new ProcRfqLine();
        line.setId(201L);
        line.setTenantId(41L);
        line.setRfqId(100L);
        line.setLineNo(1);
        line.setMaterialCode("NB-001");
        line.setMaterialName("笔记本电脑");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private ProcRfqSupplier invitation(LocalDateTime invitedTime) {
        ProcRfqSupplier invitation = new ProcRfqSupplier();
        invitation.setId(301L);
        invitation.setTenantId(41L);
        invitation.setRfqId(100L);
        invitation.setSupplierId(501L);
        invitation.setSupplierNameSnapshot("合格供应商");
        invitation.setStatus(RfqStateMachine.INVITED);
        invitation.setInvitedTime(invitedTime);
        invitation.setVersion(invitedTime == null ? 0 : 1);
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
