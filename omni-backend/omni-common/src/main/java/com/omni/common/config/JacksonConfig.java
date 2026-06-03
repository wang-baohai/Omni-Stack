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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置，处理 Java 8 日期/时间类型的 JSON 转换。
 * <p>
 * 注册 {@link JavaTimeModule} 以支持 {@link LocalDateTime} 和 {@link LocalDate}
 * 的自定义格式序列化与反序列化，禁用默认的时间戳输出模式。
 * </p>
 */
@Configuration
public class JacksonConfig {

    /** 日期时间格式：yyyy-MM-dd HH:mm:ss */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 日期格式：yyyy-MM-dd */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 创建并配置全局 ObjectMapper。
     * <p>
     * 注册 JavaTimeModule，配置 LocalDateTime 和 LocalDate 的序列化器/反序列化器，
     * 并禁用日期的时间戳输出格式。
     * </p>
     *
     * @return 配置完成的全局 ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        // 创建 Java 时间模块，注册自定义的序列化器和反序列化器
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // LocalDateTime 序列化格式
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
        // LocalDateTime 反序列化格式
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
        // LocalDate 序列化格式
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        // LocalDate 反序列化格式
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));

        // 注册模块并禁用时间戳输出
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
