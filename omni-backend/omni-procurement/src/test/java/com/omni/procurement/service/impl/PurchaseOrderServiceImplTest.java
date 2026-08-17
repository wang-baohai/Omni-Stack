package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.PurchaseOrderStateMachine;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.GoodsReceiptContracts;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.PurchaseOrderRequests;
import com.omni.procurement.dto.PurchaseOrderViews;
import com.omni.procurement.entity.ProcGoodsReceiptLine;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.entity.ProcPurchaseOrderLine;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;
import com.omni.procurement.mapper.ProcGoodsReceiptLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderMapper;
import com.omni.procurement.security.ProcDataScopeContext;
import com.omni.procurement.security.ProcTenantContext;
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

/** 采购订单中标快照、状态命令和 Outbox 测试。 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock private ProcPurchaseOrderMapper orderMapper;
    @Mock private ProcPurchaseOrderLineMapper lineMapper;
    @Mock private ProcGoodsReceiptLineMapper receiptLineMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private PurchaseOrderServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcPurchaseOrder.class, "ProcPurchaseOrderMapper");
        initialize(ProcPurchaseOrderLine.class, "ProcPurchaseOrderLineMapper");
        initialize(ProcGoodsReceiptLine.class, "ProcGoodsReceiptLineMapper");
        initialize(ProcRfq.class, "ProcRfqMapper");
        initialize(ProcRfqLine.class, "ProcRfqLineMapper");
    }

    /** 初始化服务和采购员上下文。 */
    @BeforeEach
    void setUp() {
        service = new PurchaseOrderServiceImpl(orderMapper, lineMapper,
                receiptLineMapper, reliableMessageRelay, new ProcRecordAccessGuard());
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(7L, 41L, "buyer"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                7L, 41L, "procurement:rfq:award", 12L, "SELF", Set.of(12L)));
    }

    /** 清理线程上下文。 */
    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 定点必须复制报价版本、供应商、价格和交期不可变快照并发送 created 事件。 */
    @Test
    void shouldCreateImmutableOrderFromAwardSnapshot() {
        doAnswer(invocation -> {
            ProcPurchaseOrder order = invocation.getArgument(0);
            order.setId(801L);
            return 1;
        }).when(orderMapper).insert(any(ProcPurchaseOrder.class));
        doAnswer(invocation -> {
            ProcPurchaseOrderLine line = invocation.getArgument(0);
            line.setId(811L);
            return 1;
        }).when(lineMapper).insert(any(ProcPurchaseOrderLine.class));
        when(orderMapper.update(any(), any())).thenReturn(1);

        PurchaseOrderViews.Detail result = service.createFromAward(
                rfq(), List.of(rfqLine()), quotation(), terms());

        assertThat(result.getPoNo()).isEqualTo("PO-41-801");
        assertThat(result.getSupplierId()).isEqualTo(501L);
        assertThat(result.getSupplierNameSnapshot()).isEqualTo("合格供应商");
        assertThat(result.getQuotationId()).isEqualTo(701L);
        assertThat(result.getQuotationVersion()).isEqualTo(3);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("12800.0000");
        assertThat(result.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getRfqLineId()).isEqualTo(101L);
            assertThat(line.getUnitPrice()).isEqualByComparingTo("6400.000000");
            assertThat(line.getQuantity()).isEqualByComparingTo("2.000000");
            assertThat(line.getDeliveryDays()).isEqualTo(7);
        });
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq("procurement-domain-out-0"),
                eventCaptor.capture(), org.mockito.ArgumentMatchers.eq(41L), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("procurement.purchase-order.created.v1");
        assertThat(event.getPayload()).containsEntry("quotationId", 701L)
                .containsEntry("quotationVersion", 3)
                .containsEntry("totalAmount", "12800.0000");
    }

    /** 报价行金额或总额被篡改时不能生成订单。 */
    @Test
    void shouldRejectTamperedQuotationAmount() {
        PurchaseOrderContracts.QuotationSnapshot quotation = quotation();
        quotation.setTotalAmount(new BigDecimal("1.0000"));

        assertThatThrownBy(() -> service.createFromAward(
                rfq(), List.of(rfqLine()), quotation, terms()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(orderMapper, never()).insert(any(ProcPurchaseOrder.class));
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 相同定点意图重放必须返回原订单，且不能重复插入或发送事件。 */
    @Test
    void shouldReplaySameAwardWithoutDuplicatingOrderOrEvent() {
        ProcPurchaseOrder existing = order(PurchaseOrderStateMachine.DRAFT, 0);
        ProcPurchaseOrderLine existingLine = orderLine();
        LocalDateTime originalAwardTime = LocalDateTime.now().minusDays(1);
        LocalDate originalExpectedDate = originalAwardTime.toLocalDate().plusDays(7);
        existing.setCreateTime(originalAwardTime);
        existing.setExpectedDeliveryDate(originalExpectedDate);
        existingLine.setExpectedDeliveryDate(originalExpectedDate);
        when(orderMapper.selectForUpdateByRfq(41L, 91L)).thenReturn(existing);
        when(orderMapper.selectOne(any())).thenReturn(existing);
        when(lineMapper.selectList(any())).thenReturn(List.of(existingLine));
        when(receiptLineMapper.selectConfirmedTotals(41L, 801L)).thenReturn(List.of());

        PurchaseOrderViews.Detail result = service.createFromAward(
                rfq(), List.of(rfqLine()), quotation(), terms());

        assertThat(result.getId()).isEqualTo(801L);
        assertThat(result.getQuotationId()).isEqualTo(701L);
        assertThat(result.getExpectedDeliveryDate()).isEqualTo(originalExpectedDate);
        assertThat(result.getLines()).singleElement()
                .extracting(PurchaseOrderViews.Line::getUnitPrice)
                .isEqualTo(new BigDecimal("6400.000000"));
        verify(orderMapper, never()).insert(any(ProcPurchaseOrder.class));
        verify(lineMapper, never()).insert(any(ProcPurchaseOrderLine.class));
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 相同 RFQ 已绑定不同不可变行快照时必须拒绝幂等伪装。 */
    @Test
    void shouldRejectAwardReplayWhenStoredLineSnapshotDiffers() {
        ProcPurchaseOrder existing = order(PurchaseOrderStateMachine.DRAFT, 0);
        ProcPurchaseOrderLine changedLine = orderLine();
        changedLine.setUnitPrice(new BigDecimal("6300.000000"));
        when(orderMapper.selectForUpdateByRfq(41L, 91L)).thenReturn(existing);
        when(lineMapper.selectList(any())).thenReturn(List.of(changedLine));

        assertThatThrownBy(() -> service.createFromAward(
                rfq(), List.of(rfqLine()), quotation(), terms()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(orderMapper, never()).insert(any(ProcPurchaseOrder.class));
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 已发送订单确认后必须写入 confirmed Outbox 且状态推进。 */
    @Test
    void shouldConfirmSentOrderAndPublishOutbox() {
        ProcPurchaseOrder sent = order(PurchaseOrderStateMachine.SENT, 0);
        ProcPurchaseOrder confirmed = order(PurchaseOrderStateMachine.CONFIRMED, 1);
        when(orderMapper.selectForUpdate(41L, 801L)).thenReturn(sent);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(confirmed);
        when(lineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(receiptLineMapper.selectConfirmedTotals(41L, 801L)).thenReturn(List.of());

        PurchaseOrderViews.Detail result = service.confirm(801L, 0);

        assertThat(result.getStatus()).isEqualTo(PurchaseOrderStateMachine.CONFIRMED);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(any(), eventCaptor.capture(), any(), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType())
                .isEqualTo("procurement.purchase-order.confirmed.v1");
        assertThat(event.getPayload()).containsEntry("purchaseOrderId", 801L)
                .containsEntry("status", PurchaseOrderStateMachine.CONFIRMED);
    }

    private ProcRfq rfq() {
        ProcRfq rfq = new ProcRfq();
        rfq.setId(91L);
        rfq.setTenantId(41L);
        rfq.setRfqNo("RFQ-41-91");
        rfq.setCurrencyCode("CNY");
        rfq.setStatus(RfqStateMachine.SENT);
        rfq.setOwnerUserId(7L);
        rfq.setOwnerUnitId(12L);
        return rfq;
    }

    private ProcRfqLine rfqLine() {
        ProcRfqLine line = new ProcRfqLine();
        line.setId(101L);
        line.setTenantId(41L);
        line.setRfqId(91L);
        line.setLineNo(1);
        line.setMaterialId(301L);
        line.setMaterialCode("NB-001");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        return line;
    }

    private PurchaseOrderContracts.QuotationSnapshot quotation() {
        PurchaseOrderContracts.QuotationLineSnapshot line =
                new PurchaseOrderContracts.QuotationLineSnapshot();
        line.setId(711L);
        line.setRfqLineId(101L);
        line.setMaterialCode("NB-001");
        line.setMaterialName("商务笔记本");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal("6400.000000"));
        line.setLineAmount(new BigDecimal("12800.0000"));
        line.setDeliveryDays(7);
        PurchaseOrderContracts.QuotationSnapshot quotation =
                new PurchaseOrderContracts.QuotationSnapshot();
        quotation.setId(701L);
        quotation.setRfqId(91L);
        quotation.setRfqNo("RFQ-41-91");
        quotation.setSupplierId(501L);
        quotation.setSupplierNameSnapshot("合格供应商");
        quotation.setQuotationTime(LocalDateTime.now().minusHours(1));
        quotation.setValidUntil(LocalDateTime.now().plusDays(3));
        quotation.setTotalAmount(new BigDecimal("12800.0000"));
        quotation.setCurrencyCode("CNY");
        quotation.setStatus("SUBMITTED");
        quotation.setVersion(3);
        quotation.setLines(List.of(line));
        return quotation;
    }

    private PurchaseOrderRequests.AwardTerms terms() {
        PurchaseOrderRequests.AwardTerms terms = new PurchaseOrderRequests.AwardTerms();
        terms.setTitle("商务笔记本采购订单");
        terms.setDeliveryAddress("上海市浦东新区示例路 1 号");
        terms.setContactName("张三");
        terms.setContactPhone("13800138000");
        return terms;
    }

    private ProcPurchaseOrder order(String status, int version) {
        ProcPurchaseOrder order = new ProcPurchaseOrder();
        order.setId(801L);
        order.setTenantId(41L);
        order.setPoNo("PO-41-801");
        order.setRfqId(91L);
        order.setSupplierId(501L);
        order.setSupplierNameSnapshot("合格供应商");
        order.setQuotationId(701L);
        order.setQuotationVersion(3);
        order.setTitle("商务笔记本采购订单");
        order.setTotalAmount(new BigDecimal("12800.0000"));
        order.setCurrencyCode("CNY");
        order.setStatus(status);
        order.setExpectedDeliveryDate(LocalDate.now().plusDays(7));
        order.setDeliveryAddress("上海市浦东新区示例路 1 号");
        order.setContactName("张三");
        order.setContactPhone("13800138000");
        order.setOwnerUserId(7L);
        order.setOwnerUnitId(12L);
        order.setVersion(version);
        order.setDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    private ProcPurchaseOrderLine orderLine() {
        ProcPurchaseOrderLine line = new ProcPurchaseOrderLine();
        line.setId(811L);
        line.setTenantId(41L);
        line.setPoId(801L);
        line.setLineNo(1);
        line.setRfqLineId(101L);
        line.setMaterialId(301L);
        line.setMaterialCode("NB-001");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal("6400.000000"));
        line.setTotalPrice(new BigDecimal("12800.0000"));
        line.setDeliveryDays(7);
        line.setExpectedDeliveryDate(LocalDate.now().plusDays(7));
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.mapper." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
