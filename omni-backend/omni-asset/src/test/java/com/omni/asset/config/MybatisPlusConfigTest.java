package com.omni.asset.config;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.omni.asset.mapper.AstInboxEventMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** 资产 MyBatis-Plus 安全拦截器测试。 */
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

    /** TenantLine 只能拦截 ast 表，Outbox 必须供全租户后台中继扫描。 */
    @Test
    void shouldApplyTenantLineOnlyToAssetTables() throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        TenantLineInnerInterceptor tenant =
                (TenantLineInnerInterceptor) interceptor.getInterceptors().getFirst();
        Field field = TenantLineInnerInterceptor.class.getDeclaredField("tenantLineHandler");
        field.setAccessible(true);
        TenantLineHandler handler = (TenantLineHandler) field.get(tenant);

        assertThat(handler.ignoreTable("ast_asset")).isFalse();
        assertThat(handler.ignoreTable("ast_inbox_event")).isFalse();
        assertThat(handler.ignoreTable("sys_mq_message")).isTrue();
        assertThat(handler.ignoreTable("wf_process_model")).isTrue();
    }

    /** Inbox 全局唯一键锁查询需跨租户命中，后续再由意图校验严格核对租户。 */
    @Test
    void shouldIgnoreTenantLineOnlyForGlobalInboxLockLookup() throws Exception {
        InterceptorIgnore annotation = AstInboxEventMapper.class
                .getMethod("selectForUpdate", String.class, String.class)
                .getAnnotation(InterceptorIgnore.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.tenantLine()).isEqualTo("true");
        assertThat(AstInboxEventMapper.class
                .getMethod("markProcessed", com.omni.asset.entity.AstInboxEvent.class)
                .getAnnotation(InterceptorIgnore.class)).isNull();
    }
}
