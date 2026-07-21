package com.omni.srm.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.omni.srm.security.SrmDataPermissionHandler;
import com.omni.srm.security.SrmTenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SRM MyBatis-Plus 拦截器链配置。
 * <p>顺序固定：TenantLine -> DataPermission -> Pagination。</p>
 *
 * @author Omni-Stack Team
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 创建 SRM SQL 拦截器链。
     *
     * @return 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(SrmTenantContext.requireTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return tableName == null || !tableName.toLowerCase().startsWith("srm_");
            }
        });
        interceptor.addInnerInterceptor(tenant);
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new SrmDataPermissionHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
