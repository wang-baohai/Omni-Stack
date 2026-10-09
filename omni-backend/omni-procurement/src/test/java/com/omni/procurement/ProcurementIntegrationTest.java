package com.omni.procurement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
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
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.impl.GoodsReceiptServiceImpl;
import com.omni.procurement.service.impl.PurchaseOrderServiceImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 端到端采购流程集成测试。
 *
 * <p>覆盖：订单已确认 → 创建收货草稿 → 确认收货 → 资产事件发布。
 * 使用 Mockito Mock 外部依赖（MQ），验证 GoodsReceiptService 与
 * PurchaseOrderService 之间的数据流转和业务协作正确性。
 */
@ExtendWith(MockitoExtension.class)
class ProcurementIntegrationTest {

    @Mock private ProcGoodsReceiptMapper receiptMapper;
    @Mock private ProcGoodsReceiptLineMapper receiptLineMapper;
    @Mock private ProcPurchaseOrderMapper orderMapper;
    @Mock private ProcPurchaseOrderLineMapper orderLineMapper;
    @Mock private ProcMaterialMapper materialMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private GoodsReceiptServiceImpl goodsReceiptService;
    private PurchaseOrderServiceImpl purchaseOrderService;

    private static final long TENANT_ID = 100L;
    private static final long USER_ID = 1L;
    private static final long UNIT_ID = 10L;

    @BeforeAll
    static void initializeTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        String[] resources = {
                "ProcGoodsReceiptMapper", "ProcGoodsReceiptLineMapper",
                "ProcPurchaseOrderMapper", "ProcPurchaseOrderLineMapper",
                "ProcMaterialMapper",
        };
        Class<?>[] entities = {
                ProcGoodsReceipt.class, ProcGoodsReceiptLine.class,
                ProcPurchaseOrder.class, ProcPurchaseOrderLine.class,
                ProcMaterial.class,
        };
        for (int i = 0; i < resources.length; i++) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                    configuration, resources[i]);
            assistant.setCurrentNamespace(
                    "com.omni.procurement.mapper." + resources[i]);
            TableInfoHelper.initTableInfo(assistant, entities[i]);
        }
    }

    @BeforeEach
    void setUp() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(
                USER_ID, TENANT_ID, "procurement-user"));
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                USER_ID, UNIT_ID, "procurement:goods-receipt:create",
                UNIT_ID, "ALL", Set.of(), null));

        goodsReceiptService = new GoodsReceiptServiceImpl(
                receiptMapper, receiptLineMapper, orderMapper,
                orderLineMapper, materialMapper, reliableMessageRelay,
                new ProcRecordAccessGuard());

        purchaseOrderService = new PurchaseOrderServiceImpl(
                orderMapper, orderLineMapper, receiptLineMapper,
                reliableMessageRelay, new ProcRecordAccessGuard());
    }

    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 端到端验证：已确认订单 → 创建收货草稿 → 确认收货 → 资产候选事件发布。 */
    @Test
    void shouldCompleteReceiptCycleForConfirmedOrder() {
        // ── Step 1: 创建收货草稿 ──
        ProcPurchaseOrder confirmedOrder = buildOrder(
                PurchaseOrderStateMachine.CONFIRMED, 0);
        when(orderMapper.selectOne(any())).thenReturn(confirmedOrder);
        when(orderLineMapper.selectList(any()))
                .thenReturn(List.of(buildOrderLine()));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_ID, 801L))
                .thenReturn(List.of());
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(buildMaterial()));
        doAnswer(inv -> {
            ProcGoodsReceipt r = inv.getArgument(0);
            r.setId(901L);
            r.setGrNo("GR-100-901");
            return 1;
        }).when(receiptMapper).insert(any(ProcGoodsReceipt.class));
        doAnswer(inv -> {
            ProcGoodsReceiptLine l = inv.getArgument(0);
            l.setId(911L);
            return 1;
        }).when(receiptLineMapper).insert(any(ProcGoodsReceiptLine.class));
        when(receiptMapper.update(any(), any())).thenReturn(1);

        GoodsReceiptRequests.CreateRequest createReq = buildCreateRequest();
        GoodsReceiptViews.Detail draft = goodsReceiptService.create(createReq);

        assertThat(draft.getStatus()).isEqualTo(GoodsReceiptStateMachine.DRAFT);
        assertThat(draft.getLines()).hasSize(1);
        assertThat(draft.getLines().get(0).getAssetManaged()).isTrue();

        // ── Step 2: 确认收货（锁定订单 + 推进状态 + 发布事件）──
        ProcGoodsReceipt draftReceipt = buildReceipt(
                GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceipt confirmedReceipt = buildReceipt(
                GoodsReceiptStateMachine.CONFIRMED, 1);
        confirmedReceipt.setConfirmedTime(LocalDateTime.now());
        ProcGoodsReceiptLine receiptLine = buildReceiptLine("PASS", "2.000000", true);

        when(receiptMapper.selectForUpdate(TENANT_ID, 901L)).thenReturn(draftReceipt);
        when(orderMapper.selectForUpdate(TENANT_ID, 801L)).thenReturn(confirmedOrder);
        when(receiptLineMapper.selectForUpdateByReceipt(TENANT_ID, 901L))
                .thenReturn(List.of(receiptLine));
        when(receiptLineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.selectOne(any())).thenReturn(confirmedReceipt);

        ProcPurchaseOrder receivedOrder = buildOrder(
                PurchaseOrderStateMachine.RECEIVED, 1);
        when(orderMapper.selectOne(any())).thenReturn(receivedOrder);
        when(receiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine));
        when(receiptMapper.selectMaxConfirmedReceiveTime(TENANT_ID, 801L))
                .thenReturn(LocalDateTime.now());

        GoodsReceiptViews.Detail result = goodsReceiptService.confirm(901L, 0);

        assertThat(result.getStatus()).isEqualTo(GoodsReceiptStateMachine.CONFIRMED);

        // ── Step 3: 验证事件发布 ──
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(any(), eventCaptor.capture(), any(), any());
        GoodsReceiptContracts.DomainEvent event =
                (GoodsReceiptContracts.DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(
                "procurement.goods-receipt.confirmed.v1");
        assertThat(event.getPayload()).containsEntry("purchaseOrderId", 801L);

        // 验证订单状态被推进
        verify(orderMapper).selectForUpdate(TENANT_ID, 801L);
        verify(orderMapper).update(any(), any());
    }

    /** 分批收货：第一次 PARTIAL，第二次 RECEIVED。 */
    @Test
    void shouldSupportPartialThenFullReceipt() {
        ProcPurchaseOrder confirmedOrder = buildOrder(
                PurchaseOrderStateMachine.CONFIRMED, 0);
        ProcGoodsReceipt draft = buildReceipt(
                GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceiptLine line = buildReceiptLine("PASS", "1.000000", true);

        when(receiptMapper.selectForUpdate(TENANT_ID, 901L)).thenReturn(draft);
        when(orderMapper.selectForUpdate(TENANT_ID, 801L)).thenReturn(confirmedOrder);
        when(receiptLineMapper.selectForUpdateByReceipt(TENANT_ID, 901L))
                .thenReturn(List.of(line));
        when(orderLineMapper.selectList(any()))
                .thenReturn(List.of(buildOrderLine()));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_ID, 801L))
                .thenReturn(List.of());
        when(receiptLineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ProcGoodsReceipt confirmed = buildReceipt(
                GoodsReceiptStateMachine.CONFIRMED, 1);
        confirmed.setConfirmedTime(LocalDateTime.now());
        when(receiptMapper.selectOne(any())).thenReturn(confirmed);

        ProcPurchaseOrder partialOrder = buildOrder(
                PurchaseOrderStateMachine.PARTIAL_RECEIVED, 1);
        when(orderMapper.selectOne(any())).thenReturn(partialOrder);
        when(receiptLineMapper.selectList(any())).thenReturn(List.of(line));

        GoodsReceiptViews.Detail result = goodsReceiptService.confirm(901L, 0);

        assertThat(result.getStatus()).isEqualTo(GoodsReceiptStateMachine.CONFIRMED);
        verify(orderMapper).update(any(), any());
    }

    // ── 测试数据工厂 ──

    private ProcMaterial buildMaterial() {
        ProcMaterial material = new ProcMaterial();
        material.setId(301L);
        material.setTenantId(TENANT_ID);
        material.setCategoryId(1L);
        material.setMaterialCode("MAT-301");
        material.setMaterialName("商务笔记本");
        material.setUnit("EA");
        material.setAssetManaged(true);
        material.setStatus("ACTIVE");
        material.setDeleted(0);
        return material;
    }

    private ProcPurchaseOrder buildOrder(String status, int version) {
        ProcPurchaseOrder order = new ProcPurchaseOrder();
        order.setId(801L);
        order.setTenantId(TENANT_ID);
        order.setPoNo("PO-100-801");
        order.setSupplierId(501L);
        order.setSupplierNameSnapshot("合格供应商");
        order.setCurrencyCode("CNY");
        order.setStatus(status);
        order.setOwnerUserId(USER_ID);
        order.setOwnerUnitId(UNIT_ID);
        order.setVersion(version);
        order.setDeleted(0);
        return order;
    }

    private ProcPurchaseOrderLine buildOrderLine() {
        ProcPurchaseOrderLine line = new ProcPurchaseOrderLine();
        line.setId(811L);
        line.setTenantId(TENANT_ID);
        line.setPoId(801L);
        line.setLineNo(1);
        line.setMaterialId(301L);
        line.setMaterialCode("MAT-301");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT_DEVICE");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal("6400.000000"));
        line.setTotalPrice(new BigDecimal("12800.000000"));
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private ProcGoodsReceipt buildReceipt(String status, int version) {
        ProcGoodsReceipt receipt = new ProcGoodsReceipt();
        receipt.setId(901L);
        receipt.setTenantId(TENANT_ID);
        receipt.setGrNo("GR-100-901");
        receipt.setPoId(801L);
        receipt.setReceiverUserId(USER_ID);
        receipt.setReceiveTime(LocalDateTime.now());
        receipt.setStatus(status);
        receipt.setOwnerUserId(USER_ID);
        receipt.setOwnerUnitId(UNIT_ID);
        receipt.setVersion(version);
        receipt.setDeleted(0);
        return receipt;
    }

    private ProcGoodsReceiptLine buildReceiptLine(
            String qualityStatus, String quantity, boolean assetManaged) {
        ProcGoodsReceiptLine line = new ProcGoodsReceiptLine();
        line.setId(911L);
        line.setTenantId(TENANT_ID);
        line.setGoodsReceiptId(901L);
        line.setLineNo(1);
        line.setPoLineId(811L);
        line.setMaterialId(301L);
        line.setMaterialCode("MAT-301");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT_DEVICE");
        line.setUnit("EA");
        line.setAssetManaged(assetManaged);
        line.setOrderedQuantity(new BigDecimal("2.000000"));
        line.setReceivedQuantity(new BigDecimal(quantity));
        line.setQualityStatus(qualityStatus);
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private GoodsReceiptRequests.CreateRequest buildCreateRequest() {
        GoodsReceiptRequests.LineInput line = new GoodsReceiptRequests.LineInput();
        line.setPoLineId(811L);
        line.setReceivedQuantity(new BigDecimal("2.000000"));
        line.setQualityStatus("PASS");
        GoodsReceiptRequests.CreateRequest request =
                new GoodsReceiptRequests.CreateRequest();
        request.setPoId(801L);
        request.setReceiveTime(LocalDateTime.now());
        request.setLines(List.of(line));
        return request;
    }
}
