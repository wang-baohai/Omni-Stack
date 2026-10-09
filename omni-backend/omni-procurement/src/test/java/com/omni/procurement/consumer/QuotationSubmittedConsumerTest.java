package com.omni.procurement.consumer;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.dto.RfqContracts;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.procurement.service.QuotationSubmittedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 报价提交消息消费者上下文与路由测试。 */
class QuotationSubmittedConsumerTest {

    /** 清理测试线程上下文。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 有效事件必须绑定租户上下文、调用服务并在结束后清理。 */
    @Test
    void shouldRouteValidEventAndAlwaysClearContext() {
        QuotationSubmittedService service = mock(QuotationSubmittedService.class);
        Consumer<RfqContracts.QuotationSubmittedEvent> consumer =
                new QuotationSubmittedConsumer(service).quotationSubmittedFunction();
        RfqContracts.QuotationSubmittedEvent event = event(41L);

        consumer.accept(event);

        verify(service).handle(event);
        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .isInstanceOf(BusinessException.class);
        org.assertj.core.api.Assertions.assertThat(ServiceDataScopeContext.get()).isNull();
    }

    /** 非法租户必须在创建线程上下文和调用业务服务前拒绝。 */
    @Test
    void shouldRejectInvalidTenantBeforeBindingContext() {
        QuotationSubmittedService service = mock(QuotationSubmittedService.class);
        Consumer<RfqContracts.QuotationSubmittedEvent> consumer =
                new QuotationSubmittedConsumer(service).quotationSubmittedFunction();
        RfqContracts.QuotationSubmittedEvent event = event(0L);

        assertThatThrownBy(() -> consumer.accept(event))
                .isInstanceOf(IllegalArgumentException.class);

        verify(service, never()).handle(event);
        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .isInstanceOf(BusinessException.class);
    }

    /** 同一主题上的其他 SRM 事件必须由本消费者忽略。 */
    @Test
    void shouldIgnoreUnrelatedSrmEvent() {
        QuotationSubmittedService service = mock(QuotationSubmittedService.class);
        Consumer<RfqContracts.QuotationSubmittedEvent> consumer =
                new QuotationSubmittedConsumer(service).quotationSubmittedFunction();
        RfqContracts.QuotationSubmittedEvent event = event(41L);
        event.setEventType("srm.supplier.updated.v1");

        consumer.accept(event);

        verify(service, never()).handle(event);
    }

    private RfqContracts.QuotationSubmittedEvent event(Long tenantId) {
        RfqContracts.QuotationSubmittedEvent event =
                new RfqContracts.QuotationSubmittedEvent();
        event.setTenantId(tenantId);
        event.setEventType("srm.quotation.submitted.v1");
        event.setAggregateType("QUOTATION");
        return event;
    }
}
