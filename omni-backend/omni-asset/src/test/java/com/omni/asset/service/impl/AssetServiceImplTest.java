package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetRequests;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.asset.service.support.AssetIdentityGuard;
import com.omni.asset.service.support.AssetRecordAccessGuard;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 资产台账命令、本人范围和乐观锁测试。 */
@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock private AstAssetMapper assetMapper;
    @Mock private AstAssetHistoryMapper historyMapper;
    @Mock private AssetRecordAccessGuard accessGuard;
    @Mock private AssetIdentityGuard identityGuard;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(AstAsset.class, "AstAssetMapper");
        initialize(AstAssetHistory.class, "AstAssetHistoryMapper");
    }

    /** 清理资产身份上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** “我的资产”必须显式追加 current_user_id，不能依赖宽权限角色。 */
    @Test
    void shouldAlwaysFilterMyAssetsByCurrentUser() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "employee"));
        AtomicReference<Wrapper<AstAsset>> wrapperRef = new AtomicReference<>();
        when(assetMapper.selectPage(
                ArgumentMatchers.<Page<AstAsset>>any(),
                ArgumentMatchers.<Wrapper<AstAsset>>any()))
                .thenAnswer(invocation -> {
                    Page<AstAsset> page = invocation.getArgument(0);
                    wrapperRef.set(invocation.getArgument(1));
                    page.setRecords(List.of());
                    page.setTotal(0);
                    return page;
                });
        AssetServiceImpl service = service();

        service.pageMine(new AssetRequests.MyAssetQuery());

        assertThat(wrapperRef.get().getSqlSegment())
                .contains("tenant_id").contains("current_user_id");
    }

    /** 退还必须校验本人、版本和状态，并原子清空使用关系。 */
    @Test
    void shouldReturnAssignedAssetWithOptimisticCondition() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "employee"));
        AstAsset asset = asset(AssetStateMachine.ALLOCATED, 2);
        asset.setCurrentUserId(7L);
        asset.setCurrentUnitId(12L);
        when(assetMapper.selectForUpdate(31L, 100L)).thenReturn(asset);
        when(accessGuard.requireVisible(asset, "资产不存在")).thenReturn(asset);
        when(assetMapper.update(
                ArgumentMatchers.<AstAsset>isNull(),
                ArgumentMatchers.<Wrapper<AstAsset>>any()))
                .thenAnswer(invocation -> {
                    LambdaUpdateWrapper<AstAsset> update = invocation.getArgument(1);
                    assertThat(update.getSqlSegment())
                            .contains("tenant_id").contains("version").contains("status");
                    assertThat(update.getSqlSet())
                            .contains("version = version + 1")
                            .contains("current_user_id").contains("current_unit_id");
                    return 1;
                });
        AssetRequests.VersionCommandRequest request = command(2);
        AssetServiceImpl service = service();

        var result = service.returnAsset(100L, request);

        verify(accessGuard).requireAssignedToCurrentUser(asset);
        verify(historyMapper).insert(any(AstAssetHistory.class));
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq("asset-domain-out-0"),
                any(), org.mockito.ArgumentMatchers.eq(31L), anyString());
        assertThat(result.getStatus()).isEqualTo(AssetStateMachine.IN_STOCK);
        assertThat(result.getCurrentUserId()).isNull();
        assertThat(result.getCurrentUnitId()).isNull();
        assertThat(result.getVersion()).isEqualTo(3);
    }

    /** 过期版本必须在写数据库前以 409 拒绝。 */
    @Test
    void shouldRejectStaleVersionBeforeUpdate() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "employee"));
        AstAsset asset = asset(AssetStateMachine.ALLOCATED, 3);
        asset.setCurrentUserId(7L);
        when(assetMapper.selectForUpdate(31L, 100L)).thenReturn(asset);
        when(accessGuard.requireVisible(asset, "资产不存在")).thenReturn(asset);
        AssetServiceImpl service = service();

        assertThatThrownBy(() -> service.accept(100L, command(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verify(assetMapper, never()).update(any(), any());
    }

    /** 非在库资产不得重新分配。 */
    @Test
    void shouldRejectAllocationFromInUse() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(9L, 31L, "manager"));
        AstAsset asset = asset(AssetStateMachine.IN_USE, 1);
        when(assetMapper.selectForUpdate(31L, 100L)).thenReturn(asset);
        when(accessGuard.requireVisible(asset, "资产不存在")).thenReturn(asset);
        AssetRequests.AllocateRequest request = new AssetRequests.AllocateRequest();
        request.setVersion(1);
        request.setTargetUserId(7L);
        request.setTargetUnitId(12L);
        AssetServiceImpl service = service();

        assertThatThrownBy(() -> service.allocate(100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verify(assetMapper, never()).update(any(), any());
    }

    /** 普通更新请求不得暴露状态、使用人或位置字段。 */
    @Test
    void shouldKeepCommandOwnedFieldsOutOfUpdateRequest() {
        assertThat(java.util.Arrays.stream(AssetRequests.UpdateAssetRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("status", "currentUserId", "currentUnitId", "locationCode",
                        "activeOperationType", "activeOperationId");
    }

    private AssetServiceImpl service() {
        return new AssetServiceImpl(assetMapper, historyMapper, accessGuard, identityGuard,
                reliableMessageRelay);
    }

    private AstAsset asset(String status, int version) {
        AstAsset asset = new AstAsset();
        asset.setId(100L);
        asset.setTenantId(31L);
        asset.setAssetNo("AST-100");
        asset.setStatus(status);
        asset.setOwnerUserId(9L);
        asset.setOwnerUnitId(12L);
        asset.setVersion(version);
        asset.setDeleted(0);
        return asset;
    }

    private AssetRequests.VersionCommandRequest command(int version) {
        AssetRequests.VersionCommandRequest request = new AssetRequests.VersionCommandRequest();
        request.setVersion(version);
        return request;
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "asset-service-" + mapperName);
        assistant.setCurrentNamespace("com.omni.asset.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
