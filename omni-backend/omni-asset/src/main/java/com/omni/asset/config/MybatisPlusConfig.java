package com.omni.asset.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.omni.asset.security.AssetDataPermissionHandler;
import com.omni.asset.security.AssetTenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * 资产 MyBatis-Plus 拦截器链配置。
 * <p>顺序固定：TenantLine、DataPermission、OptimisticLocker、Pagination。</p>
 *
 * @author Omni-Stack Team
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 创建资产 SQL 拦截器链。
     *
     * @return 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(AssetTenantContext.requireTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return tableName == null || !tableName.toLowerCase(Locale.ROOT).startsWith("ast_");
            }
        });
        interceptor.addInnerInterceptor(tenant);
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new AssetDataPermissionHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
