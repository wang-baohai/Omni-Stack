package com.omni.workflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 配置租户拦截器和分页插件。仅对业务扩展表（{@code wf_*}）应用租户过滤，
 * Flowable 的 {@code ACT_*} 表由引擎自身的 {@code TENANT_ID_} 机制处理，
 * 需排除在 {@link TenantLineInnerInterceptor} 之外。</p>
 *
 * <h3>插件注册顺序</h3>
 * <ol>
 *   <li>{@link TenantLineInnerInterceptor} — 业务表（wf_*）的租户隔离</li>
 *   <li>{@link PaginationInnerInterceptor} — MySQL 分页（自动改写 LIMIT/OFFSET）</li>
 * </ol>
 *
 * @author Omni-Stack Team
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 插件配置。
     * <p>
     * 租户拦截器排除 {@code ACT_} 前缀的 Flowable 内部表，
     * 仅对 {@code wf_*} 业务扩展表注入 {@code tenant_id} 条件。</p>
     *
     * @return 配置完成的 {@link MybatisPlusInterceptor} 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 租户拦截器：排除 Flowable ACT_* 表
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 从 SecurityContext 或请求头中获取租户 ID，默认返回 1
                String tenantId = com.omni.common.workflow.tenant.TenantInfoHolder.getTenantId();
                return new LongValue(tenantId != null ? Long.parseLong(tenantId) : 1L);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 仅拦截 wf_* 业务表；Flowable 表与 sys_mq_message 等基础设施表自行处理租户边界。
                return !tableName.toLowerCase(Locale.ROOT).startsWith("wf_");
            }
        });

        interceptor.addInnerInterceptor(tenantInterceptor);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
