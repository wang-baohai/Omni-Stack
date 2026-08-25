package com.omni.asset.consumer;

import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.common.service.observability.InboxMetrics;
import com.omni.asset.service.ProcurementAssetImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.slf4j.MDC;

import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Procurement 收货与质检通过事件消费者。
 *
 * @author Omni-Stack Team
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
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
    private static final String PRODUCER_TRACE_HEADER = "omniProducerTraceId";
    private static final String MESSAGE_ID_HEADER = "omniMessageId";
    private static final Pattern SAFE_LOG_FIELD = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final ProcurementAssetImportService importService;

    /**
     * 消费支持的 v1 收货事件，并始终清理消息线程租户上下文。
     *
     * @return 消息消费函数
     */
    @Bean(name = "procurementGoodsReceiptFunction")
    public Consumer<Message<ProcurementAssetContracts.GoodsReceiptEvent>>
            procurementGoodsReceiptFunction() {
        return message -> {
            if (message == null) {
                return;
            }
            ProcurementAssetContracts.GoodsReceiptEvent event = message.getPayload();
            log.info("Asset Inbox 收到采购事件 msgId={}, producerTraceId={}, consumerTraceId={}",
                    safeHeader(message, MESSAGE_ID_HEADER),
                    safeHeader(message, PRODUCER_TRACE_HEADER), MDC.get("traceId"));
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
                ServiceIdentityContext.set(new ServiceRequestIdentity(
                        0L, event.getTenantId(), "procurement-event"));
                ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                        0L, event.getTenantId(), "asset:procurement:import",
                        null, "TENANT", Set.of(), null));
                importService.importEvent(event);
                InboxMetrics.record("procurement-goods-receipt", "success");
            } catch (RuntimeException exception) {
                InboxMetrics.record("procurement-goods-receipt", "retry");
                throw exception;
            } finally {
                ServiceDataScopeContext.clear();
                ServiceIdentityContext.clear();
            }
        };
    }

    /** 只允许固定格式的关联字段进入日志，避免外部 Broker 头注入控制字符。 */
    private String safeHeader(Message<?> message, String name) {
        Object value = message.getHeaders().get(name);
        String text = value == null ? null : value.toString();
        return text != null && SAFE_LOG_FIELD.matcher(text).matches() ? text : null;
    }
}
