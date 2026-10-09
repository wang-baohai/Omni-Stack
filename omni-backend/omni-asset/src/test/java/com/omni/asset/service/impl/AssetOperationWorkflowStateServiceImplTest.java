package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.asset.domain.AssetOperationStateMachine;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstDisposal;
import com.omni.asset.entity.AstTransfer;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.mapper.AstDisposalMapper;
import com.omni.asset.mapper.AstTransferMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.asset.service.support.AssetRecordAccessGuard;
import com.omni.asset.workflow.AssetWorkflowCommand;
import com.omni.common.core.result.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 资产活动操作原子占位与 Workflow 重试快照测试。 */
@ExtendWith(MockitoExtension.class)
class AssetOperationWorkflowStateServiceImplTest {

    @Mock private AstAssetMapper assetMapper;
    @Mock private AstAssetHistoryMapper historyMapper;
    @Mock private AstTransferMapper transferMapper;
    @Mock private AstDisposalMapper disposalMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(AstAsset.class, "AstAssetMapper");
        initialize(AstAssetHistory.class, "AstAssetHistoryMapper");
        initialize(AstTransfer.class, "AstTransferMapper");
        initialize(AstDisposal.class, "AstDisposalMapper");
    }

    /** 清理身份上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 原子占位未命中时必须返回 409，不能写历史。 */
    @Test
    void shouldRejectConcurrentTransferWhenAssetSlotWasOccupied() {
        AstAsset asset = asset();
        when(assetMapper.selectForUpdate(1L, 50L)).thenReturn(asset);
        when(transferMapper.insert(any(AstTransfer.class))).thenAnswer(invocation -> {
            AstTransfer transfer = invocation.getArgument(0);
            transfer.setId(10L);
            return 1;
        });
        when(transferMapper.setTransferNoAfterInsert(1L, 10L, "AT-1-10")).thenReturn(1);
        when(transferMapper.update(
                ArgumentMatchers.<AstTransfer>isNull(),
                ArgumentMatchers.<Wrapper<AstTransfer>>any())).thenReturn(1);
        when(assetMapper.occupyOperation(
                asset, AssetStateMachine.TRANSFER, "TRANSFER", 10L, "admin")).thenReturn(0);
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        assertThatThrownBy(() -> service().prepareTransfer(transferRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("占用");
        verify(historyMapper, never()).insert(any(AstAssetHistory.class));
    }

    /** 启动失败重试必须复用原 requestId、businessKey 和模型版本。 */
    @Test
    void shouldReuseOriginalWorkflowSnapshotWhenRetrying() {
        AstTransfer transfer = new AstTransfer();
        transfer.setId(10L);
        transfer.setTenantId(1L);
        transfer.setTransferNo("AT-1-10");
        transfer.setAssetId(50L);
        transfer.setToUserId(8L);
        transfer.setToUnitId(12L);
        transfer.setPreviousAssetStatus(AssetStateMachine.IN_USE);
        transfer.setStatus(AssetOperationStateMachine.START_FAILED);
        transfer.setWorkflowStartStatus(AssetOperationStateMachine.START_FAILED_FLAG);
        transfer.setWorkflowRequestId("request-original");
        transfer.setWorkflowBusinessKey("10");
        transfer.setModelVersionId(42L);
        transfer.setWorkflowStartUserId(6L);
        transfer.setWorkflowStartUserName("original-user");
        transfer.setVersion(3);
        transfer.setDeleted(0);
        AstAsset asset = asset();
        asset.setStatus(AssetStateMachine.TRANSFER);
        asset.setActiveOperationType("TRANSFER");
        asset.setActiveOperationId(10L);
        when(transferMapper.selectForUpdate(1L, 10L)).thenReturn(transfer);
        when(assetMapper.selectForUpdate(1L, 50L)).thenReturn(asset);
        when(transferMapper.update(
                ArgumentMatchers.<AstTransfer>isNull(),
                ArgumentMatchers.<Wrapper<AstTransfer>>any())).thenReturn(1);
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        AssetWorkflowCommand command = service().prepareRetry("TRANSFER", 10L, 3);

        assertThat(command.requestId()).isEqualTo("request-original");
        assertThat(command.businessKey()).isEqualTo("10");
        assertThat(command.modelVersionId()).isEqualTo(42L);
        assertThat(command.startUserId()).isEqualTo(6L);
        assertThat(command.startUserName()).isEqualTo("original-user");
    }

    /** 启动结果未知时允许复用原快照重试，但不得把待确认状态改写为明确失败。 */
    @Test
    void shouldRetryUnknownOutcomeWithoutChangingPendingState() {
        AstTransfer transfer = new AstTransfer();
        transfer.setId(10L);
        transfer.setTenantId(1L);
        transfer.setTransferNo("AT-1-10");
        transfer.setAssetId(50L);
        transfer.setToUserId(8L);
        transfer.setToUnitId(12L);
        transfer.setPreviousAssetStatus(AssetStateMachine.IN_USE);
        transfer.setStatus(AssetOperationStateMachine.PENDING_APPROVAL);
        transfer.setWorkflowStartStatus(AssetOperationStateMachine.START_PENDING);
        transfer.setWorkflowRequestId("request-unknown");
        transfer.setWorkflowBusinessKey("10");
        transfer.setModelVersionId(42L);
        transfer.setWorkflowStartUserId(6L);
        transfer.setWorkflowStartUserName("original-user");
        transfer.setVersion(3);
        transfer.setDeleted(0);
        AstAsset asset = asset();
        asset.setStatus(AssetStateMachine.TRANSFER);
        asset.setActiveOperationType("TRANSFER");
        asset.setActiveOperationId(10L);
        when(transferMapper.selectForUpdate(1L, 10L)).thenReturn(transfer);
        when(assetMapper.selectForUpdate(1L, 50L)).thenReturn(asset);
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        AssetWorkflowCommand command = service().prepareRetry("TRANSFER", 10L, 3);

        assertThat(command.requestId()).isEqualTo("request-unknown");
        assertThat(command.startUserId()).isEqualTo(6L);
        verify(transferMapper, never())
                .update(ArgumentMatchers.<AstTransfer>isNull(),
                        ArgumentMatchers.<Wrapper<AstTransfer>>any());
    }

    private AssetOperationWorkflowStateServiceImpl service() {
        return new AssetOperationWorkflowStateServiceImpl(assetMapper, historyMapper,
                transferMapper, disposalMapper, new AssetRecordAccessGuard());
    }

    private AssetOperationRequests.CreateTransferRequest transferRequest() {
        AssetOperationRequests.CreateTransferRequest request =
                new AssetOperationRequests.CreateTransferRequest();
        request.setAssetId(50L);
        request.setToUserId(8L);
        request.setToUnitId(12L);
        request.setReason("岗位调整");
        request.setModelVersionId(42L);
        return request;
    }

    private AstAsset asset() {
        AstAsset asset = new AstAsset();
        asset.setId(50L);
        asset.setTenantId(1L);
        asset.setAssetNo("AST-50");
        asset.setName("笔记本");
        asset.setStatus(AssetStateMachine.IN_USE);
        asset.setCurrentUserId(7L);
        asset.setCurrentUnitId(11L);
        asset.setVersion(2);
        asset.setDeleted(0);
        return asset;
    }

    private static void initialize(Class<?> entityClass, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, resource), entityClass);
    }
}
