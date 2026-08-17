package com.omni.auth.controller;

import com.omni.auth.service.PermissionService;
import com.omni.auth.service.PermissionTreeNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 动态菜单失败关闭测试。
 */
@ExtendWith(MockitoExtension.class)
class MenuControllerTest {

    @Mock private PermissionService permissionService;

    /** 清理安全上下文。 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 仅有角色但没有功能权限时不得返回全部菜单。 */
    @Test
    void shouldReturnEmptyMenusWhenPermissionAuthoritiesAreEmpty() {
        PermissionTreeNode menu = PermissionTreeNode.builder()
                .id(1L)
                .permissionCode("system:user")
                .permissionName("用户管理")
                .type("MENU")
                .children(List.of())
                .build();
        when(permissionService.getPermissionTree(3L)).thenReturn(List.of(menu));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("alice", null, "ROLE_USER"));

        var response = new MenuController(permissionService).getMenus(3L);

        assertThat(response.getData()).isEmpty();
    }
}
