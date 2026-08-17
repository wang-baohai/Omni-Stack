package com.omni.procurement.controller;

import com.omni.procurement.dto.OverviewRequests;
import com.omni.procurement.security.ProcDataScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购概览接口权限声明测试。 */
class OverviewControllerSecurityTest {

    /** 摘要与支出分析必须使用同一概览权限和数据范围。 */
    @Test
    void shouldProtectOverviewEndpointsWithMatchingPermission() throws Exception {
        assertPermission(OverviewController.class.getMethod("summary"));
        assertPermission(OverviewController.class.getMethod(
                "spendAnalysis", OverviewRequests.SpendAnalysisQuery.class));
    }

    private void assertPermission(Method method) {
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('procurement:overview:list')");
        assertThat(method.getAnnotation(ProcDataScope.class).permissionCode())
                .isEqualTo("procurement:overview:list");
    }
}
