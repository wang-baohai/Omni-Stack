package com.omni.auth.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.omni.auth.security.DataPermissionHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 配置数据权限拦截器和分页插件。
 * 数据权限拦截器必须在分页拦截器之前注册，
 * 以确保 WHERE 条件在 COUNT 查询执行前已正确追加。</p>
 *
 * <h3>插件注册顺序</h3>
 * <ol>
 *   <li>{@link DataPermissionInterceptor} — 基于 {@link DataPermissionHandlerImpl} 的租户数据隔离</li>
 *   <li>{@link OptimisticLockerInnerInterceptor} — 支持 {@code @Version} 并发更新</li>
 *   <li>{@link PaginationInnerInterceptor} — MySQL 分页（自动改写 LIMIT/OFFSET）</li>
 * </ol>
 *
 * @see DataPermissionHandlerImpl
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件配置。
     * <p>
     * 注册以下拦截器（顺序敏感）：</p>
     * <ol>
     *   <li>{@link DataPermissionInterceptor} — 在 SQL 执行前追加租户过滤条件</li>
     *   <li>{@link OptimisticLockerInnerInterceptor} — 填充原始版本参数并校验并发更新</li>
     *   <li>{@link PaginationInnerInterceptor} — 自动改写分页 SQL（MySQL 方言）</li>
     * </ol>
     *
     * @return 配置完成的 {@link MybatisPlusInterceptor} 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 数据权限拦截器必须在分页拦截器之前
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionHandlerImpl()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
