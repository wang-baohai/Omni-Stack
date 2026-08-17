package com.omni.asset.consumer;

import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.asset.security.AssetDataScopeContext;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.ProcurementAssetImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Procurement 收货与质检通过事件消费者。
 *
 * @author Omni-Stack Team
 */
@Configuration
@RequiredArgsConstructor
public class ProcurementGoodsReceiptConsumer {

    /** 已确认收货 v1。 */
    public static final String CONFIRMED_EVENT =
            "procurement.goods-receipt.confirmed.v1";

    /** 质检通过 v1。 */
    public static final String QUALITY_PASSED_EVENT =
            "procurement.goods-receipt.quality-passed.v1";

    private static final String EVENT_PREFIX = "procurement.goods-receipt.";
    private static final Set<String> SUPPORTED_EVENTS =
            Set.of(CONFIRMED_EVENT, QUALITY_PASSED_EVENT);

    private final ProcurementAssetImportService importService;

    /**
     * 消费支持的 v1 收货事件，并始终清理消息线程租户上下文。
     *
     * @return 消息消费函数
     */
    @Bean(name = "procurementGoodsReceiptFunction")
    public Consumer<ProcurementAssetContracts.GoodsReceiptEvent>
            procurementGoodsReceiptFunction() {
        return event -> {
            if (event == null || event.getEventType() == null
                    || event.getEventType().isBlank()) {
                throw new IllegalArgumentException("Procurement 事件缺少事件类型版本");
            }
            if (!SUPPORTED_EVENTS.contains(event.getEventType())) {
                if (event.getEventType().startsWith(EVENT_PREFIX)) {
                    throw new IllegalArgumentException("不支持的 Procurement 收货事件版本");
                }
                return;
            }
            if (event.getTenantId() == null || event.getTenantId() <= 0) {
                throw new IllegalArgumentException("Procurement 收货事件 tenantId 必须为正整数");
            }
            try {
                AssetTenantContext.set(new AssetTenantContext.RequestIdentity(
                        0L, event.getTenantId(), "procurement-event"));
                AssetDataScopeContext.set(new AssetDataScopeContext.ScopeInfo(
                        0L, event.getTenantId(), "asset:procurement:import",
                        null, "TENANT", Set.of()));
                importService.importEvent(event);
            } finally {
                AssetDataScopeContext.clear();
                AssetTenantContext.clear();
            }
        };
    }
}
