package com.omni.asset.consumer;

import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.asset.security.AssetDataScopeContext;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.ProcurementAssetImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Procurement 收货事件消费者测试。
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class ProcurementGoodsReceiptConsumerTest {

    @Mock private ProcurementAssetImportService importService;

    /** 清理可能残留的租户上下文。 */
    @AfterEach
    void tearDown() {
        AssetTenantContext.clear();
        AssetDataScopeContext.clear();
    }

    /** 验证消费期间显式设置租户，结束后 finally 清理。 */
    @Test
    void should_set_and_clear_tenant_context_when_consuming_supported_event() {
        ProcurementGoodsReceiptConsumer configuration =
                new ProcurementGoodsReceiptConsumer(importService);
        Consumer<ProcurementAssetContracts.GoodsReceiptEvent> consumer =
                configuration.procurementGoodsReceiptFunction();
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                ProcurementGoodsReceiptConsumer.CONFIRMED_EVENT, 41L);

        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(AssetDataScopeContext.require().effectiveScope()).isEqualTo("TENANT");
            assertThat(AssetDataScopeContext.require().tenantId()).isEqualTo(41L);
            return null;
        }).when(importService).importEvent(event);

        consumer.accept(event);

        verify(importService).importEvent(event);
        assertThatThrownBy(AssetTenantContext::requireTenantId)
                .hasMessageContaining("上下文");
        assertThatThrownBy(AssetDataScopeContext::require)
                .hasMessageContaining("上下文");
    }

    /** 验证收货事件 v2 不会被静默按 v1 处理。 */
    @Test
    void should_reject_unsupported_goods_receipt_event_version() {
        ProcurementGoodsReceiptConsumer configuration =
                new ProcurementGoodsReceiptConsumer(importService);
        Consumer<ProcurementAssetContracts.GoodsReceiptEvent> consumer =
                configuration.procurementGoodsReceiptFunction();
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "procurement.goods-receipt.confirmed.v2", 41L);

        assertThatThrownBy(() -> consumer.accept(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
        verify(importService, never()).importEvent(event);
    }

    /** 验证同一 Procurement Topic 上其他聚合事件会被忽略。 */
    @Test
    void should_ignore_unrelated_procurement_domain_event() {
        ProcurementGoodsReceiptConsumer configuration =
                new ProcurementGoodsReceiptConsumer(importService);
        Consumer<ProcurementAssetContracts.GoodsReceiptEvent> consumer =
                configuration.procurementGoodsReceiptFunction();
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "procurement.requisition.approved.v1", 41L);

        consumer.accept(event);

        verify(importService, never()).importEvent(event);
    }

    /** 验证支持事件缺 tenant 时失败关闭。 */
    @Test
    void should_fail_closed_when_tenant_missing() {
        ProcurementGoodsReceiptConsumer configuration =
                new ProcurementGoodsReceiptConsumer(importService);
        Consumer<ProcurementAssetContracts.GoodsReceiptEvent> consumer =
                configuration.procurementGoodsReceiptFunction();
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                ProcurementGoodsReceiptConsumer.QUALITY_PASSED_EVENT, null);

        assertThatThrownBy(() -> consumer.accept(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        verify(importService, never()).importEvent(event);
    }

    private ProcurementAssetContracts.GoodsReceiptEvent event(
            String eventType, Long tenantId) {
        ProcurementAssetContracts.GoodsReceiptEvent event =
                new ProcurementAssetContracts.GoodsReceiptEvent();
        event.setEventId("event-consumer");
        event.setEventType(eventType);
        event.setTenantId(tenantId);
        return event;
    }
}
