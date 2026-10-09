package com.omni.common.mqlog.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ModuleResultStatus;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionResultEvent;
import com.omni.common.mqlog.entity.SysTenantProvisionReceipt;
import com.omni.common.mqlog.mapper.SysTenantProvisionReceiptMapper;

/**
 * 租户模块初始化通用处理器测试。
 */
class TenantProvisionRequestHandlerTest {

    private TenantModuleProvisioner provisioner;
    private SysTenantProvisionReceiptMapper receiptMapper;
    private ReliableMessageRelay reliableMessageRelay;
    private AtomicReference<SysTenantProvisionReceipt> receipt;
    private TenantProvisionRequestHandler handler;

    @BeforeEach
    void setUp() {
        provisioner = mock(TenantModuleProvisioner.class);
        receiptMapper = mock(SysTenantProvisionReceiptMapper.class);
        reliableMessageRelay = mock(ReliableMessageRelay.class);
        receipt = new AtomicReference<>();
        when(provisioner.moduleId()).thenReturn("crm");
        when(receiptMapper.selectOne(any())).thenAnswer(invocation -> receipt.get());
        when(receiptMapper.insert(any(SysTenantProvisionReceipt.class))).thenAnswer(invocation -> {
            receipt.set(invocation.getArgument(0));
            return 1;
        });
        handler = new TenantProvisionRequestHandler(
                provisioner, receiptMapper, reliableMessageRelay, new NoOpTransactionManager());
    }

    @Test
    void shouldProvisionAndPublishSuccessOnlyOnce() {
        ProvisionRequestedEvent event = request(List.of("crm", "srm"));

        handler.handle(event);
        handler.handle(event);

        verify(provisioner, times(1)).provision(event);
        verify(reliableMessageRelay, times(1)).send(
                org.mockito.ArgumentMatchers.eq(TenantProvisionRequestHandler.RESULT_BINDING),
                any(ProvisionResultEvent.class),
                org.mockito.ArgumentMatchers.eq(31L),
                any(String.class));
        assertThat(receipt.get().getStatus()).isEqualTo("SUCCESS");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq(TenantProvisionRequestHandler.RESULT_BINDING),
                payload.capture(),
                org.mockito.ArgumentMatchers.eq(31L),
                any(String.class));
        ProvisionResultEvent result = (ProvisionResultEvent) payload.getValue();
        assertThat(result.status()).isEqualTo(ModuleResultStatus.SUCCESS);
        assertThat(result.errorCode()).isNull();
    }

    @Test
    void shouldRollbackDomainAndPublishSanitizedFailureReceipt() {
        ProvisionRequestedEvent event = request(List.of("crm"));
        doThrow(new IllegalStateException(
                "jdbc:mysql://db:3306/omni password=plain-secret\ntrace"))
                .when(provisioner).provision(event);

        handler.handle(event);

        assertThat(receipt.get().getStatus()).isEqualTo("FAILED");
        assertThat(receipt.get().getErrorMessage())
                .contains("[REDACTED_DB]", "password=[REDACTED]")
                .doesNotContain("plain-secret", "mysql://", "\n");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq(TenantProvisionRequestHandler.RESULT_BINDING),
                payload.capture(),
                org.mockito.ArgumentMatchers.eq(31L),
                any(String.class));
        ProvisionResultEvent result = (ProvisionResultEvent) payload.getValue();
        assertThat(result.status()).isEqualTo(ModuleResultStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo(receipt.get().getErrorMessage());
    }

    @Test
    void shouldIgnoreRequestForOtherModules() {
        handler.handle(request(List.of("srm")));

        verify(provisioner, never()).provision(any());
        verify(receiptMapper, never()).insert(any(SysTenantProvisionReceipt.class));
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    private static ProvisionRequestedEvent request(List<String> moduleIds) {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", 31L, "tenant-31", "租户 31", moduleIds, Instant.now());
    }

    /**
     * 仅验证事务编排分支的无资源事务管理器。
     */
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 无外部资源。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // 无外部资源。
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // 无外部资源。
        }
    }
}
