package com.omni.procurement.consumer;

import com.omni.procurement.dto.RfqContracts;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.common.service.observability.InboxMetrics;
import com.omni.procurement.service.QuotationSubmittedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.function.Consumer;

/**
 * SRM 报价提交事件消费者。
 *
 * @author Omni-Stack Team
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class QuotationSubmittedConsumer {

    private static final String EVENT_TYPE = "srm.quotation.submitted.v1";
    private static final String AGGREGATE_TYPE = "QUOTATION";

    private final QuotationSubmittedService quotationSubmittedService;

    /**
     * 消费报价提交事件并在 finally 中清理消息线程上下文。
     *
     * @return 消息消费函数
     */
    @Bean(name = "quotationSubmittedFunction")
    public Consumer<RfqContracts.QuotationSubmittedEvent> quotationSubmittedFunction() {
        return event -> {
            log.info("收到 SRM 报价提交事件: eventId={}, aggregateId={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getAggregateId());
            if (event == null || !EVENT_TYPE.equals(event.getEventType())
                    || !AGGREGATE_TYPE.equals(event.getAggregateType())) {
                return;
            }
            if (event.getTenantId() == null || event.getTenantId() <= 0) {
                throw new IllegalArgumentException("SRM 报价提交事件 tenantId 必须为正整数");
            }
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    0L, event.getTenantId(), "srm-quotation-event"));
            ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                    0L, event.getTenantId(), "srm-quotation-event", null, "TENANT", Set.of(), null));
            try {
                quotationSubmittedService.handle(event);
                InboxMetrics.record("quotation-submitted", "success");
            } catch (RuntimeException exception) {
                InboxMetrics.record("quotation-submitted", "retry");
                log.error("SRM 报价提交事件处理失败: eventId={}, 异常类型={}, 异常信息={}",
                        event.getEventId(), exception.getClass().getName(), exception.getMessage());
                throw exception;
            } finally {
                ServiceDataScopeContext.clear();
                ServiceIdentityContext.clear();
            }
        };
    }
}
