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
 * <p>在 Servlet Web 环境下自动装配操作日志切面、实体 Diff 工具和 MQ 生产者。
 * 生效条件：classpath 中存在 AspectJ {@code ProceedingJoinPoint} 且为 Servlet Web 环境。</p>
 * <p>注册三个 Bean：{@link EntityDiffer}（JSON diff 工具）、{@link OperLogProducer}（MQ 生产者）、
 * {@link OperLogAspect}（AOP 切面，依赖 MyBatis-Plus BaseMapper）。</p>
 *
 * @author Omni-Stack Team
 * @see OperLogAspect
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
public class OperLogAutoConfiguration {

    /**
     * 实体 JSON diff 工具。
     * <p>用于比较新旧实体的 JSON 差异，仅输出变更字段。</p>
     *
     * @param objectMapper Jackson ObjectMapper
     * @return EntityDiffer 实例
     */
    @Bean
    public EntityDiffer entityDiffer(ObjectMapper objectMapper) {
        return new EntityDiffer(objectMapper);
    }

    /**
     * 操作日志 MQ 生产者。
     * <p>仅当 classpath 中存在 {@link StreamBridge} 时注册，
     * 通过 RocketMQ 将日志消息发送至消费端。</p>
     *
     * @param streamBridge Spring Cloud Stream 桥接器
     * @return OperLogProducer 实例
     */
    @Bean
    @ConditionalOnClass(StreamBridge.class)
    public OperLogProducer operLogProducer(StreamBridge streamBridge) {
        return new OperLogProducer(streamBridge);
    }

    /**
     * 操作日志 AOP 切面。
     * <p>仅当 classpath 中存在 MyBatis-Plus {@code BaseMapper} 时注册，
     * 因为变更快照采集依赖 Mapper 查询实体。</p>
     *
     * @param operLogProducer MQ 生产者
     * @param entityDiffer    JSON diff 工具
     * @param objectMapper    Jackson ObjectMapper
     * @param applicationContext Spring 应用上下文
     * @return OperLogAspect 实例
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
