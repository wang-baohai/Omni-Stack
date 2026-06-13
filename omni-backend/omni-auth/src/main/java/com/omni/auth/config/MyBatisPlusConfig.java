package com.omni.auth.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.omni.auth.security.DataPermissionHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>配置数据权限和分页插件。数据权限拦截器必须在分页拦截器之前注册，
 * 以确保 WHERE 条件在 COUNT 查询执行前已正确追加。</p>
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件配置。
     * <p>注册数据权限拦截器和 MySQL 分页拦截器。</p>
     *
     * @return 配置完成的拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 数据权限拦截器必须在分页拦截器之前
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionHandlerImpl()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
