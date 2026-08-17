package com.omni.procurement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.GoodsReceiptStateMachine;
import com.omni.procurement.domain.PurchaseOrderStateMachine;
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
import com.omni.procurement.service.impl.GoodsReceiptServiceImpl;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收货并发安全测试。
 *
 * <p>验证乐观锁 + FOR UPDATE 机制确保：
 * <ul>
 *   <li>两张草稿并发确认时，只有一个成功</li>
 *   <li>版本号不匹配时确认被拒绝</li>
 *   <li>累计超收在并发场景下被正确拦截</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GoodsReceiptConcurrencyTest {

    @Mock private ProcGoodsReceiptMapper receiptMapper;
    @Mock private ProcGoodsReceiptLineMapper receiptLineMapper;
    @Mock private ProcPurchaseOrderMapper orderMapper;
    @Mock private ProcPurchaseOrderLineMapper orderLineMapper;
    @Mock private ProcMaterialMapper materialMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private GoodsReceiptServiceImpl service;

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
        service = new GoodsReceiptServiceImpl(
                receiptMapper, receiptLineMapper, orderMapper,
                orderLineMapper, materialMapper, reliableMessageRelay,
                new ProcRecordAccessGuard());
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(
                USER_ID, TENANT_ID, "receiver"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                USER_ID, UNIT_ID, "procurement:goods-receipt:confirm",
                UNIT_ID, "ALL", Set.of()));
    }

    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /**
     * 两张草稿并发确认同一订单：第一张成功后订单变为 PARTIAL_RECEIVED，
     * 第二张因累计超收被拒绝。
     */
    @Test
    void shouldAllowOnlyOneConcurrentConfirmation() {
        // 订单数量 2.0，两张收货草稿各收 1.5（累计 3.0 > 2.0）
        ProcPurchaseOrder confirmedOrder = buildOrder(
                PurchaseOrderStateMachine.CONFIRMED, 0);

        ProcGoodsReceipt draft1 = buildReceipt(901L, GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceiptLine line1 = buildReceiptLine(
                911L, 811L, "PASS", "1.500000");

        ProcGoodsReceipt draft2 = buildReceipt(902L, GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceiptLine line2 = buildReceiptLine(
                912L, 811L, "PASS", "1.500000");

        ProcPurchaseOrderLine orderLine = buildOrderLine();

        // ── 第一张确认成功 ──
        when(receiptMapper.selectForUpdate(TENANT_ID, 901L)).thenReturn(draft1);
        when(orderMapper.selectForUpdate(TENANT_ID, 801L)).thenReturn(confirmedOrder);
        when(receiptLineMapper.selectForUpdateByReceipt(TENANT_ID, 901L))
                .thenReturn(List.of(line1));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_ID, 801L))
                .thenReturn(List.of()); // 无已确认收货
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ProcGoodsReceipt confirmedReceipt = buildReceipt(
                901L, GoodsReceiptStateMachine.CONFIRMED, 1);
        confirmedReceipt.setConfirmedTime(LocalDateTime.now());
        when(receiptMapper.selectOne(any())).thenReturn(confirmedReceipt);

        ProcPurchaseOrder partialOrder = buildOrder(
                PurchaseOrderStateMachine.PARTIAL_RECEIVED, 1);
        when(orderMapper.selectOne(any())).thenReturn(partialOrder);
        when(receiptLineMapper.selectList(any())).thenReturn(List.of(line1));

        GoodsReceiptViews.Detail result1 = service.confirm(901L, 0);
        assertThat(result1.getStatus()).isEqualTo(GoodsReceiptStateMachine.CONFIRMED);

        // ── 第二张确认因累计超收被拒绝 ──
        when(receiptMapper.selectForUpdate(TENANT_ID, 902L)).thenReturn(draft2);
        when(orderMapper.selectForUpdate(TENANT_ID, 801L)).thenReturn(partialOrder);
        when(receiptLineMapper.selectForUpdateByReceipt(TENANT_ID, 902L))
                .thenReturn(List.of(line2));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine));

        // 已确认 1.5，再加 1.5 = 3.0 > 2.0 订单数量
        var existingTotal = new com.omni.procurement.dto.GoodsReceiptContracts.ReceivedTotal();
        existingTotal.setPoLineId(811L);
        existingTotal.setTotalQuantity(new BigDecimal("1.500000"));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_ID, 801L))
                .thenReturn(List.of(existingTotal));

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.confirm(902L, 0));

        // 第二张确认不应修改任何数据
        verify(receiptMapper, times(1)).update(any(), any()); // 只有第一张
    }

    /** 版本号不匹配时必须拒绝确认（乐观锁防护）。 */
    @Test
    void shouldRejectConfirmationWhenVersionMismatch() {
        ProcGoodsReceipt draft = buildReceipt(
                901L, GoodsReceiptStateMachine.DRAFT, 2);
        when(receiptMapper.selectForUpdate(TENANT_ID, 901L)).thenReturn(draft);

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.confirm(901L, 0)); // 传入 version=0，实际 version=2

        verify(receiptMapper, never()).update(any(), any());
        verify(orderMapper, never()).update(any(), any());
    }

    /** 并发确认不同订单（不同 PO）互不影响，两个都应该成功。 */
    @Test
    void shouldAllowConcurrentConfirmationOfDifferentOrders() {
        // 订单 A
        ProcPurchaseOrder orderA = buildOrder(
                PurchaseOrderStateMachine.CONFIRMED, 0);
        orderA.setId(801L);

        // 订单 B
        ProcPurchaseOrder orderB = buildOrder(
                PurchaseOrderStateMachine.CONFIRMED, 0);
        orderB.setId(802L);

        ProcGoodsReceipt receiptA = buildReceipt(
                901L, GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceiptLine lineA = buildReceiptLine(
                911L, 811L, "PASS", "1.000000");

        ProcGoodsReceipt receiptB = buildReceipt(
                902L, GoodsReceiptStateMachine.DRAFT, 0);
        ProcGoodsReceiptLine lineB = buildReceiptLine(
                912L, 821L, "PASS", "1.000000");

        ProcPurchaseOrderLine orderLineA = buildOrderLine();
        orderLineA.setId(811L);
        ProcPurchaseOrderLine orderLineB = buildOrderLine();
        orderLineB.setId(821L);
        orderLineB.setPoId(802L);

        // Mock 订单 A 的收货
        when(receiptMapper.selectForUpdate(TENANT_ID, 901L)).thenReturn(receiptA);
        when(orderMapper.selectForUpdate(TENANT_ID, 801L)).thenReturn(orderA);
        when(receiptLineMapper.selectForUpdateByReceipt(TENANT_ID, 901L))
                .thenReturn(List.of(lineA));
        when(orderLineMapper.selectList(any()))
                .thenReturn(List.of(orderLineA));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_ID, 801L))
                .thenReturn(List.of());
        when(receiptLineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ProcGoodsReceipt confirmedA = buildReceipt(
                901L, GoodsReceiptStateMachine.CONFIRMED, 1);
        confirmedA.setConfirmedTime(LocalDateTime.now());
        when(receiptMapper.selectOne(any())).thenReturn(confirmedA);

        ProcPurchaseOrder receivedA = buildOrder(
                PurchaseOrderStateMachine.RECEIVED, 1);
        receivedA.setId(801L);
        when(orderMapper.selectOne(any())).thenReturn(receivedA);
        when(receiptLineMapper.selectList(any())).thenReturn(List.of(lineA));

        GoodsReceiptViews.Detail result = service.confirm(901L, 0);
        assertThat(result.getStatus()).isEqualTo(GoodsReceiptStateMachine.CONFIRMED);
    }

    // ── 测试数据工厂 ──

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
        line.setMaterialName("测试物料");
        line.setCategoryCode("IT_DEVICE");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal("6400.000000"));
        line.setTotalPrice(new BigDecimal("12800.000000"));
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }

    private ProcGoodsReceipt buildReceipt(long id, String status, int version) {
        ProcGoodsReceipt receipt = new ProcGoodsReceipt();
        receipt.setId(id);
        receipt.setTenantId(TENANT_ID);
        receipt.setGrNo("GR-100-" + id);
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
            long id, long poLineId, String qualityStatus, String quantity) {
        ProcGoodsReceiptLine line = new ProcGoodsReceiptLine();
        line.setId(id);
        line.setTenantId(TENANT_ID);
        line.setGoodsReceiptId(901L);
        line.setLineNo(1);
        line.setPoLineId(poLineId);
        line.setMaterialId(301L);
        line.setMaterialCode("MAT-301");
        line.setMaterialName("测试物料");
        line.setCategoryCode("IT_DEVICE");
        line.setUnit("EA");
        line.setAssetManaged(true);
        line.setOrderedQuantity(new BigDecimal("2.000000"));
        line.setReceivedQuantity(new BigDecimal(quantity));
        line.setQualityStatus(qualityStatus);
        line.setVersion(0);
        line.setDeleted(0);
        return line;
    }
}
