package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批规则 Mapper 行锁 SQL 契约测试。
 */
class ProcApprovalRouteMapperContractTest {

    /**
     * 租户配置行锁必须保留 MySQL 的 LIMIT ... FOR UPDATE 顺序，并跳过会重排语句的租户拦截器。
     *
     * @throws NoSuchMethodException Mapper 方法不存在
     */
    @Test
    void shouldKeepTenantLockSqlCompatibleWithMysql() throws NoSuchMethodException {
        Method method = ProcApprovalRouteMapper.class.getMethod("lockTenantConfig", Long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);

        assertThat(sql).contains("tenant_id = #{tenantId}")
                .contains("LIMIT 1 FOR UPDATE");
        assertThat(ignore).isNotNull();
        assertThat(ignore.tenantLine()).isEqualTo("true");
    }
}
