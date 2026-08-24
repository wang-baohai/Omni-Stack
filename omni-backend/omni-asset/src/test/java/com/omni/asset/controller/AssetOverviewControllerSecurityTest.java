package com.omni.asset.controller;

import com.omni.asset.dto.AssetOverviewRequests;
import com.omni.common.service.datascope.ServiceDataScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 资产概览接口权限声明测试。 */
class AssetOverviewControllerSecurityTest {

    /** 摘要与分布必须使用同一概览权限和管理数据范围。 */
    @Test
    void shouldProtectOverviewEndpointsWithMatchingPermission() throws Exception {
        assertPermission(AssetOverviewController.class.getMethod("summary"));
        assertPermission(AssetOverviewController.class.getMethod(
                "distribution", AssetOverviewRequests.DistributionQuery.class));
    }

    private void assertPermission(Method method) {
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('asset:overview:list')");
        assertThat(method.getAnnotation(ServiceDataScope.class).permissionCode())
                .isEqualTo("asset:overview:list");
    }
}
