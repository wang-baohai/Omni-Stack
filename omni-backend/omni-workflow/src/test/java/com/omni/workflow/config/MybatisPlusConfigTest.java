package com.omni.workflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

/** Workflow MyBatis-Plus 拦截器顺序测试。 */
class MybatisPlusConfigTest {

    /** 租户、乐观锁和分页拦截器必须按约束顺序注册。 */
    @Test
    void shouldRegisterTenantOptimisticLockerAndPaginationInOrder() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(3)
                .satisfiesExactly(
                        item -> assertThat(item).isInstanceOf(TenantLineInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(OptimisticLockerInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(PaginationInnerInterceptor.class));
    }
}
