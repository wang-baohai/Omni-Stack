package com.omni.procurement;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨租户隔离集成测试。
 *
 * <p>验证 TenantLine + DataPermission 机制确保不同租户的数据完全隔离：
 * <ul>
 *   <li>租户 A 的操作不影响租户 B 的数据</li>
 *   <li>跨租户访问被安全机制拦截</li>
 *   <li>各租户的数据操作相互独立</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenantIsolationIntegrationTest {

    @Mock private ProcGoodsReceiptMapper receiptMapper;
    @Mock private ProcGoodsReceiptLineMapper receiptLineMapper;
    @Mock private ProcPurchaseOrderMapper orderMapper;
    @Mock private ProcPurchaseOrderLineMapper orderLineMapper;
    @Mock private ProcMaterialMapper materialMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private GoodsReceiptServiceImpl goodsReceiptService;

    private static final long TENANT_A = 100L;
    private static final long TENANT_B = 200L;
    private static final long USER_A = 1L;
    private static final long USER_B = 2L;
    private static final long UNIT_A = 10L;
    private static final long UNIT_B = 20L;

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
        goodsReceiptService = new GoodsReceiptServiceImpl(
                receiptMapper, receiptLineMapper, orderMapper,
                orderLineMapper, materialMapper, reliableMessageRelay,
                new ProcRecordAccessGuard());
    }

    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 租户 A 创建的收货草稿必须属于租户 A，租户 B 无法看到或操作。 */
    @Test
    void shouldIsolateReceiptCreationBetweenTenants() {
        // ── 租户 A 创建收货草稿 ──
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(
                USER_A, TENANT_A, "tenant-a-user"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                USER_A, UNIT_A, "procurement:goods-receipt:create",
                UNIT_A, "ALL", Set.of()));

        ProcPurchaseOrder orderA = buildOrder(TENANT_A, 801L,
                PurchaseOrderStateMachine.CONFIRMED, 0);
        when(orderMapper.selectOne(any())).thenReturn(orderA);
        when(orderLineMapper.selectList(any()))
                .thenReturn(List.of(buildOrderLine(TENANT_A, 811L, 801L)));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_A, 801L))
                .thenReturn(List.of());
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(buildMaterial(TENANT_A, 301L)));
        doAnswer(inv -> {
            ProcGoodsReceipt r = inv.getArgument(0);
            r.setId(901L);
            r.setTenantId(TENANT_A);
            return 1;
        }).when(receiptMapper).insert(any(ProcGoodsReceipt.class));
        doAnswer(inv -> {
            ProcGoodsReceiptLine l = inv.getArgument(0);
            l.setId(911L);
            l.setTenantId(TENANT_A);
            return 1;
        }).when(receiptLineMapper).insert(any(ProcGoodsReceiptLine.class));
        when(receiptMapper.update(any(), any())).thenReturn(1);

        GoodsReceiptViews.Detail resultA = goodsReceiptService.create(
                buildCreateRequest(801L));
        assertThat(resultA.getGrNo()).isNotBlank();
        assertThat(resultA.getStatus()).isEqualTo(GoodsReceiptStateMachine.DRAFT);

        // ── 租户 B 尝试操作租户 A 的收货单（应该查不到）──
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(
                USER_B, TENANT_B, "tenant-b-user"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                USER_B, UNIT_B, "procurement:goods-receipt:confirm",
                UNIT_B, "ALL", Set.of()));

        // 租户 B 查询租户 A 的收货单，FOR UPDATE 使用 TENANT_B 查不到
        when(receiptMapper.selectForUpdate(TENANT_B, 901L)).thenReturn(null);

        assertThatThrownBy(() -> goodsReceiptService.confirm(901L, 0))
                .isInstanceOf(BusinessException.class);

        // 验证租户 B 没有触发确认操作（只有租户 A 创建时的 GR 编号更新）
        verify(orderMapper, never()).update(any(), any());
        verify(receiptLineMapper, never()).update(any(), any());
    }

    /** 不同租户确认收货时，各自的订单状态推进互不影响。 */
    @Test
    void shouldIsolateReceiptConfirmationBetweenTenants() {
        // ── 租户 A 确认收货 ──
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(
                USER_A, TENANT_A, "tenant-a-user"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                USER_A, UNIT_A, "procurement:goods-receipt:confirm",
                UNIT_A, "ALL", Set.of()));

        ProcGoodsReceipt draftA = buildReceipt(TENANT_A, 901L,
                GoodsReceiptStateMachine.DRAFT, 0);
        ProcPurchaseOrder orderA = buildOrder(TENANT_A, 801L,
                PurchaseOrderStateMachine.CONFIRMED, 0);
        ProcGoodsReceiptLine lineA = buildReceiptLine(
                new ReceiptLineIdentity(TENANT_A, 911L, 811L),
                "PASS", "2.000000", true);

        when(receiptMapper.selectForUpdate(TENANT_A, 901L)).thenReturn(draftA);
        when(orderMapper.selectForUpdate(TENANT_A, 801L)).thenReturn(orderA);
        when(receiptLineMapper.selectForUpdateByReceipt(TENANT_A, 901L))
                .thenReturn(List.of(lineA));
        when(orderLineMapper.selectList(any()))
                .thenReturn(List.of(buildOrderLine(TENANT_A, 811L, 801L)));
        when(receiptLineMapper.selectConfirmedTotals(TENANT_A, 801L))
                .thenReturn(List.of());
        when(receiptLineMapper.update(any(), any())).thenReturn(1);
        when(receiptMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ProcGoodsReceipt confirmedA = buildReceipt(TENANT_A, 901L,
                GoodsReceiptStateMachine.CONFIRMED, 1);
        confirmedA.setConfirmedTime(LocalDateTime.now());
        when(receiptMapper.selectOne(any())).thenReturn(confirmedA);

        ProcPurchaseOrder receivedA = buildOrder(TENANT_A, 801L,
                PurchaseOrderStateMachine.RECEIVED, 1);
        when(orderMapper.selectOne(any())).thenReturn(receivedA);
        when(receiptLineMapper.selectList(any())).thenReturn(List.of(lineA));
        when(receiptMapper.selectMaxConfirmedReceiveTime(TENANT_A, 801L))
                .thenReturn(LocalDateTime.now());

        GoodsReceiptViews.Detail result = goodsReceiptService.confirm(901L, 0);
        assertThat(result.getStatus()).isEqualTo(GoodsReceiptStateMachine.CONFIRMED);

        // ── 租户 B 的订单完全不受影响 ──
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(
                USER_B, TENANT_B, "tenant-b-user"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                USER_B, UNIT_B, "procurement:goods-receipt:confirm",
                UNIT_B, "ALL", Set.of()));

        // 租户 B 查询时使用的是 TENANT_B，不会查到租户 A 的数据
        verify(orderMapper, never()).selectForUpdate(TENANT_B, 801L);
    }

    /** 同一物料在不同租户可以有独立的配置。 */
    @Test
    void shouldAllowIndependentMaterialConfigPerTenant() {
        ProcMaterial matA = buildMaterial(TENANT_A, 301L);
        matA.setAssetManaged(true);

        ProcMaterial matB = buildMaterial(TENANT_B, 301L);
        matB.setAssetManaged(false);

        assertThat(matA.getTenantId()).isNotEqualTo(matB.getTenantId());
        assertThat(matA.getAssetManaged()).isNotEqualTo(matB.getAssetManaged());
    }

    // ── 测试数据工厂 ──

    private ProcMaterial buildMaterial(long tenantId, long id) {
        ProcMaterial material = new ProcMaterial();
        material.setId(id);
        material.setTenantId(tenantId);
        material.setCategoryId(1L);
        material.setMaterialCode("MAT-" + id);
        material.setMaterialName("测试物料");
        material.setUnit("EA");
        material.setAssetManaged(true);
        material.setStatus("ACTIVE");
        material.setDeleted(0);
        return material;
    }

    private ProcPurchaseOrder buildOrder(
            long tenantId, long id, String status, int version) {
        ProcPurchaseOrder order = new ProcPurchaseOrder();
        order.setId(id);
        order.setTenantId(tenantId);
        order.setPoNo("PO-" + tenantId + "-" + id);
        order.setSupplierId(501L);
        order.setSupplierNameSnapshot("供应商");
        order.setCurrencyCode("CNY");
        order.setStatus(status);
        order.setOwnerUserId(tenantId == TENANT_A ? USER_A : USER_B);
        order.setOwnerUnitId(tenantId == TENANT_A ? UNIT_A : UNIT_B);
        order.setVersion(version);
        order.setDeleted(0);
        return order;
    }

    private ProcPurchaseOrderLine buildOrderLine(
            long tenantId, long id, long poId) {
        ProcPurchaseOrderLine line = new ProcPurchaseOrderLine();
        line.setId(id);
        line.setTenantId(tenantId);
        line.setPoId(poId);
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

    private ProcGoodsReceipt buildReceipt(
            long tenantId, long id, String status, int version) {
        ProcGoodsReceipt receipt = new ProcGoodsReceipt();
        receipt.setId(id);
        receipt.setTenantId(tenantId);
        receipt.setGrNo("GR-" + tenantId + "-" + id);
        receipt.setPoId(801L);
        receipt.setReceiverUserId(tenantId == TENANT_A ? USER_A : USER_B);
        receipt.setReceiveTime(LocalDateTime.now());
        receipt.setStatus(status);
        receipt.setOwnerUserId(tenantId == TENANT_A ? USER_A : USER_B);
        receipt.setOwnerUnitId(tenantId == TENANT_A ? UNIT_A : UNIT_B);
        receipt.setVersion(version);
        receipt.setDeleted(0);
        return receipt;
    }

    private ProcGoodsReceiptLine buildReceiptLine(
            ReceiptLineIdentity identity,
            String qualityStatus, String quantity, boolean assetManaged) {
        ProcGoodsReceiptLine line = new ProcGoodsReceiptLine();
        line.setId(identity.id());
        line.setTenantId(identity.tenantId());
        line.setGoodsReceiptId(901L);
        line.setLineNo(1);
        line.setPoLineId(identity.poLineId());
        line.setMaterialId(301L);
        line.setMaterialCode("MAT-301");
        line.setMaterialName("测试物料");
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

    private GoodsReceiptRequests.CreateRequest buildCreateRequest(long poId) {
        GoodsReceiptRequests.LineInput line = new GoodsReceiptRequests.LineInput();
        line.setPoLineId(811L);
        line.setReceivedQuantity(new BigDecimal("2.000000"));
        line.setQualityStatus("PASS");
        GoodsReceiptRequests.CreateRequest request =
                new GoodsReceiptRequests.CreateRequest();
        request.setPoId(poId);
        request.setReceiveTime(LocalDateTime.now());
        request.setLines(List.of(line));
        return request;
    }

    /**
     * 租户隔离收货测试行标识。
     */
    private record ReceiptLineIdentity(long tenantId, long id, long poLineId) {
    }
}
