package com.omni.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/** Auth MyBatis-Plus 拦截器顺序测试。 */
class MyBatisPlusConfigTest {

    /** 数据权限、乐观锁和分页拦截器必须按约束顺序注册。 */
    @Test
    void shouldRegisterDataPermissionOptimisticLockerAndPaginationInOrder() {
        MybatisPlusInterceptor interceptor = new MyBatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(3)
                .satisfiesExactly(
                        item -> assertThat(item).isInstanceOf(DataPermissionInterceptor.class),
                        item -> assertThat(item).isInstanceOf(OptimisticLockerInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(PaginationInnerInterceptor.class));
    }
}
