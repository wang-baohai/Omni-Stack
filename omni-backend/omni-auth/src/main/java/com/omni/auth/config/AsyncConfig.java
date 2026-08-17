package com.omni.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务配置，为审计日志事件监听器提供线程池。
 * <p>
 * 启用 Spring 异步支持（{@code @EnableAsync}），并注册专用的
 * {@code auditExecutor} 线程池 Bean，供操作日志异步写入使用。
 * </p>
 *
 * <h3>线程池参数说明</h3>
 * <ul>
 *   <li>核心线程数: 2（常驻线程）</li>
 *   <li>最大线程数: 5（突发流量扩容）</li>
 *   <li>队列容量: 1000（缓冲任务）</li>
 *   <li>拒绝策略: CallerRunsPolicy（队列满时调用线程执行，实现背压）</li>
 *   <li>守护线程: {@code daemon=true}，不阻止 JVM 退出</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see com.omni.common.operlog.OperLogProducer
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 审计日志异步写入线程池。
     *
     * <p>核心线程数 2，最大线程数 5，队列容量 1000，
     * 线程名前缀 {@code audit-}，拒绝策略为 {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}。
     * 空闲线程存活时间 60 秒，允许核心线程超时回收。</p>
     *
     * @return 配置完成的线程池执行器，Bean 名称为 {@code auditExecutor}
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
                    t.setName("audit-" + t.threadId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
