package com.omni.asset.controller;

import com.omni.common.service.datascope.ServiceDataScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 调拨与处置写接口权限和数据范围声明测试。 */
class AssetOperationControllerSecurityTest {

    /** 所有调拨写接口必须同时声明功能权限与数据范围。 */
    @Test
    void shouldProtectEveryTransferWriteEndpoint() {
        assertProtectedWrites(AssetTransferController.class, "asset:transfer:");
    }

    /** 所有处置写接口必须同时声明功能权限与数据范围。 */
    @Test
    void shouldProtectEveryDisposalWriteEndpoint() {
        assertProtectedWrites(AssetDisposalController.class, "asset:disposal:");
    }

    private void assertProtectedWrites(Class<?> controllerType, String permissionPrefix) {
        Method[] writes = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .toArray(Method[]::new);

        assertThat(writes).isNotEmpty();
        assertThat(writes).allSatisfy(method -> {
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            ServiceDataScope dataScope = method.getAnnotation(ServiceDataScope.class);
            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).contains(permissionPrefix);
            assertThat(dataScope).isNotNull();
            assertThat(dataScope.permissionCode()).startsWith(permissionPrefix);
        });
    }
}
