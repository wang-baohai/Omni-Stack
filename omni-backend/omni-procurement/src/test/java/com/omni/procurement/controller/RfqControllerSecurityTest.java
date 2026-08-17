package com.omni.procurement.controller;

import com.omni.procurement.dto.RfqRequests;
import com.omni.procurement.security.ProcDataScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 询价外部接口权限声明测试。 */
class RfqControllerSecurityTest {

    /** 供应商选项允许询价创建者和只读用户访问，且不绑定单一数据范围权限。 */
    @Test
    void shouldProtectSupplierOptionsWithoutLeakingThroughSingleScopePermission() throws Exception {
        Method method = RfqController.class.getMethod(
                "supplierOptions", RfqRequests.SupplierOptionQuery.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyAuthority('procurement:rfq:create', 'procurement:rfq:list')");
        assertThat(method.getAnnotation(ProcDataScope.class)).isNull();
    }

    /** 所有询价写命令必须声明与数据范围一致的独立权限码。 */
    @Test
    void shouldProtectEveryWriteCommandWithMatchingPermission() throws Exception {
        assertPermission("create", new Class<?>[]{RfqRequests.CreateRequest.class},
                "procurement:rfq:create");
        assertPermission("update", new Class<?>[]{Long.class, RfqRequests.UpdateRequest.class},
                "procurement:rfq:update");
        assertPermission("delete", new Class<?>[]{Long.class, Integer.class},
                "procurement:rfq:delete");
        assertPermission("send", new Class<?>[]{Long.class, RfqRequests.VersionCommand.class},
                "procurement:rfq:send");
        assertPermission("award", new Class<?>[]{Long.class, RfqRequests.AwardRequest.class},
                "procurement:rfq:award");
        assertPermission("cancel", new Class<?>[]{Long.class, RfqRequests.VersionCommand.class},
                "procurement:rfq:cancel");
    }

    /** 比价读取必须复用 RFQ 列表权限及同一数据范围。 */
    @Test
    void shouldProtectComparisonWithListPermission() throws Exception {
        Method method = RfqController.class.getMethod("comparison", Long.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('procurement:rfq:list')");
        assertThat(method.getAnnotation(ProcDataScope.class).permissionCode())
                .isEqualTo("procurement:rfq:list");
    }

    private void assertPermission(String methodName, Class<?>[] parameterTypes,
                                  String permission) throws Exception {
        Method method = RfqController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('" + permission + "')");
        assertThat(method.getAnnotation(ProcDataScope.class).permissionCode())
                .isEqualTo(permission);
    }
}
