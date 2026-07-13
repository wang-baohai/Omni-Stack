package com.omni.base.consumer;

import com.omni.base.service.OperLogService;
import com.omni.common.core.operlog.OperLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;

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
            } catch (DuplicateKeyException e) {
                log.debug("操作日志事件已消费，忽略重复消息: {}", message.getEventId());
            } catch (Exception e) {
                log.error("操作日志消费失败，交由消息中间件重试: {}", e.getMessage(), e);
                throw e;
            }
        };
    }
}
