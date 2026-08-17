package com.omni.asset.controller;

import com.omni.asset.dto.AssetRequests;
import com.omni.asset.security.AssetDataScope;
import com.omni.common.core.operlog.OperLog;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 资产台账端点权限、数据范围与操作日志声明测试。 */
class AssetControllerSecurityTest {

    /** 管理列表和“我的资产”必须使用不同的 permission-aware 数据范围。 */
    @Test
    void shouldSeparateManagementAndCurrentUserScopes() throws Exception {
        assertPermission("list", new Class<?>[]{AssetRequests.AssetQuery.class}, "asset:asset:list", false);
        assertPermission("my", new Class<?>[]{AssetRequests.MyAssetQuery.class}, "asset:asset:self", false);
    }

    /** 所有写命令必须同时声明权限、同码数据范围和操作日志。 */
    @Test
    void shouldProtectEveryWriteCommand() throws Exception {
        assertPermission("create", new Class<?>[]{AssetRequests.CreateAssetRequest.class},
                "asset:asset:create", true);
        assertPermission("update", new Class<?>[]{Long.class, AssetRequests.UpdateAssetRequest.class},
                "asset:asset:update", true);
        assertPermission("delete", new Class<?>[]{Long.class, Integer.class},
                "asset:asset:delete", true);
        assertPermission("allocate", new Class<?>[]{Long.class, AssetRequests.AllocateRequest.class},
                "asset:asset:allocate", true);
        assertPermission("accept", new Class<?>[]{Long.class, AssetRequests.VersionCommandRequest.class},
                "asset:asset:accept", true);
        assertPermission("returnAsset", new Class<?>[]{Long.class, AssetRequests.VersionCommandRequest.class},
                "asset:asset:return", true);
        assertPermission("startMaintenance", new Class<?>[]{Long.class, AssetRequests.VersionCommandRequest.class},
                "asset:asset:maintenance", true);
        assertPermission("completeMaintenance",
                new Class<?>[]{Long.class, AssetRequests.VersionCommandRequest.class},
                "asset:asset:maintenance", true);
    }

    private void assertPermission(String methodName, Class<?>[] parameterTypes,
                                  String permission, boolean requiresOperLog) throws Exception {
        Method method = AssetController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('" + permission + "')");
        assertThat(method.getAnnotation(AssetDataScope.class).permissionCode())
                .isEqualTo(permission);
        if (requiresOperLog) {
            assertThat(method.getAnnotation(OperLog.class)).isNotNull();
        }
    }
}
