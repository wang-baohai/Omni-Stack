package com.omni.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务配置，为审计日志事件监听器提供线程池。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 审计日志异步写入线程池。
     *
     * <p>核心线程数 2，最大线程数 5，队列容量 1000，
     * 线程名前缀 {@code audit-}，拒绝策略为 CallerRunsPolicy。</p>
     *
     * @return 线程池执行器
     */
    @Bean("auditExecutor")
    public ThreadPoolExecutor auditExecutor() {
        return new ThreadPoolExecutor(
                2,
                5,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("audit-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
