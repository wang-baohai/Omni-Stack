package com.omni.common.job.config;

import com.omni.common.job.SystemJobRegistry;
import com.omni.common.job.XxlJobProperties;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * XXL-JOB 执行器自动配置。
 * <p>
 * 当 classpath 中存在 {@code XxlJobSpringExecutor} 且配置项
 * {@code xxl.job.executor.enabled=true}（默认）时，自动创建执行器 Bean。
 * 服务模块可通过自定义同名 Bean 覆盖此默认配置。</p>
 *
 * <p>本类注册以下 Bean：</p>
 * <ul>
 *   <li>{@link XxlJobSpringExecutor} — XXL-JOB 执行器，负责与调度中心通信并执行任务</li>
 *   <li>{@link SystemJobRegistry} — 系统任务元数据注册中心，启动时扫描双注解方法</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see XxlJobProperties
 * @see SystemJobRegistry
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobAutoConfiguration {

    /**
     * 创建 XXL-JOB 执行器 Bean。
     * <p>
     * 读取 {@link XxlJobProperties} 配置并初始化执行器。
     * 当 {@code xxl.job.executor.appname} 为空时，回退使用
     * {@code spring.application.name} 作为 AppName。</p>
     *
     * @param properties XXL-JOB 配置属性（{@code xxl.job.*}）
     * @param appName    Spring 应用名称（兑底值）
     * @return 配置完成的 {@link XxlJobSpringExecutor} 实例
     * @see XxlJobProperties
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public XxlJobSpringExecutor xxlJobSpringExecutor(
            XxlJobProperties properties,
            @Value("${spring.application.name}") String appName) {

        String resolvedAppName = properties.getExecutor().getAppname().isEmpty()
                ? appName : properties.getExecutor().getAppname();

        log.info("XXL-JOB 执行器初始化: admin={}, appname={}, ip={}, address={}, port={}",
                properties.getAdmin().getAddresses(),
                resolvedAppName,
                properties.getExecutor().getIp(),
                properties.getExecutor().getAddress(),
                properties.getExecutor().getPort());

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdmin().getAddresses());
        executor.setAppname(resolvedAppName);
        executor.setAddress(properties.getExecutor().getAddress());
        executor.setIp(properties.getExecutor().getIp());
        executor.setPort(properties.getExecutor().getPort());
        executor.setAccessToken(properties.getAccessToken());
        executor.setLogPath(properties.getExecutor().getLogPath());
        executor.setLogRetentionDays(properties.getExecutor().getLogRetentionDays());

        return executor;
    }

    /**
     * 系统任务元数据注册中心 Bean。
     * <p>
     * 启动时自动扫描所有带 {@code @XxlJob} + {@code @SystemJobMeta} 双注解的方法，
     * 收集任务元数据供管理界面展示和任务管理 API 使用。</p>
     *
     * @return 新建的 {@link SystemJobRegistry} 实例
     * @see SystemJobRegistry
     * @see SystemJobMeta
     */
    @Bean
    @ConditionalOnMissingBean
    public SystemJobRegistry systemJobRegistry() {
        return new SystemJobRegistry();
    }
}
