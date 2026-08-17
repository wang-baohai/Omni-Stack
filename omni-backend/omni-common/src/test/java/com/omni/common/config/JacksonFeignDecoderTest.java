package com.omni.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.R;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 公共 JSON 解码器测试。
 */
class JacksonFeignDecoderTest {

    /** 验证统一格式的日期时间可以在泛型响应中反序列化。 */
    @Test
    void shouldDecodeFormattedLocalDateTimeInGenericResponse() throws Exception {
        JacksonConfig config = new JacksonConfig(
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(com.fasterxml.jackson.databind.Module.class),
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(tools.jackson.databind.JacksonModule.class));
        ObjectMapper mapper = config.objectMapper();
        JacksonFeignDecoder decoder = new JacksonFeignDecoder(mapper);
        String json = "{\"code\":200,\"message\":\"success\",\"data\":{"
                + "\"id\":9,\"createTime\":\"2026-08-17 09:30:45\"}}";
        Request request = Request.create(Request.HttpMethod.GET, "http://localhost/test",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .request(request)
                .status(200)
                .reason("OK")
                .headers(Map.of())
                .body(json, StandardCharsets.UTF_8)
                .build();

        @SuppressWarnings("unchecked")
        R<DatePayload> result = (R<DatePayload>) decoder.decode(response, rOf(DatePayload.class));

        assertThat(result.getData().createTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 17, 9, 30, 45));
    }

    private static ParameterizedType rOf(Type dataType) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{dataType};
            }

            @Override
            public Type getRawType() {
                return R.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    private record DatePayload(Long id, LocalDateTime createTime) {
    }
}
