package com.omni.srm.config;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.config.ServicePersistenceAutoConfiguration;
import com.omni.common.service.persistence.DataScopeTablePolicy;
import com.omni.common.service.persistence.TenantTablePolicy;
import com.omni.srm.security.SrmDataPermissionHandler;
import com.omni.srm.security.SrmTenantTablePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** SRM MyBatis-Plus 拦截器顺序测试。 */
class MybatisPlusConfigTest {

    /** 租户、数据权限、乐观锁和分页拦截器必须按约束顺序注册。 */
    @Test
    void shouldRegisterTenantDataPermissionOptimisticLockerAndPaginationInOrder() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.getTenant().setEnabled(true);
        properties.getDataScope().setEnabled(true);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("tenantTablePolicy", new SrmTenantTablePolicy());
        beans.addBean("dataScopeTablePolicy", new SrmDataPermissionHandler());

        MybatisPlusInterceptor interceptor = new ServicePersistenceAutoConfiguration().mybatisPlusInterceptor(
                properties,
                beans.getBeanProvider(TenantTablePolicy.class),
                beans.getBeanProvider(DataScopeTablePolicy.class));

        assertThat(interceptor.getInterceptors())
                .hasSize(4)
                .satisfiesExactly(
                        item -> assertThat(item).isInstanceOf(TenantLineInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(DataPermissionInterceptor.class),
                        item -> assertThat(item).isInstanceOf(OptimisticLockerInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(PaginationInnerInterceptor.class));
    }
}
