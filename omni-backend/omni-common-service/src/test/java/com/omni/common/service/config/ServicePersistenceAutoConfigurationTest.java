package com.omni.common.service.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.omni.common.service.persistence.DataScopeTablePolicy;
import com.omni.common.service.persistence.TenantTablePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicePersistenceAutoConfigurationTest {

    private final ServicePersistenceAutoConfiguration configuration = new ServicePersistenceAutoConfiguration();

    @Test
    void shouldBuildFixedInterceptorOrder() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.getTenant().setEnabled(true);
        properties.getDataScope().setEnabled(true);
        TenantTablePolicy tenantPolicy = tableName -> tableName.startsWith("crm_");
        DataScopeTablePolicy dataScopePolicy = (table, where, mappedStatementId) -> null;
        StaticListableBeanFactory factory = new StaticListableBeanFactory(Map.of(
                "tenantTablePolicy", tenantPolicy,
                "dataScopeTablePolicy", dataScopePolicy));

        MybatisPlusInterceptor interceptor = configuration.mybatisPlusInterceptor(
                properties,
                factory.getBeanProvider(TenantTablePolicy.class),
                factory.getBeanProvider(DataScopeTablePolicy.class));

        assertThat(interceptor.getInterceptors())
                .satisfiesExactly(
                        item -> assertThat(item).isInstanceOf(TenantLineInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(DataPermissionInterceptor.class),
                        item -> assertThat(item).isInstanceOf(OptimisticLockerInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(PaginationInnerInterceptor.class));
    }

    @Test
    void shouldFailWhenEnabledPolicyIsMissing() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.getTenant().setEnabled(true);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();

        assertThatThrownBy(() -> configuration.mybatisPlusInterceptor(
                properties,
                factory.getBeanProvider(TenantTablePolicy.class),
                factory.getBeanProvider(DataScopeTablePolicy.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantTablePolicy");
    }
}
