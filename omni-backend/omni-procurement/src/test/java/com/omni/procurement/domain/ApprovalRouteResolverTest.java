package com.omni.procurement.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.security.ProcTenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 审批路由解析器租户边界测试。 */
@ExtendWith(MockitoExtension.class)
class ApprovalRouteResolverTest {

    @Mock
    private ProcApprovalRouteMapper routeMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "approval-route-resolver-test");
        assistant.setCurrentNamespace("com.omni.procurement.mapper.ProcApprovalRouteMapper");
        TableInfoHelper.initTableInfo(assistant, ProcApprovalRoute.class);
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ProcTenantContext.clear();
    }

    /** 解析器必须使用当前租户候选并返回精确路由。 */
    @Test
    void shouldResolveExactRouteWithinCurrentTenant() {
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(7L, 9L, "buyer"));
        ProcApprovalRoute wildcard = route(1L, "*");
        ProcApprovalRoute exact = route(2L, "IT_DEVICE");
        when(routeMapper.selectList(any())).thenReturn(List.of(wildcard, exact));

        ProcApprovalRoute selected = new ApprovalRouteResolver(routeMapper)
                .resolve("IT_DEVICE", new BigDecimal("5000"));

        assertThat(selected.getId()).isEqualTo(2L);
        assertThat(ProcTenantContext.requireTenantId()).isEqualTo(9L);
    }

    private ProcApprovalRoute route(Long id, String categoryCode) {
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setId(id);
        route.setCategoryCode(categoryCode);
        route.setMinAmount(BigDecimal.ZERO);
        route.setMaxAmount(null);
        route.setPriority(0);
        route.setStatus(ApprovalRoutePolicy.ACTIVE);
        return route;
    }
}
