package com.omni.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 自动配置类。
 * <p>提供默认的分页插件配置，仅注册 MySQL 分页拦截器。
 * 服务模块可通过自定义 {@link MybatisPlusInterceptor} Bean
 * 覆盖此默认配置（例如添加数据权限拦截器、租户拦截器等）。</p>
 * <p>{@code @ConditionalOnMissingBean} 保证应用级 {@code @Configuration} 中定义的 Bean
 * 优先于此自动配置，实现可插拔式扩展。</p>
 *
 * @author Omni-Stack
 * @see PaginationInnerInterceptor
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusAutoConfiguration {

    /**
     * 默认 MyBatis-Plus 拦截器，仅注册 MySQL 分页插件。
     * <p>服务模块如需添加数据权限等自定义拦截器，可在应用配置类中定义同名 Bean 覆盖。</p>
     *
     * @return 配置完成的拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
