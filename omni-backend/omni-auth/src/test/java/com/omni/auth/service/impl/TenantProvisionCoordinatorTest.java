package com.omni.auth.service.impl;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.omni.auth.catalog.ModuleCatalog;
import com.omni.auth.catalog.ModuleCatalog.ModuleDefinition;
import com.omni.auth.catalog.ModuleCatalog.TenantProvisioningMode;
import com.omni.auth.catalog.ModuleCatalogLoader;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.SysTenantModuleProvision;
import com.omni.auth.entity.TenantModuleProvisionStatusEnum;
import com.omni.auth.entity.TenantProvisionStatusEnum;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.mapper.SysTenantModuleProvisionMapper;
import com.omni.auth.service.TenantLocalProvisioner;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.tenant.TenantProvisionContracts.ModuleResultStatus;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionResultEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户初始化状态协调与 Outbox 边界测试。
 */
@SuppressWarnings("unchecked")
class TenantProvisionCoordinatorTest {

    /**
     * 启动初始化必须创建本地成功和事件待处理状态，跨服务事件不得包含管理员凭据。
     */
    @Test
    void should_start_with_local_success_and_credential_free_event() {
        ModuleCatalogLoader catalogLoader = catalogLoader();
        TenantLocalProvisioner localProvisioner = mock(TenantLocalProvisioner.class);
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        SysTenantModuleProvisionMapper stateMapper = mock(SysTenantModuleProvisionMapper.class);
        ReliableMessageRelay relay = mock(ReliableMessageRelay.class);
        when(stateMapper.selectOne(any())).thenReturn(null);
        TenantProvisionServiceImpl service = new TenantProvisionServiceImpl(
                catalogLoader, localProvisioner, tenantMapper, stateMapper, relay);
        SysTenant tenant = tenant();

        service.startProvisioning(tenant, "$2a$10$encoded");

        verify(localProvisioner).provisionLocal(9L, "第九租户", "$2a$10$encoded");
        ArgumentCaptor<SysTenantModuleProvision> stateCaptor =
                ArgumentCaptor.forClass(SysTenantModuleProvision.class);
        verify(stateMapper, times(2)).insert(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues())
                .extracting(SysTenantModuleProvision::getModuleId, SysTenantModuleProvision::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("auth", TenantModuleProvisionStatusEnum.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple("base", TenantModuleProvisionStatusEnum.PENDING));
        ArgumentCaptor<ProvisionRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProvisionRequestedEvent.class);
        verify(relay).send(
                eq(TenantProvisionServiceImpl.REQUEST_BINDING), eventCaptor.capture(), eq(9L), anyString());
        ProvisionRequestedEvent event = eventCaptor.getValue();
        assertThat(event.moduleIds()).containsExactly("base");
        assertThat(event.toString()).doesNotContain("encoded");
        assertThat(tenant.getProvisioningStatus()).isEqualTo(TenantProvisionStatusEnum.PROVISIONING);
        assertThat(tenant.getProvisioningRequestId()).isNotBlank();
    }

    /**
     * 失败结果必须在租户行锁内汇总，并对数据库连接和凭据片段二次脱敏。
     */
    @Test
    void should_sanitize_failed_result_and_mark_tenant_failed() {
        ModuleCatalogLoader catalogLoader = catalogLoader();
        TenantLocalProvisioner localProvisioner = mock(TenantLocalProvisioner.class);
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        SysTenantModuleProvisionMapper stateMapper = mock(SysTenantModuleProvisionMapper.class);
        ReliableMessageRelay relay = mock(ReliableMessageRelay.class);
        TenantProvisionServiceImpl service = new TenantProvisionServiceImpl(
                catalogLoader, localProvisioner, tenantMapper, stateMapper, relay);
        SysTenant tenant = tenant();
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
        tenant.setProvisioningRequestId("request-1");
        SysTenantModuleProvision local = state("request-1", "auth", TenantModuleProvisionStatusEnum.SUCCESS);
        SysTenantModuleProvision eventState = state(
                "request-1", "base", TenantModuleProvisionStatusEnum.PENDING);
        when(tenantMapper.selectByIdForUpdate(9L)).thenReturn(tenant);
        when(stateMapper.selectOne(any())).thenReturn(eventState);
        when(stateMapper.selectList(any())).thenReturn(List.of(local, eventState));
        ProvisionResultEvent event = new ProvisionResultEvent(
                "event-1", "request-1", 9L, "base", ModuleResultStatus.FAILED,
                "DB_FAILED", "jdbc:mysql://db:3306/omni password=plain-secret\nstack", Instant.now());

        service.handleResult(event);

        assertThat(eventState.getStatus()).isEqualTo(TenantModuleProvisionStatusEnum.FAILED);
        assertThat(eventState.getErrorMessage())
                .doesNotContain("mysql://", "plain-secret", "\n")
                .contains("[REDACTED_DB]", "password=[REDACTED]");
        assertThat(tenant.getProvisioningStatus()).isEqualTo(TenantProvisionStatusEnum.FAILED);
        assertThat(tenant.getProvisioningError()).startsWith("base:");
        verify(tenantMapper).selectByIdForUpdate(9L);
        verify(stateMapper).updateById(eventState);
        verify(tenantMapper).updateById(tenant);
    }

    /**
     * 创建包含一个本地模块和一个事件模块的最小目录。
     */
    private static ModuleCatalogLoader catalogLoader() {
        ModuleCatalogLoader loader = mock(ModuleCatalogLoader.class);
        ModuleDefinition auth = new ModuleDefinition(
                "auth", "foundation", List.of(), TenantProvisioningMode.LOCAL,
                List.of("system"), List.of("auth-root"));
        ModuleDefinition base = new ModuleDefinition(
                "base", "foundation", List.of("auth"), TenantProvisioningMode.EVENT,
                List.of("base"), List.of("base-dict"));
        when(loader.catalog()).thenReturn(new ModuleCatalog("1.0.0", List.of(auth, base)));
        return loader;
    }

    /**
     * 创建租户测试数据。
     */
    private static SysTenant tenant() {
        SysTenant tenant = new SysTenant();
        tenant.setId(9L);
        tenant.setTenantCode("tenant-nine");
        tenant.setTenantName("第九租户");
        tenant.setStatus(1);
        return tenant;
    }

    /**
     * 创建模块状态测试数据。
     */
    private static SysTenantModuleProvision state(
            String requestId, String moduleId, TenantModuleProvisionStatusEnum status) {
        SysTenantModuleProvision state = new SysTenantModuleProvision();
        state.setId((long) moduleId.hashCode() & Integer.MAX_VALUE);
        state.setTenantId(9L);
        state.setRequestId(requestId);
        state.setModuleId(moduleId);
        state.setStatus(status);
        state.setAttemptCount(1);
        state.setVersion(0);
        return state;
    }
}
