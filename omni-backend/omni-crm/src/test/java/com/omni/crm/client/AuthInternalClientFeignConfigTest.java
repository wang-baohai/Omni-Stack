package com.omni.crm.client;

import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.internal.InternalFeignHeadersFactory;
import feign.RequestTemplate;
import feign.codec.Decoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.http.converter.HttpMessageConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Auth 内部 Feign 客户端配置测试。 */
class AuthInternalClientFeignConfigTest {

    /** CRM 专用 Auth Client 必须使用 Starter 工厂注入内部认证头。 */
    @Test
    void shouldUseStarterFactoryForInternalAuthentication() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.getInternalApi().setToken("0123456789abcdef0123456789abcdef");
        RequestTemplate template = new RequestTemplate();

        new AuthInternalClient.FeignConfig().internalTokenInterceptor(
                properties, new InternalFeignHeadersFactory()).apply(template);

        assertThat(template.headers().get("X-Internal-Token"))
                .containsExactly("0123456789abcdef0123456789abcdef");
    }

    /** 解码器创建时必须完成消息转换器预热，禁止延迟到首次并发请求。 */
    @Test
    void shouldWarmMessageConvertersWhenDecoderIsCreated() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        TrackingMessageConverters converters = new TrackingMessageConverters(
                beanFactory.getBeanProvider(ClientHttpMessageConvertersCustomizer.class),
                beanFactory.getBeanProvider(HttpMessageConverterCustomizer.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<FeignHttpMessageConverters> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(converters);

        Decoder decoder = new AuthInternalClient.FeignConfig().feignDecoder(provider);

        assertThat(decoder).isNotNull();
        assertThat(converters.initialized).isTrue();
        assertThat(converters.getConverters()).isNotEmpty();
    }

    /** 记录消息转换器是否已被主动初始化。 */
    private static final class TrackingMessageConverters extends FeignHttpMessageConverters {
        private boolean initialized;

        private TrackingMessageConverters(
                ObjectProvider<ClientHttpMessageConvertersCustomizer> customizers,
                ObjectProvider<HttpMessageConverterCustomizer> cloudCustomizers) {
            super(customizers, cloudCustomizers);
        }

        @Override
        public List<HttpMessageConverter<?>> getConverters() {
            initialized = true;
            return super.getConverters();
        }
    }
}
