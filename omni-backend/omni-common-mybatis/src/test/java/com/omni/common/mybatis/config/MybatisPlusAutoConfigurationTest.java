package com.omni.common.mybatis.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/** 通用 MyBatis-Plus 默认配置测试。 */
class MybatisPlusAutoConfigurationTest {

    /** 默认配置必须同时提供乐观锁和分页能力。 */
    @Test
    void shouldRegisterOptimisticLockerBeforePagination() {
        MybatisPlusInterceptor interceptor = new MybatisPlusAutoConfiguration().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(2)
                .satisfiesExactly(
                        item -> assertThat(item).isInstanceOf(OptimisticLockerInnerInterceptor.class),
                        item -> assertThat(item).isInstanceOf(PaginationInnerInterceptor.class));
    }
}
