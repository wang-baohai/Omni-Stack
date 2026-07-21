package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.auth.dto.PortalRoleAssignmentCommand;
import com.omni.auth.dto.PortalRoleAssignmentResult;
import com.omni.auth.entity.SysPortalRoleRequest;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysPortalRoleRequestMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户角色分配 Inbox 服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PortalRoleAssignmentServiceImplTest {

    @Mock private SysPortalRoleRequestMapper requestMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private PortalRoleAssignmentServiceImpl service;
    private PortalRoleAssignmentCommand command;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "portal-role-assignment-test");
        assistant.setCurrentNamespace("portal-role-assignment-test");
        TableInfoHelper.initTableInfo(assistant, SysPortalRoleRequest.class);
        service = new PortalRoleAssignmentServiceImpl(
                requestMapper, userMapper, roleMapper, userRoleMapper, reliableMessageRelay);
        command = PortalRoleAssignmentCommand.builder()
                .requestId("request-1")
                .tenantId(1L)
                .supplierId(10L)
                .userId(20L)
                .roleCode("SUPPLIER")
                .build();
    }

    @Test
    void shouldAssignRoleAndPersistResultOutbox() {
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.insert(any(SysPortalRoleRequest.class))).thenReturn(1);
        when(requestMapper.update(isNull(), any())).thenReturn(1);
        when(userMapper.selectEnabledByIdAndTenantId(20L, 1L)).thenReturn(new SysUser());
        SysRole role = new SysRole();
        role.setId(30L);
        when(roleMapper.selectByTenantIdAndRoleCode(1L, "SUPPLIER")).thenReturn(role);

        PortalRoleAssignmentResult result = service.assign(command);

        assertTrue(result.isSuccess());
        verify(userRoleMapper).insertIgnore(20L, 30L);
        verify(reliableMessageRelay).send(eq("authPortalRoleResult-out-0"), any(), eq(1L), anyString());
    }

    @Test
    void shouldReplaySuccessForCompletedRequestWithoutDuplicateRoleInsert() {
        SysPortalRoleRequest receipt = receipt("COMPLETED");
        when(requestMapper.selectOne(any())).thenReturn(receipt);

        PortalRoleAssignmentResult result = service.assign(command);

        assertTrue(result.isSuccess());
        verify(userRoleMapper, never()).insertIgnore(anyLong(), anyLong());
        verify(reliableMessageRelay).send(eq("authPortalRoleResult-out-0"), any(), eq(1L), anyString());
    }

    @Test
    void shouldPersistFailedResultWhenUserIsDisabled() {
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.insert(any(SysPortalRoleRequest.class))).thenReturn(1);
        when(requestMapper.update(isNull(), any())).thenReturn(1);
        when(userMapper.selectEnabledByIdAndTenantId(20L, 1L)).thenReturn(null);

        PortalRoleAssignmentResult result = service.assign(command);

        assertFalse(result.isSuccess());
        assertEquals("USER_NOT_FOUND_OR_DISABLED", result.getErrorCode());
        verify(userRoleMapper, never()).insertIgnore(anyLong(), anyLong());
        verify(reliableMessageRelay).send(eq("authPortalRoleResult-out-0"), any(), eq(1L), anyString());
    }

    @Test
    void shouldRejectRequestIdReuseWithDifferentIdentity() {
        SysPortalRoleRequest receipt = receipt("COMPLETED");
        receipt.setUserId(999L);
        when(requestMapper.selectOne(any())).thenReturn(receipt);

        assertThrows(IllegalArgumentException.class, () -> service.assign(command));

        verify(reliableMessageRelay, never()).send(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void shouldRejectMalformedCommandBeforeDatabaseAccess() {
        PortalRoleAssignmentCommand malformed = PortalRoleAssignmentCommand.builder()
                .requestId(" ")
                .tenantId(1L)
                .supplierId(10L)
                .userId(20L)
                .roleCode("SUPPLIER")
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.assign(malformed));

        verify(requestMapper, never()).selectOne(any());
        verify(reliableMessageRelay, never()).send(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void shouldRequestMqRedeliveryWhenConcurrentReceiptInsertLosesUniqueRace() {
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.insert(any(SysPortalRoleRequest.class)))
                .thenThrow(new DuplicateKeyException("uk_portal_role_request"));

        assertThrows(IllegalStateException.class, () -> service.assign(command));

        verify(userRoleMapper, never()).insertIgnore(anyLong(), anyLong());
        verify(reliableMessageRelay, never()).send(anyString(), any(), anyLong(), anyString());
    }

    private SysPortalRoleRequest receipt(String status) {
        SysPortalRoleRequest receipt = new SysPortalRoleRequest();
        receipt.setId(1L);
        receipt.setTenantId(1L);
        receipt.setRequestId("request-1");
        receipt.setSupplierId(10L);
        receipt.setUserId(20L);
        receipt.setRoleCode("SUPPLIER");
        receipt.setStatus(status);
        receipt.setVersion(0);
        return receipt;
    }
}
