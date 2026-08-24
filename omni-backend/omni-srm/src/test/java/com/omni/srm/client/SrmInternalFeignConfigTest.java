package com.omni.srm.client;

import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.internal.InternalFeignHeadersFactory;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SRM 内部 Feign 客户端统一认证头配置测试。 */
class SrmInternalFeignConfigTest {

    private static final String TOKEN = "srm-feign-test-token-0123456789abcdef";

    /** Auth、Procurement 与 Workflow 客户端都必须使用 Starter 工厂注入内部令牌。 */
    @Test
    void shouldUseStarterFactoryForEveryInternalClient() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.getInternalApi().setToken(TOKEN);
        InternalFeignHeadersFactory factory = new InternalFeignHeadersFactory();
        List<RequestInterceptor> interceptors = List.of(
                new AuthInternalClient.FeignConfig().internalTokenInterceptor(properties, factory),
                new ProcurementInternalClient.FeignConfig()
                        .procurementInternalHeadersInterceptor(properties, factory),
                new WorkflowInternalClient.FeignConfig()
                        .srmWorkflowInternalTokenInterceptor(properties, factory));

        for (RequestInterceptor interceptor : interceptors) {
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);
            assertThat(template.headers().get("X-Internal-Token")).containsExactly(TOKEN);
        }
    }
}
