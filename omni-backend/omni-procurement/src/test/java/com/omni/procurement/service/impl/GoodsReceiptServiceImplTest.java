package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.GoodsReceiptStateMachine;
import com.omni.procurement.domain.PurchaseOrderStateMachine;
import com.omni.procurement.dto.GoodsReceiptContracts;
import com.omni.procurement.dto.GoodsReceiptRequests;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.entity.ProcGoodsReceipt;
import com.omni.procurement.entity.ProcGoodsReceiptLine;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.entity.ProcPurchaseOrderLine;
import com.omni.procurement.mapper.ProcGoodsReceiptLineMapper;
import com.omni.procurement.mapper.ProcGoodsReceiptMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 收货草稿、累计防超收、资产事件和后续质检测试。 */
@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceImplTest {

    @Mock private ProcGoodsReceiptMapper receiptMapper;
    @Mock private ProcGoodsReceiptLineMapper lineMapper;
    @Mock private ProcPurchaseOrderMapper orderMapper;
    @Mock private ProcPurchaseOrderLineMapper orderLineMapper;
    @Mock private ProcMaterialMapper materialMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private GoodsReceiptServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcGoodsReceipt.class, "ProcGoodsReceiptMapper");
        initialize(ProcGoodsReceiptLine.class, "ProcGoodsReceiptLineMapper");
        initialize(ProcPurchaseOrder.class, "ProcPurchaseOrderMapper");
        initialize(ProcPurchaseOrderLine.class, "ProcPurchaseOrderLineMapper");
        initialize(ProcMaterial.class, "ProcMaterialMapper");
    }

    /** 初始化服务和收货人上下文。 */
    @BeforeEach
    void setUp() {
        service = new GoodsReceiptServiceImpl(receiptMapper, lineMapper, orderMapper,
                orderLineMapper, materialMapper, reliableMessageRelay,
                new ProcRecordAccessGuard());
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(7L, 41L, "receiver"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                7L, 41L, "procurement:goods-receipt:create", 12L, "SELF", Set.of(12L)));
    }

    /** 清理线程上下文。 */
    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 创建草稿只复制快照，不占用订单数量且不发送事件。 */
    @Test
    void shouldCreateDraftWithoutUpdatingOrderOrPublishingEvent() {
        when(orderMapper.selectOne(any())).thenReturn(order(PurchaseOrderStateMachine.CONFIRMED, 0));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(lineMapper.selectConfirmedTotals(41L, 801L)).thenReturn(List.of());
        when(materialMapper.selectList(any())).thenReturn(List.of(assetMaterial()));
        doAnswer(invocation -> {
            ProcGoodsReceipt receipt = invocation.getArgument(0);
            receipt.setId(901L);
            return 1;
        }).when(receiptMapper).insert(any(ProcGoodsReceipt.class));
        doAnswer(invocation -> {
            ProcGoodsReceiptLine line = invocation.getArgument(0);
            line.setId(911L);
            return 1;
        }).when(lineMapper).insert(any(ProcGoodsReceiptLine.class));
        when(receiptMapper.update(any(), any())).thenReturn(1);

        GoodsReceiptViews.Detail result = service.create(createRequest("PASS", "1.000000"));

        assertThat(result.getGrNo()).isEqualTo("GR-41-901");
        assertThat(result.getStatus()).isEqualTo(GoodsReceiptStateMachine.DRAFT);
        assertThat(result.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getPoLineId()).isEqualTo(811L);
            assertThat(line.getAssetManaged()).isTrue();
            assertThat(line.getReceivedQuantity()).isEqualByComparingTo("1.000000");
        });
        verify(orderMapper, never()).update(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 确认完整收货必须锁订单、推进 RECEIVED 并仅发送可资产化行。 */
    @Test
    void shouldConfirmFullReceiptAndPublishAssetCandidates() {
        ProcGoodsReceipt draft = receipt(GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceipt confirmed = receipt(GoodsReceiptStateMachine.CONFIRMED, 1);
        confirmed.setConfirmedTime(LocalDateTime.now());
        ProcPurchaseOrder confirmedOrder = order(PurchaseOrderStateMachine.CONFIRMED, 0);
        ProcPurchaseOrder receivedOrder = order(PurchaseOrderStateMachine.RECEIVED, 1);
        ProcGoodsReceiptLine receiptLine = receiptLine("PASS", "2.000000", true);
        when(receiptMapper.selectForUpdate(41L, 901L)).thenReturn(draft);
        when(orderMapper.selectForUpdate(41L, 801L)).thenReturn(confirmedOrder);
        when(lineMapper.selectForUpdateByReceipt(41L, 901L)).thenReturn(List.of(receiptLine));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(lineMapper.selectConfirmedTotals(41L, 801L)).thenReturn(List.of());
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(lineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.selectMaxConfirmedReceiveTime(41L, 801L))
                .thenReturn(LocalDateTime.now());
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.selectOne(any())).thenReturn(confirmed);
        when(orderMapper.selectOne(any())).thenReturn(receivedOrder);
        when(lineMapper.selectList(any())).thenReturn(List.of(receiptLine));

        GoodsReceiptViews.Detail result = service.confirm(901L, 0);

        assertThat(result.getStatus()).isEqualTo(GoodsReceiptStateMachine.CONFIRMED);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(any(), eventCaptor.capture(), any(), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(
                "procurement.goods-receipt.confirmed.v1");
        assertThat((List<?>) event.getPayload().get("lines")).singleElement();
        assertThat(event.getPayload()).containsEntry("purchaseOrderId", 801L);
        assertThat(event.getPayload()).containsEntry("ownerUserId", 7L);
        assertThat(event.getPayload()).containsEntry("ownerUnitId", 12L);
        verify(orderMapper).selectForUpdate(41L, 801L);
        verify(receiptMapper).selectMaxConfirmedReceiveTime(41L, 801L);
    }

    /** 多张草稿分别合法但累计超收时，确认阶段必须基于全部已确认数量拒绝。 */
    @Test
    void shouldRejectConfirmationWhenCumulativeQuantityExceedsOrder() {
        when(receiptMapper.selectForUpdate(41L, 901L))
                .thenReturn(receipt(GoodsReceiptStateMachine.DRAFT, 0));
        when(orderMapper.selectForUpdate(41L, 801L))
                .thenReturn(order(PurchaseOrderStateMachine.PARTIAL_RECEIVED, 2));
        when(lineMapper.selectForUpdateByReceipt(41L, 901L))
                .thenReturn(List.of(receiptLine("PASS", "1.000000", true)));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(lineMapper.selectConfirmedTotals(41L, 801L))
                .thenReturn(List.of(receivedTotal("1.500000")));

        assertThatThrownBy(() -> service.confirm(901L, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(receiptMapper, never()).update(any(), any());
        verify(orderMapper, never()).update(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 连续计量的非整数收货即使质检通过，也不能进入资产事件候选行。 */
    @Test
    void shouldExcludeFractionalQuantityFromAssetEvent() {
        ProcGoodsReceipt draft = receipt(GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceipt confirmed = receipt(GoodsReceiptStateMachine.CONFIRMED, 1);
        ProcGoodsReceiptLine fractional = receiptLine("PASS", "0.500000", true);
        when(receiptMapper.selectForUpdate(41L, 901L)).thenReturn(draft);
        when(orderMapper.selectForUpdate(41L, 801L))
                .thenReturn(order(PurchaseOrderStateMachine.CONFIRMED, 0));
        when(lineMapper.selectForUpdateByReceipt(41L, 901L))
                .thenReturn(List.of(fractional));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(lineMapper.selectConfirmedTotals(41L, 801L)).thenReturn(List.of());
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.selectOne(any())).thenReturn(confirmed);
        when(orderMapper.selectOne(any())).thenReturn(
                order(PurchaseOrderStateMachine.PARTIAL_RECEIVED, 1));
        when(lineMapper.selectList(any())).thenReturn(List.of(fractional));

        service.confirm(901L, 0);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(any(), eventCaptor.capture(), any(), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat((List<?>) event.getPayload().get("lines")).isEmpty();
        verify(lineMapper, never()).update(any(), any());
    }

    /** PENDING 后续转 PASS 时只发布本次新通过且可资产化的行。 */
    @Test
    void shouldPublishOnlyNewlyPassedAssetLine() {
        ProcGoodsReceipt confirmed = receipt(GoodsReceiptStateMachine.CONFIRMED, 1);
        ProcGoodsReceipt updated = receipt(GoodsReceiptStateMachine.CONFIRMED, 2);
        ProcGoodsReceiptLine pending = receiptLine("PENDING", "1.000000", true);
        when(receiptMapper.selectForUpdate(41L, 901L)).thenReturn(confirmed);
        when(lineMapper.selectForUpdateByReceipt(41L, 901L)).thenReturn(List.of(pending));
        when(lineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(
                order(PurchaseOrderStateMachine.RECEIVED, 1));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(receiptMapper.selectOne(any())).thenReturn(updated);
        when(lineMapper.selectList(any())).thenReturn(List.of(pending));

        GoodsReceiptViews.Detail result = service.updateQualityResult(
                901L, qualityCommand(1, "PASS"));

        assertThat(result.getVersion()).isEqualTo(2);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(any(), eventCaptor.capture(), any(), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(
                "procurement.goods-receipt.quality-passed.v1");
        assertThat((List<?>) event.getPayload().get("lines")).singleElement();
    }

    /** 同一批多个新通过行必须共用一个事件，且排除非资产行。 */
    @Test
    void shouldPublishMultipleNewlyPassedLinesInOneEvent() {
        ProcGoodsReceipt confirmed = receipt(GoodsReceiptStateMachine.CONFIRMED, 1);
        ProcGoodsReceipt updated = receipt(GoodsReceiptStateMachine.CONFIRMED, 2);
        ProcGoodsReceiptLine first = receiptLine(
                new ReceiptLineIdentity(911L, 811L, 301L),
                "PENDING", "1.000000", true);
        ProcGoodsReceiptLine second = receiptLine(
                new ReceiptLineIdentity(912L, 812L, 302L),
                "PENDING", "2.000000", true);
        ProcGoodsReceiptLine nonAsset = receiptLine(
                new ReceiptLineIdentity(913L, 813L, 303L),
                "PENDING", "1.000000", false);
        when(receiptMapper.selectForUpdate(41L, 901L)).thenReturn(confirmed);
        when(lineMapper.selectForUpdateByReceipt(41L, 901L))
                .thenReturn(List.of(first, second, nonAsset));
        when(lineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(
                order(PurchaseOrderStateMachine.RECEIVED, 1));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(
                orderLine(811L, 301L, "6400.000000"),
                orderLine(812L, 302L, "120.500000")));
        when(receiptMapper.selectOne(any())).thenReturn(updated);
        when(lineMapper.selectList(any())).thenReturn(List.of(first, second, nonAsset));

        GoodsReceiptRequests.QualityResultCommand command = qualityCommand(
                1, List.of(911L, 912L, 913L), "PASS");
        service.updateQualityResult(901L, command);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay, times(1))
                .send(any(), eventCaptor.capture(), any(), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(
                "procurement.goods-receipt.quality-passed.v1");
        List<?> payloadLines = (List<?>) event.getPayload().get("lines");
        List<Long> payloadLineIds = payloadLines.stream()
                .map(item -> (Long) ((java.util.Map<?, ?>) item)
                        .get("goodsReceiptLineId"))
                .toList();
        assertThat(payloadLineIds).containsExactly(911L, 912L);
        assertThat(first.getQualityPassedEventId()).isNotBlank()
                .isEqualTo(second.getQualityPassedEventId());
        assertThat(nonAsset.getQualityPassedEventId()).isNull();
    }

    private GoodsReceiptRequests.CreateRequest createRequest(
            String qualityStatus, String quantity) {
        GoodsReceiptRequests.LineInput line = new GoodsReceiptRequests.LineInput();
        line.setPoLineId(811L);
        line.setReceivedQuantity(new BigDecimal(quantity));
        line.setQualityStatus(qualityStatus);
        GoodsReceiptRequests.CreateRequest request = new GoodsReceiptRequests.CreateRequest();
        request.setPoId(801L);
        request.setReceiveTime(LocalDateTime.now());
        request.setLines(List.of(line));
        return request;
    }

    private GoodsReceiptRequests.QualityResultCommand qualityCommand(
            int version, String status) {
        GoodsReceiptRequests.QualityResultLine line =
                new GoodsReceiptRequests.QualityResultLine();
        line.setGoodsReceiptLineId(911L);
        line.setQualityStatus(status);
        GoodsReceiptRequests.QualityResultCommand command =
                new GoodsReceiptRequests.QualityResultCommand();
        command.setVersion(version);
        command.setLines(List.of(line));
        return command;
    }

    private GoodsReceiptRequests.QualityResultCommand qualityCommand(
            int version, List<Long> lineIds, String status) {
        List<GoodsReceiptRequests.QualityResultLine> lines = lineIds.stream().map(lineId -> {
            GoodsReceiptRequests.QualityResultLine line =
                    new GoodsReceiptRequests.QualityResultLine();
            line.setGoodsReceiptLineId(lineId);
            line.setQualityStatus(status);
            return line;
        }).toList();
        GoodsReceiptRequests.QualityResultCommand command =
                new GoodsReceiptRequests.QualityResultCommand();
        command.setVersion(version);
        command.setLines(lines);
        return command;
    }

    private ProcPurchaseOrder order(String status, int version) {
        ProcPurchaseOrder order = new ProcPurchaseOrder();
        order.setId(801L);
        order.setTenantId(41L);
        order.setPoNo("PO-41-801");
        order.setSupplierId(501L);
        order.setSupplierNameSnapshot("合格供应商");
        order.setCurrencyCode("CNY");
        order.setStatus(status);
        order.setOwnerUserId(7L);
        order.setOwnerUnitId(12L);
        order.setVersion(version);
        order.setDeleted(0);
        return order;
    }

    private ProcPurchaseOrderLine orderLine() {
        return orderLine(811L, 301L, "6400.000000");
    }

    private ProcPurchaseOrderLine orderLine(
            long id, long materialId, String unitPrice) {
        ProcPurchaseOrderLine line = new ProcPurchaseOrderLine();
        line.setId(id);
        line.setTenantId(41L);
        line.setPoId(801L);
        line.setLineNo(1);
        line.setMaterialId(materialId);
        line.setMaterialCode("NB-001");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setTotalPrice(new BigDecimal(unitPrice)
                .multiply(new BigDecimal("2.000000")));
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private ProcGoodsReceipt receipt(String status, int version) {
        ProcGoodsReceipt receipt = new ProcGoodsReceipt();
        receipt.setId(901L);
        receipt.setTenantId(41L);
        receipt.setGrNo("GR-41-901");
        receipt.setPoId(801L);
        receipt.setReceiverUserId(7L);
        receipt.setReceiveTime(LocalDateTime.now());
        receipt.setStatus(status);
        receipt.setOwnerUserId(7L);
        receipt.setOwnerUnitId(12L);
        receipt.setVersion(version);
        receipt.setDeleted(0);
        return receipt;
    }

    private ProcGoodsReceiptLine receiptLine(
            String qualityStatus, String quantity, boolean assetManaged) {
        return receiptLine(
                new ReceiptLineIdentity(911L, 811L, 301L),
                qualityStatus, quantity, assetManaged);
    }

    private ProcGoodsReceiptLine receiptLine(
            ReceiptLineIdentity identity,
            String qualityStatus, String quantity, boolean assetManaged) {
        ProcGoodsReceiptLine line = new ProcGoodsReceiptLine();
        line.setId(identity.id());
        line.setTenantId(41L);
        line.setGoodsReceiptId(901L);
        line.setLineNo(1);
        line.setPoLineId(identity.poLineId());
        line.setMaterialId(identity.materialId());
        line.setMaterialCode("NB-001");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT");
        line.setUnit("EA");
        line.setAssetManaged(assetManaged);
        line.setOrderedQuantity(new BigDecimal("2.000000"));
        line.setReceivedQuantity(new BigDecimal(quantity));
        line.setQualityStatus(qualityStatus);
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private ProcMaterial assetMaterial() {
        ProcMaterial material = new ProcMaterial();
        material.setId(301L);
        material.setTenantId(41L);
        material.setAssetManaged(true);
        material.setDeleted(0);
        return material;
    }

    private GoodsReceiptContracts.ReceivedTotal receivedTotal(String quantity) {
        GoodsReceiptContracts.ReceivedTotal total =
                new GoodsReceiptContracts.ReceivedTotal();
        total.setPoLineId(811L);
        total.setTotalQuantity(new BigDecimal(quantity));
        return total;
    }

    /**
     * 收货测试行标识。
     */
    private record ReceiptLineIdentity(long id, long poLineId, long materialId) {
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.mapper." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
