package com.omni.procurement.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购 MyBatis-Plus 安全拦截器顺序测试。 */
class MybatisPlusConfigTest {

    /** 租户、数据权限、乐观锁和分页拦截器必须按安全约束顺序注册。 */
    @Test
    void shouldRegisterTenantDataPermissionAndPaginationInOrder() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(4)
                .satisfiesExactly(
                        item -> assertThat(item).isInstanceOf(TenantLineInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(DataPermissionInterceptor.class),
                        item -> assertThat(item).isInstanceOf(OptimisticLockerInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(PaginationInnerInterceptor.class));
    }
}
