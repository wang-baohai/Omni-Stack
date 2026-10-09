package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.asset.domain.AssetOperationStateMachine;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstDisposal;
import com.omni.asset.entity.AstTransfer;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.mapper.AstDisposalMapper;
import com.omni.asset.mapper.AstTransferMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.asset.service.AssetOperationWorkflowStateService;
import com.omni.asset.service.support.AssetIdentityGuard;
import com.omni.asset.service.support.AssetRecordAccessGuard;
import com.omni.asset.workflow.AssetWorkflowApprovalGuard;
import com.omni.asset.workflow.AssetWorkflowCoordinator;
import com.omni.asset.workflow.AssetWorkflowModelGuard;
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

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 调拨与处置 approval-view 任务边界及 DataScope 恢复测试。 */
@ExtendWith(MockitoExtension.class)
class AssetOperationApprovalViewTest {

    @Mock private AstTransferMapper transferMapper;
    @Mock private AstDisposalMapper disposalMapper;
    @Mock private AstAssetMapper assetMapper;
    @Mock private AstAssetHistoryMapper historyMapper;
    @Mock private AssetOperationWorkflowStateService workflowStateService;
    @Mock private AssetWorkflowCoordinator workflowCoordinator;
    @Mock private AssetWorkflowModelGuard workflowModelGuard;
    @Mock private AssetWorkflowApprovalGuard workflowApprovalGuard;
    @Mock private AssetIdentityGuard identityGuard;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    /** 初始化 approval-view 查询使用的 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(AstTransfer.class, "AstTransferMapper");
        initialize(AstDisposal.class, "AstDisposalMapper");
        initialize(AstAsset.class, "AstAssetMapper");
    }

    /** 清理请求身份与数据范围线程上下文。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 调拨审批视图必须完整校验任务意图，并仅在详情读取期间提升到租户范围。 */
    @Test
    void shouldReadTransferApprovalViewWithinTemporaryTenantScope() {
        bindIdentity();
        ServiceDataScopeContext.ScopeInfo previous = previousScope("asset:transfer:approve");
        ServiceDataScopeContext.set(previous);
        AstTransfer transfer = transfer();
        AstAsset asset = asset();
        AtomicReference<ServiceDataScopeContext.ScopeInfo> scopeDuringRead =
                new AtomicReference<>();
        when(transferMapper.selectWorkflowIdentity(31L, 100L)).thenReturn(transfer);
        when(transferMapper.selectOne(ArgumentMatchers.<Wrapper<AstTransfer>>any()))
                .thenAnswer(invocation -> {
                    scopeDuringRead.set(ServiceDataScopeContext.require());
                    return transfer;
                });
        when(assetMapper.selectOne(ArgumentMatchers.<Wrapper<AstAsset>>any()))
                .thenReturn(asset);

        var view = transferService().approvalView(100L, "task-transfer");

        assertThat(view.getId()).isEqualTo(100L);
        ArgumentCaptor<AssetWorkflowApprovalGuard.AssignmentIntent> captor =
                ArgumentCaptor.forClass(AssetWorkflowApprovalGuard.AssignmentIntent.class);
        verify(workflowApprovalGuard).requireAssigned(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new AssetWorkflowApprovalGuard.AssignmentIntent(
                        31L, 7L, "task-transfer",
                        AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE,
                        "100", "process-transfer"));
        assertThat(scopeDuringRead.get().effectiveScope()).isEqualTo("TENANT");
        assertThat(scopeDuringRead.get().permissionCode())
                .isEqualTo("asset:transfer:approve");
        assertThat(ServiceDataScopeContext.get()).isEqualTo(previous);
    }

    /** 处置审批视图必须使用处置业务键和本地流程实例执行任务校验。 */
    @Test
    void shouldReadDisposalApprovalViewWithinTemporaryTenantScope() {
        bindIdentity();
        ServiceDataScopeContext.ScopeInfo previous = previousScope("asset:disposal:approve");
        ServiceDataScopeContext.set(previous);
        AstDisposal disposal = disposal();
        AtomicReference<ServiceDataScopeContext.ScopeInfo> scopeDuringRead =
                new AtomicReference<>();
        when(disposalMapper.selectWorkflowIdentity(31L, 200L)).thenReturn(disposal);
        when(disposalMapper.selectOne(ArgumentMatchers.<Wrapper<AstDisposal>>any()))
                .thenAnswer(invocation -> {
                    scopeDuringRead.set(ServiceDataScopeContext.require());
                    return disposal;
                });
        when(assetMapper.selectOne(ArgumentMatchers.<Wrapper<AstAsset>>any()))
                .thenReturn(asset());

        var view = disposalService().approvalView(200L, "task-disposal");

        assertThat(view.getId()).isEqualTo(200L);
        ArgumentCaptor<AssetWorkflowApprovalGuard.AssignmentIntent> captor =
                ArgumentCaptor.forClass(AssetWorkflowApprovalGuard.AssignmentIntent.class);
        verify(workflowApprovalGuard).requireAssigned(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new AssetWorkflowApprovalGuard.AssignmentIntent(
                        31L, 7L, "task-disposal",
                        AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE,
                        "200", "process-disposal"));
        assertThat(scopeDuringRead.get().effectiveScope()).isEqualTo("TENANT");
        assertThat(scopeDuringRead.get().permissionCode())
                .isEqualTo("asset:disposal:approve");
        assertThat(ServiceDataScopeContext.get()).isEqualTo(previous);
    }

    /** 非活动审批快照不得调用 Workflow，也不得进入租户范围详情读取。 */
    @Test
    void shouldRejectInactiveTransferBeforeApprovalScopeBypass() {
        bindIdentity();
        ServiceDataScopeContext.ScopeInfo previous = previousScope("asset:transfer:approve");
        ServiceDataScopeContext.set(previous);
        AstTransfer transfer = transfer();
        transfer.setStatus(AssetOperationStateMachine.APPROVED);
        when(transferMapper.selectWorkflowIdentity(31L, 100L)).thenReturn(transfer);

        assertThatThrownBy(() -> transferService().approvalView(100L, "task-transfer"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verifyNoInteractions(workflowApprovalGuard);
        verify(transferMapper, never()).selectOne(any());
        assertThat(ServiceDataScopeContext.get()).isEqualTo(previous);
    }

    /** Workflow 拒绝任务资格时不得读取处置详情或提升 DataScope。 */
    @Test
    void shouldRejectUnassignedDisposalWithoutApprovalScopeBypass() {
        bindIdentity();
        ServiceDataScopeContext.ScopeInfo previous = previousScope("asset:disposal:approve");
        ServiceDataScopeContext.set(previous);
        when(disposalMapper.selectWorkflowIdentity(31L, 200L)).thenReturn(disposal());
        doThrow(new BusinessException(403, "任务未分配"))
                .when(workflowApprovalGuard)
                .requireAssigned(any(AssetWorkflowApprovalGuard.AssignmentIntent.class));

        assertThatThrownBy(() -> disposalService().approvalView(200L, "task-disposal"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);

        verify(disposalMapper, never()).selectOne(any());
        verify(assetMapper, never()).selectOne(any());
        assertThat(ServiceDataScopeContext.get()).isEqualTo(previous);
    }

    /** 资格通过后详情查询异常也必须恢复调用方原始 DataScope。 */
    @Test
    void shouldRestorePreviousScopeWhenTransferDetailReadFails() {
        bindIdentity();
        ServiceDataScopeContext.ScopeInfo previous = previousScope("asset:transfer:approve");
        ServiceDataScopeContext.set(previous);
        when(transferMapper.selectWorkflowIdentity(31L, 100L)).thenReturn(transfer());
        when(transferMapper.selectOne(ArgumentMatchers.<Wrapper<AstTransfer>>any()))
                .thenAnswer(invocation -> {
                    assertThat(ServiceDataScopeContext.require().effectiveScope())
                            .isEqualTo("TENANT");
                    throw new IllegalStateException("database unavailable");
                });

        assertThatThrownBy(() -> transferService().approvalView(100L, "task-transfer"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ServiceDataScopeContext.get()).isEqualTo(previous);
    }

    private AssetTransferServiceImpl transferService() {
        return new AssetTransferServiceImpl(
                transferMapper, assetMapper, historyMapper, workflowStateService,
                workflowCoordinator, workflowModelGuard, workflowApprovalGuard,
                identityGuard, new AssetRecordAccessGuard(), reliableMessageRelay);
    }

    private AssetDisposalServiceImpl disposalService() {
        return new AssetDisposalServiceImpl(
                disposalMapper, assetMapper, historyMapper, workflowStateService,
                workflowCoordinator, workflowModelGuard, workflowApprovalGuard,
                new AssetRecordAccessGuard(), reliableMessageRelay);
    }

    private void bindIdentity() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "approver"));
    }

    private ServiceDataScopeContext.ScopeInfo previousScope(String permissionCode) {
        return new ServiceDataScopeContext.ScopeInfo(
                7L, 31L, permissionCode, 12L, "DEPT", Set.of(12L), null);
    }

    private AstTransfer transfer() {
        AstTransfer transfer = new AstTransfer();
        transfer.setId(100L);
        transfer.setTenantId(31L);
        transfer.setAssetId(50L);
        transfer.setStatus(AssetOperationStateMachine.PENDING_APPROVAL);
        transfer.setWorkflowStartStatus(AssetOperationStateMachine.START_STARTED);
        transfer.setWorkflowBusinessKey("100");
        transfer.setProcessInstanceId("process-transfer");
        transfer.setVersion(2);
        return transfer;
    }

    private AstDisposal disposal() {
        AstDisposal disposal = new AstDisposal();
        disposal.setId(200L);
        disposal.setTenantId(31L);
        disposal.setAssetId(50L);
        disposal.setStatus(AssetOperationStateMachine.PENDING_APPROVAL);
        disposal.setWorkflowStartStatus(AssetOperationStateMachine.START_STARTED);
        disposal.setWorkflowBusinessKey("200");
        disposal.setProcessInstanceId("process-disposal");
        disposal.setVersion(3);
        return disposal;
    }

    private AstAsset asset() {
        AstAsset asset = new AstAsset();
        asset.setId(50L);
        asset.setTenantId(31L);
        asset.setAssetNo("AT-50");
        asset.setName("测试资产");
        return asset;
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "asset-approval-view-" + mapperName);
        assistant.setCurrentNamespace("com.omni.asset.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
