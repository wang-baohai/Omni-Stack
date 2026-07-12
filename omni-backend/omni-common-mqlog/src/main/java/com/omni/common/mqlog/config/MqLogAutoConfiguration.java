package com.omni.common.mqlog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.mqlog.controller.MqMessageInternalController;
import com.omni.common.mqlog.filter.InternalApiAuthFilter;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import com.omni.common.mqlog.relay.MqMessageRelayJob;
import com.omni.common.mqlog.relay.MqMessageRelayService;
import com.omni.common.mqlog.sender.MessageSender;
import com.omni.common.mqlog.sender.RocketMqMessageSender;
import com.omni.common.mqlog.template.ReliableMessageTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * MQ 消息记录自动装配。
 * <p>
 * 当 classpath 中存在 MyBatis-Plus {@code BaseMapper} 时自动装配以下 Bean：
 * </p>
 * <ul>
 *   <li>{@link ReliableMessageTemplate} — 可靠消息发送模板（Transactional Outbox）</li>
 *   <li>{@link RocketMqMessageSender} — RocketMQ 发送策略（依赖 StreamBridge）</li>
 *   <li>{@link MqMessageRelayService} — 消息投递中继服务</li>
 *   <li>{@link MqMessageRelayJob} — XXL-JOB 系统任务（依赖 common-job）</li>
 *   <li>{@link MqMessageInternalController} — Feign 内部查询 API</li>
 * </ul>
 *
 * @author Omni-Stack Team
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@MapperScan("com.omni.common.mqlog.mapper")
public class MqLogAutoConfiguration {

    /**
     * 内部 API 认证过滤器。
     * <p>校验 {@code X-Internal-Token} 请求头，保护 {@code /api/internal/} 路径。</p>
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<InternalApiAuthFilter> internalApiAuthFilter(
            @Value("${omni.internal.api-token:omni-internal-default-token}") String internalToken) {
        FilterRegistrationBean<InternalApiAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalApiAuthFilter(internalToken));
        registration.addUrlPatterns("/api/internal/*");
        registration.setOrder(-200);
        registration.setName("internalApiAuthFilter");
        return registration;
    }

    /**
     * 可靠消息发送模板。
     */
    @Bean
    @ConditionalOnMissingBean
    public ReliableMessageTemplate reliableMessageTemplate(SysMqMessageMapper mapper,
                                                            ObjectMapper objectMapper) {
        return new ReliableMessageTemplate(mapper, objectMapper);
    }

    /**
     * RocketMQ 发送策略（仅当 classpath 有 StreamBridge 时生效）。
     */
    @Bean
    @ConditionalOnMissingBean(MessageSender.class)
    @ConditionalOnClass(StreamBridge.class)
    public RocketMqMessageSender rocketMqMessageSender(StreamBridge streamBridge) {
        return new RocketMqMessageSender(streamBridge);
    }

    /**
     * 消息投递中继服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public MqMessageRelayService mqMessageRelayService(SysMqMessageMapper mapper,
                                                        List<MessageSender> senders) {
        return new MqMessageRelayService(mapper, senders);
    }

    /**
     * XXL-JOB 消息中继系统任务（仅当 common-job 在 classpath 中时生效）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.xxl.job.core.handler.annotation.XxlJob")
    public MqMessageRelayJob mqMessageRelayJob(MqMessageRelayService relayService) {
        return new MqMessageRelayJob(relayService);
    }

    /**
     * Feign 内部查询 API（仅 Servlet Web 环境）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public MqMessageInternalController mqMessageInternalController(SysMqMessageMapper mapper) {
        return new MqMessageInternalController(mapper);
    }
}
