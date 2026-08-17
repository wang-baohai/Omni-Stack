package com.omni.common.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.Decoder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import com.omni.common.web.TraceIdFilter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * 使用项目统一 Jackson 2 ObjectMapper 的 Feign JSON 解码器。
 * <p>
 * Spring MVC 的消息转换器定制不会自动传播到 OpenFeign 子上下文。该解码器显式复用
 * 公共 ObjectMapper，确保跨服务响应中的日期时间格式与普通 HTTP 响应保持一致。
 * </p>
 */
@Slf4j
public class JacksonFeignDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    /**
     * 创建 Feign JSON 解码器。
     *
     * @param objectMapper 公共 Jackson 2 ObjectMapper
     */
    public JacksonFeignDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Feign 响应体反序列化为目标类型。
     *
     * @param response Feign 响应
     * @param type     目标 Java 类型
     * @return 解码结果；无响应体时返回 null
     * @throws IOException 读取或反序列化失败
     */
    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.status() == 204 || response.body() == null) {
            return null;
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        try (InputStream inputStream = response.body().asInputStream()) {
            return objectMapper.readValue(inputStream, javaType);
        } catch (IOException | RuntimeException exception) {
            log.error("Feign 响应解码失败: traceId={}, status={}, method={}, url={}, targetType={}, cause={}",
                    MDC.get(TraceIdFilter.MDC_KEY), response.status(),
                    response.request().httpMethod(), response.request().url(),
                    type.getTypeName(), exception.getClass().getName(), exception);
            throw exception;
        }
    }
}
