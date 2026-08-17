package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.entity.ProcGoodsReceipt;
import com.omni.procurement.entity.ProcGoodsReceiptLine;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.entity.ProcPurchaseOrderLine;
import com.omni.procurement.mapper.ProcGoodsReceiptLineMapper;
import com.omni.procurement.mapper.ProcGoodsReceiptMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderMapper;
import com.omni.procurement.security.ProcDataScopeContext;
import com.omni.procurement.security.ProcTenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Asset 历史补偿游标查询测试。 */
@ExtendWith(MockitoExtension.class)
class InternalAssetCandidateServiceImplTest {

    @Mock private ProcGoodsReceiptLineMapper lineMapper;
    @Mock private ProcGoodsReceiptMapper receiptMapper;
    @Mock private ProcPurchaseOrderMapper orderMapper;
    @Mock private ProcPurchaseOrderLineMapper orderLineMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcGoodsReceipt.class, "ProcGoodsReceiptMapper");
        initialize(ProcGoodsReceiptLine.class, "ProcGoodsReceiptLineMapper");
        initialize(ProcPurchaseOrder.class, "ProcPurchaseOrderMapper");
        initialize(ProcPurchaseOrderLine.class, "ProcPurchaseOrderLineMapper");
    }

    /** 清理内部查询创建的线程上下文。 */
    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 回扫结果必须复用实时事件 ID，并返回完整采购快照和精确金额。 */
    @Test
    void shouldReturnCandidateWithOriginalEventIdentity() {
        InternalAssetCandidateServiceImpl service = service();
        ProcGoodsReceiptLine line = receiptLine();
        ProcGoodsReceipt receipt = receipt();
        ProcPurchaseOrder order = order();
        ProcPurchaseOrderLine orderLine = orderLine();
        when(lineMapper.selectAssetCandidateLines(41L, 900L, 20))
                .thenReturn(List.of(line));
        when(receiptMapper.selectList(any())).thenReturn(List.of(receipt));
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine));

        List<GoodsReceiptViews.AssetCandidate> result = service.list(41L, 900L, 20);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.getEventId()).isEqualTo("quality-event-1");
            assertThat(candidate.getGoodsReceiptId()).isEqualTo(901L);
            assertThat(candidate.getPurchaseOrderId()).isEqualTo(801L);
            assertThat(candidate.getSupplierId()).isEqualTo(501L);
            assertThat(candidate.getOwnerUserId()).isEqualTo(71L);
            assertThat(candidate.getOwnerUnitId()).isEqualTo(72L);
            assertThat(candidate.getGoodsReceiptLineId()).isEqualTo(911L);
            assertThat(candidate.getAssetQuantity()).isEqualTo(2L);
            assertThat(candidate.getUnitPrice()).isEqualByComparingTo("6400.000000");
            assertThat(candidate.getTotalPrice()).isEqualByComparingTo("12800.0000");
        });
        verify(lineMapper).selectAssetCandidateLines(41L, 900L, 20);
        assertThatThrownBy(ProcTenantContext::require)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
        assertThat(ProcDataScopeContext.get()).isNull();
    }

    /** 非法游标参数必须在访问数据库前拒绝。 */
    @Test
    void shouldRejectInvalidCursorArguments() {
        InternalAssetCandidateServiceImpl service = service();

        assertThatThrownBy(() -> service.list(41L, -1L, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        assertThatThrownBy(() -> service.list(41L, 0L, 101))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
    }

    /** 采购订单行不属于收货单来源订单时必须失败关闭。 */
    @Test
    void shouldRejectBrokenPurchaseSourceRelation() {
        InternalAssetCandidateServiceImpl service = service();
        ProcPurchaseOrderLine orderLine = orderLine();
        orderLine.setPoId(802L);
        when(lineMapper.selectAssetCandidateLines(41L, 0L, 100))
                .thenReturn(List.of(receiptLine()));
        when(receiptMapper.selectList(any())).thenReturn(List.of(receipt()));
        when(orderMapper.selectList(any())).thenReturn(List.of(order()));
        when(orderLineMapper.selectList(any())).thenReturn(List.of(orderLine));

        assertThatThrownBy(() -> service.list(41L, 0L, 100))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    private InternalAssetCandidateServiceImpl service() {
        return new InternalAssetCandidateServiceImpl(
                lineMapper, receiptMapper, orderMapper, orderLineMapper);
    }

    private ProcGoodsReceiptLine receiptLine() {
        ProcGoodsReceiptLine line = new ProcGoodsReceiptLine();
        line.setId(911L);
        line.setTenantId(41L);
        line.setGoodsReceiptId(901L);
        line.setPoLineId(811L);
        line.setMaterialId(301L);
        line.setMaterialCode("NB-001");
        line.setMaterialName("商务笔记本");
        line.setCategoryCode("IT");
        line.setUnit("EA");
        line.setAssetManaged(true);
        line.setReceivedQuantity(new BigDecimal("2.000000"));
        line.setQualityStatus("PASS");
        line.setQualityPassedEventId("quality-event-1");
        return line;
    }

    private ProcGoodsReceipt receipt() {
        ProcGoodsReceipt receipt = new ProcGoodsReceipt();
        receipt.setId(901L);
        receipt.setTenantId(41L);
        receipt.setGrNo("GR-41-901");
        receipt.setPoId(801L);
        receipt.setOwnerUserId(71L);
        receipt.setOwnerUnitId(72L);
        receipt.setReceiveTime(LocalDateTime.of(2026, 7, 22, 8, 30));
        return receipt;
    }

    private ProcPurchaseOrder order() {
        ProcPurchaseOrder order = new ProcPurchaseOrder();
        order.setId(801L);
        order.setTenantId(41L);
        order.setPoNo("PO-41-801");
        order.setSupplierId(501L);
        order.setSupplierNameSnapshot("合格供应商");
        order.setCurrencyCode("CNY");
        return order;
    }

    private ProcPurchaseOrderLine orderLine() {
        ProcPurchaseOrderLine line = new ProcPurchaseOrderLine();
        line.setId(811L);
        line.setTenantId(41L);
        line.setPoId(801L);
        line.setUnitPrice(new BigDecimal("6400.000000"));
        return line;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.mapper." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
