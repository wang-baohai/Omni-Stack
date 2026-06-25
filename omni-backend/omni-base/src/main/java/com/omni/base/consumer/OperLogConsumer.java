package com.omni.base.consumer;

import com.omni.base.service.OperLogService;
import com.omni.common.core.operlog.OperLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.function.Consumer;

/**
 * 操作日志 MQ 消费者。
 * <p>通过 Spring Cloud Stream 消费 RocketMQ 中的操作日志消息并持久化。</p>
 * <p>使用 {@code @Lazy} 延迟注入 {@link OperLogService}，避免 Spring Cloud Stream
 * 函数绑定阶段 Bean 初始化顺序问题。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Configuration
public class OperLogConsumer {

    private final OperLogService operLogService;

    public OperLogConsumer(@Lazy OperLogService operLogService) {
        this.operLogService = operLogService;
    }

    /**
     * 操作日志消费者 Bean。
     * <p>Bean 名称 {@code operlogConsumer} 对应 binding {@code operlogConsumer-in-0}。</p>
     */
    @Bean
    public Consumer<OperLogMessage> operlogConsumer() {
        return message -> {
            try {
                operLogService.save(message);
            } catch (Exception e) {
                log.warn("操作日志消费失败: {}", e.getMessage(), e);
            }
        };
    }
}
