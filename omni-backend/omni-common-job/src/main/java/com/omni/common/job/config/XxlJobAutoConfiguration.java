package com.omni.common.job.config;

import com.omni.common.job.SystemJobRegistry;
import com.omni.common.job.JobMetrics;
import com.omni.common.job.XxlJobAdminClient;
import com.omni.common.job.XxlJobProperties;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

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
     * <p>在创建执行器前，自动通过 Admin API 确保执行器组已注册到调度中心，
     * 避免执行器启动时因组不存在而注册失败。</p>
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

        // 确保执行器组在调度中心已注册（带重试），在执行器 Bean 创建前完成
        ensureExecutorGroupRegistered(properties, resolvedAppName);

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
     * 确保执行器组已注册到 XXL-JOB 调度中心。
     * <p>带重试逻辑，防止 XXL-JOB Admin 尚未就绪时失败。
     * 最多重试 5 次，每次间隔 10 秒。</p>
     *
     * @param properties     XXL-JOB 配置属性
     * @param resolvedAppName 解析后的 AppName
     */
    private void ensureExecutorGroupRegistered(XxlJobProperties properties, String resolvedAppName) {
        String adminAddr = properties.getAdmin().getAddresses();
        String adminUser = properties.getAdmin().getUsername();
        String adminPass = properties.getAdmin().getPassword();

        XxlJobAdminClient client = new XxlJobAdminClient(adminAddr, adminUser, adminPass);
        int maxRetries = 5;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                int groupId = client.ensureExecutorGroup(resolvedAppName, resolvedAppName);
                if (groupId >= 0) {
                    JobMetrics.recordRegistration("success");
                    log.info("XXL-JOB 执行器组就绪: appname={}, groupId={}", resolvedAppName, groupId);
                    return;
                }
                log.warn("执行器组创建后仍无法查询到: appname={}, 重试 {}/{}", resolvedAppName, i, maxRetries);
            } catch (Exception e) {
                log.warn("XXL-JOB 执行器组注册失败 ({}): {}, 重试 {}/{}",
                        e.getClass().getSimpleName(), e.getMessage(), i, maxRetries);
            }
            if (i < maxRetries) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("XXL-JOB 执行器组注册等待被中断");
                    return;
                }
            }
        }
        JobMetrics.recordRegistration("failure");
        log.error("XXL-JOB 执行器组注册失败，已耗尽重试次数: appname={}", resolvedAppName);
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

    /**
     * 应用启动完成后，自动将未注册的系统任务注册到 XXL-JOB 调度中心。
     * <p>
     * 遍历 {@link SystemJobRegistry} 中收集的所有 Handler 元数据，
     * 查询 XXL-JOB 中已存在的任务，对未注册的 Handler 使用默认 Cron 自动创建。
     * 已有任务不会被覆盖（保留运维人员手动修改的 Cron 和参数）。
     * </p>
     *
     * @param systemJobRegistry 系统任务元数据注册中心
     * @param properties        XXL-JOB 配置属性
     * @return 启动后执行的自动注册逻辑
     */
    @Bean
    @ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner systemJobAutoRegistrar(
            SystemJobRegistry systemJobRegistry,
            XxlJobProperties properties) {
        return args -> {
            String appname = properties.getExecutor().getAppname();
            if (appname == null || appname.isBlank()) {
                log.debug("XXL-JOB appname 未配置，跳过系统任务自动注册");
                return;
            }

            Map<String, SystemJobRegistry.SystemJobInfo> allJobs = systemJobRegistry.getAll();
            if (allJobs.isEmpty()) {
                log.debug("无系统任务元数据，跳过自动注册");
                return;
            }

            XxlJobAdminClient client = new XxlJobAdminClient(
                    properties.getAdmin().getAddresses(),
                    properties.getAdmin().getUsername(),
                    properties.getAdmin().getPassword());

            try {
                int groupId = client.getJobGroupId(appname);
                if (groupId < 0) {
                    log.warn("未找到执行器组: appname={}, 跳过自动注册", appname);
                    return;
                }

                // 查询 XXL-JOB 中已注册的任务
                List<Map<String, Object>> existingJobs = client.pageList(groupId, null);
                java.util.Set<String> existingHandlers = new java.util.HashSet<>();
                for (Map<String, Object> job : existingJobs) {
                    Object handler = job.get("executorHandler");
                    if (handler != null) {
                        existingHandlers.add(handler.toString());
                    }
                }

                // 自动注册未注册的任务
                int registered = 0;
                for (SystemJobRegistry.SystemJobInfo info : allJobs.values()) {
                    if (existingHandlers.contains(info.getHandlerName())) {
                        log.debug("系统任务已注册，跳过: {}", info.getHandlerName());
                        continue;
                    }
                    if (info.getDefaultCron() == null || info.getDefaultCron().isBlank()) {
                        log.warn("系统任务缺少默认 Cron，跳过自动注册: {}", info.getHandlerName());
                        continue;
                    }

                    String defaultParams = buildDefaultParams(info);
                    String xxlJobId = client.addJob(
                            groupId,
                            info.getName(),
                            info.getDefaultCron(),
                            info.getRouteStrategy(),
                            info.getHandlerName(),
                            defaultParams);
                    log.info("系统任务自动注册成功: {} -> XXL-JOB ID={}, cron={}",
                            info.getHandlerName(), xxlJobId, info.getDefaultCron());
                    registered++;
                }

                if (registered > 0) {
                    log.info("系统任务自动注册完成，本次注册 {} 个", registered);
                } else {
                    log.info("系统任务已全部注册，无需操作");
                }
                JobMetrics.recordRegistration("success");
            } catch (Exception e) {
                JobMetrics.recordRegistration("failure");
                log.warn("系统任务自动注册失败: {}", e.getMessage());
            }
        };
    }

    /**
     * 根据元数据中的参数定义构建默认执行参数 JSON。
     *
     * @param info 系统任务元数据
     * @return JSON 格式的默认参数，无参数时返回空字符串
     */
    private String buildDefaultParams(SystemJobRegistry.SystemJobInfo info) {
        List<SystemJobRegistry.ParamDefInfo> paramDefs = info.getParamDefs();
        if (paramDefs == null || paramDefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < paramDefs.size(); i++) {
            SystemJobRegistry.ParamDefInfo p = paramDefs.get(i);
            if (i > 0) sb.append(",");
            sb.append("\"").append(p.getName()).append("\":");
            if ("number".equals(p.getType())) {
                sb.append(p.getDefaultValue());
            } else if ("boolean".equals(p.getType())) {
                sb.append(p.getDefaultValue());
            } else {
                sb.append("\"").append(p.getDefaultValue() != null ? p.getDefaultValue() : "").append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
