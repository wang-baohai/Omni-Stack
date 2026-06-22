package com.omni.common.operlog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.operlog.aspect.OperLogAspect;
import com.omni.common.operlog.diff.EntityDiffer;
import com.omni.common.operlog.producer.OperLogProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 操作日志自动配置。
 * <p>在 Servlet Web 环境下自动装配操作日志切面、实体 Diff 工具和 MQ 生产者。</p>
 *
 * @author Omni-Stack Team
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
public class OperLogAutoConfiguration {

    /**
     * 实体 JSON diff 工具。
     */
    @Bean
    public EntityDiffer entityDiffer(ObjectMapper objectMapper) {
        return new EntityDiffer(objectMapper);
    }

    /**
     * 操作日志 MQ 生产者（仅在有 StreamBridge 时注册）。
     */
    @Bean
    @ConditionalOnClass(StreamBridge.class)
    public OperLogProducer operLogProducer(StreamBridge streamBridge) {
        return new OperLogProducer(streamBridge);
    }

    /**
     * 操作日志 AOP 切面。
     */
    @Bean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    public OperLogAspect operLogAspect(OperLogProducer operLogProducer,
                                        EntityDiffer entityDiffer,
                                        ObjectMapper objectMapper,
                                        ApplicationContext applicationContext) {
        return new OperLogAspect(operLogProducer, entityDiffer, objectMapper, applicationContext);
    }
}
