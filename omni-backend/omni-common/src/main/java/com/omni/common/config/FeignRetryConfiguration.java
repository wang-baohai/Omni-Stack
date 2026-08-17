package com.omni.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import org.slf4j.MDC;
import com.omni.common.web.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Feign 重试自动配置。
 * <p>
 * 覆盖 Spring Cloud OpenFeign 默认的 {@code Retryer.NEVER_RETRY}，
 * 在 Nacos 服务发现冷启动延迟或瞬时网络抖动时自动重试。
 *
 * @author Omni-Stack Team
 */
@AutoConfiguration
@ConditionalOnClass(Retryer.class)
public class FeignRetryConfiguration {

    /**
     * Feign 重试策略：100ms 初始间隔，1s 最大间隔，最多重试 5 次。
     * <p>
     * 覆盖 Nacos 客户端首次服务列表拉取的冷启动延迟（通常 1-3 秒）。
     *
     * @return Retryer 实例
     */
    @Bean
    @ConditionalOnMissingBean(Retryer.class)
    public Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 5);
    }

    /**
     * Feign JSON 解码器，复用公共日期和安全配置。
     *
     * @param objectMapper 公共 Jackson 2 ObjectMapper
     * @return Feign 解码器
     */
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnMissingBean(Decoder.class)
    public Decoder feignDecoder(ObjectMapper objectMapper) {
        return new JacksonFeignDecoder(objectMapper);
    }

    /**
     * 将当前 Servlet 请求追踪号传播到所有 Feign 下游调用。
     *
     * @return Feign 请求拦截器
     */
    @Bean
    public RequestInterceptor traceIdFeignRequestInterceptor() {
        return template -> {
            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            if (traceId != null && !traceId.isBlank()) {
                template.header(TraceIdFilter.TRACE_HEADER, traceId);
            }
        };
    }
}
