package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.entity.SrmSupplierInvite;
import com.omni.srm.mapper.SrmSupplierInviteMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 门户邀请原子消费测试。
 */
@ExtendWith(MockitoExtension.class)
class PortalInviteServiceImplTest {

    @Mock private SrmSupplierInviteMapper inviteMapper;

    private PortalInviteServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "portal-invite-test");
        assistant.setCurrentNamespace("portal-invite-test");
        TableInfoHelper.initTableInfo(assistant, SrmSupplierInvite.class);
        service = new PortalInviteServiceImpl(inviteMapper);
        ServiceIdentityContext.set(new ServiceRequestIdentity(20L, 1L, "supplier-user"));
    }

    @AfterEach
    void tearDown() {
        ServiceIdentityContext.clear();
    }

    @Test
    void shouldAtomicallyConsumeActiveInvite() {
        SrmSupplierInvite invite = activeInvite();
        when(inviteMapper.selectOne(any())).thenReturn(invite);
        when(inviteMapper.consume(eq(8L), eq(1L), eq(0), any(LocalDateTime.class), anyString()))
                .thenReturn(1);

        assertEquals(8L, service.consumeInviteToken("raw-token"));
    }

    @Test
    void shouldRejectConcurrentInviteConsumptionConflict() {
        SrmSupplierInvite invite = activeInvite();
        when(inviteMapper.selectOne(any())).thenReturn(invite);
        when(inviteMapper.consume(eq(8L), eq(1L), eq(0), any(LocalDateTime.class), anyString()))
                .thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.consumeInviteToken("raw-token"));

        assertEquals(409, exception.getCode());
    }

    @Test
    void shouldRequireInviteToken() {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.consumeInviteToken(" "));

        assertEquals(400, exception.getCode());
    }

    @Test
    void shouldExposeExpiredInviteWithoutLeakingToken() {
        SrmSupplierInvite invite = activeInvite();
        invite.setExpiresTime(LocalDateTime.now().minusMinutes(1));
        when(inviteMapper.selectList(any())).thenReturn(List.of(invite));

        assertEquals("EXPIRED", service.list().getFirst().getStatus());
        assertEquals(null, service.list().getFirst().getInviteToken());
    }

    private SrmSupplierInvite activeInvite() {
        SrmSupplierInvite invite = new SrmSupplierInvite();
        invite.setId(8L);
        invite.setTenantId(1L);
        invite.setStatus("ACTIVE");
        invite.setExpiresTime(LocalDateTime.now().plusHours(1));
        invite.setMaxUses(2);
        invite.setUsedCount(0);
        invite.setVersion(0);
        invite.setDeleted(0);
        return invite;
    }
}
