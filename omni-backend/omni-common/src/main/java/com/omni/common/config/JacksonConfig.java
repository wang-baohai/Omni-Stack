package com.omni.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Jackson 序列化配置，处理 Java 8 日期/时间类型的 JSON 转换。
 * <p>
 * 同时配置 Jackson 2（ObjectMapper Bean）和 Jackson 3（HttpMessageConverter 替换），
 * 确保无论哪个版本的 Jackson 处理 HTTP 响应，日期格式统一为 yyyy-MM-dd HH:mm:ss。
 * </p>
 */
@Configuration
@SuppressWarnings({"deprecation", "removal"})
public class JacksonConfig implements WebMvcConfigurer {

    /** 日期时间格式：yyyy-MM-dd HH:mm:ss */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 日期格式：yyyy-MM-dd */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private final ObjectProvider<com.fasterxml.jackson.databind.Module> jackson2Modules;
    private final ObjectProvider<tools.jackson.databind.JacksonModule> jackson3Modules;

    /**
     * 创建 Jackson 配置并保留业务模块扩展点。
     *
     * @param jackson2Modules 应用上下文中的 Jackson 2 模块
     * @param jackson3Modules 应用上下文中的 Jackson 3 模块
     */
    public JacksonConfig(ObjectProvider<com.fasterxml.jackson.databind.Module> jackson2Modules,
                         ObjectProvider<tools.jackson.databind.JacksonModule> jackson3Modules) {
        this.jackson2Modules = jackson2Modules;
        this.jackson3Modules = jackson3Modules;
    }

    /**
     * 提供全局 ObjectMapper Bean（Jackson 2），供其他组件（如 XSS 防护）注入使用。
     *
     * @return 配置完成的 Jackson 2 ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DATE_FORMATTER));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DATE_FORMATTER));

        ObjectMapper mapper = new ObjectMapper();
        jackson2Modules.orderedStream().forEach(mapper::registerModule);
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * 创建自定义日期格式的 Jackson 3 JsonMapper。
     * <p>通过 SimpleModule 注册 LocalDateTime/LocalDate 的自定义序列化器/反序列化器。</p>
     *
     * @return 配置完成的 JsonMapper.Builder
     */
    private JsonMapper.Builder customJsonMapperBuilder() {
        tools.jackson.databind.module.SimpleModule module =
                new tools.jackson.databind.module.SimpleModule("CustomDateTimeModule");
        module.addSerializer(LocalDateTime.class,
                new tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        module.addDeserializer(LocalDateTime.class,
                new tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        module.addSerializer(LocalDate.class,
                new tools.jackson.databind.ext.javatime.ser.LocalDateSerializer(DATE_FORMATTER));
        module.addDeserializer(LocalDate.class,
                new tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer(DATE_FORMATTER));

        JsonMapper.Builder builder = JsonMapper.builder();
        jackson3Modules.orderedStream().forEach(builder::addModule);
        return builder.addModule(module);
    }

    /**
     * 扩展 HTTP 消息转换器，同时兼容 Jackson 2 和 Jackson 3。
     * <p>
     * Spring Boot 4 默认使用 Jackson 3 的 {@link JacksonJsonHttpMessageConverter}，
     * 此方法将其替换为使用自定义日期格式的 JsonMapper。同时兼容 Jackson 2 的
     * {@link MappingJackson2HttpMessageConverter}。
     * </p>
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper jackson2Mapper = objectMapper();
        JsonMapper.Builder jackson3Builder = customJsonMapperBuilder();
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof JacksonJsonHttpMessageConverter) {
                converters.set(i, new JacksonJsonHttpMessageConverter(jackson3Builder.build()));
            } else if (converter instanceof MappingJackson2HttpMessageConverter) {
                converters.set(i, new MappingJackson2HttpMessageConverter(jackson2Mapper));
            }
        }
    }
}
