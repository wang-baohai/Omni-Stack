package com.omni.common.mqlog.tenant;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts;
import com.omni.common.core.tenant.TenantProvisionContracts.ModuleResultStatus;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionResultEvent;
import com.omni.common.mqlog.entity.SysTenantProvisionReceipt;
import com.omni.common.mqlog.mapper.SysTenantProvisionReceiptMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨服务租户初始化请求的通用事务处理器。
 *
 * <p>领域初始化和成功结果 Outbox 在同一事务中提交；失败时先回滚领域事务，再用独立事务保存失败回执和结果。</p>
 */
@Slf4j
public class TenantProvisionRequestHandler {

    /** 结果 Outbox binding。 */
    public static final String RESULT_BINDING = "tenantProvisionResult-out-0";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final TenantModuleProvisioner provisioner;
    private final SysTenantProvisionReceiptMapper receiptMapper;
    private final ReliableMessageRelay reliableMessageRelay;
    private final TransactionTemplate requiredTransaction;
    private final TransactionTemplate requiresNewTransaction;

    /**
     * 创建处理器。
     *
     * @param provisioner 模块领域初始化器
     * @param receiptMapper 回执 Mapper
     * @param reliableMessageRelay 可靠消息 Outbox
     * @param transactionManager 事务管理器
     */
    public TenantProvisionRequestHandler(
            TenantModuleProvisioner provisioner,
            SysTenantProvisionReceiptMapper receiptMapper,
            ReliableMessageRelay reliableMessageRelay,
            PlatformTransactionManager transactionManager) {
        this.provisioner = provisioner;
        this.receiptMapper = receiptMapper;
        this.reliableMessageRelay = reliableMessageRelay;
        this.requiredTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 处理一条初始化请求。
     *
     * @param event 初始化请求
     */
    public void handle(ProvisionRequestedEvent event) {
        validate(event);
        String moduleId = provisioner.moduleId();
        if (!event.moduleIds().contains(moduleId)) {
            return;
        }
        try {
            requiredTransaction.executeWithoutResult(status -> provisionSuccess(event, moduleId));
        } catch (RuntimeException exception) {
            recordFailure(event, moduleId, exception);
        }
    }

    private void provisionSuccess(ProvisionRequestedEvent event, String moduleId) {
        if (findReceipt(event.requestId(), moduleId) != null) {
            return;
        }
        provisioner.provision(event);
        SysTenantProvisionReceipt receipt = newReceipt(event, moduleId, SUCCESS);
        receiptMapper.insert(receipt);
        publishResult(event, moduleId, ModuleResultStatus.SUCCESS, null, null);
    }

    private void recordFailure(ProvisionRequestedEvent event, String moduleId, RuntimeException exception) {
        String errorCode = normalizeErrorCode(exception);
        String errorMessage = sanitizeError(exception.getMessage());
        try {
            requiresNewTransaction.executeWithoutResult(status -> {
                if (findReceipt(event.requestId(), moduleId) != null) {
                    return;
                }
                SysTenantProvisionReceipt receipt = newReceipt(event, moduleId, FAILED);
                receipt.setErrorCode(errorCode);
                receipt.setErrorMessage(errorMessage);
                receiptMapper.insert(receipt);
                publishResult(event, moduleId, ModuleResultStatus.FAILED, errorCode, errorMessage);
            });
        } catch (RuntimeException recordException) {
            recordException.addSuppressed(exception);
            throw recordException;
        }
        log.error(
                "租户模块初始化失败，已写入可靠失败回执: tenantId={}, requestId={}, moduleId={}, errorCode={}, errorMessage={}",
                event.tenantId(), event.requestId(), moduleId, errorCode, errorMessage);
    }

    private SysTenantProvisionReceipt findReceipt(String requestId, String moduleId) {
        return receiptMapper.selectOne(new LambdaQueryWrapper<SysTenantProvisionReceipt>()
                .eq(SysTenantProvisionReceipt::getRequestId, requestId)
                .eq(SysTenantProvisionReceipt::getModuleId, moduleId));
    }

    private static SysTenantProvisionReceipt newReceipt(
            ProvisionRequestedEvent event, String moduleId, String status) {
        LocalDateTime now = LocalDateTime.now();
        SysTenantProvisionReceipt receipt = new SysTenantProvisionReceipt();
        receipt.setRequestId(event.requestId());
        receipt.setTenantId(event.tenantId());
        receipt.setModuleId(moduleId);
        receipt.setStatus(status);
        receipt.setCreateTime(now);
        receipt.setUpdateTime(now);
        return receipt;
    }

    private void publishResult(
            ProvisionRequestedEvent request,
            String moduleId,
            ModuleResultStatus resultStatus,
            String errorCode,
            String errorMessage) {
        String eventId = deterministicResultId(request.requestId(), moduleId, resultStatus);
        ProvisionResultEvent result = new ProvisionResultEvent(
                eventId,
                request.requestId(),
                request.tenantId(),
                moduleId,
                resultStatus,
                errorCode,
                errorMessage,
                Instant.now());
        reliableMessageRelay.send(RESULT_BINDING, result, request.tenantId(), eventId);
    }

    private void validate(ProvisionRequestedEvent event) {
        if (event == null || event.tenantId() == null || event.tenantId() <= 0
                || !StringUtils.hasText(event.eventId()) || !StringUtils.hasText(event.requestId())
                || event.moduleIds() == null) {
            throw new IllegalArgumentException("租户初始化请求事件无效");
        }
        if (!StringUtils.hasText(provisioner.moduleId())) {
            throw new IllegalStateException("租户模块初始化器未声明模块 ID");
        }
    }

    private static String normalizeErrorCode(RuntimeException exception) {
        String name = exception.getClass().getSimpleName()
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toUpperCase();
        String value = StringUtils.hasText(name) ? name : "MODULE_PROVISION_FAILED";
        return truncate(value, MAX_ERROR_CODE_LENGTH);
    }

    private static String sanitizeError(String message) {
        if (!StringUtils.hasText(message)) {
            return "模块初始化失败";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)jdbc:[^\\s]+", "[REDACTED_DB]")
                .replaceAll(
                        "(?i)(password|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+",
                        "$1=[REDACTED]")
                .trim();
        return truncate(sanitized, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private static String deterministicResultId(
            String requestId, String moduleId, ModuleResultStatus resultStatus) {
        String source = TenantProvisionContracts.RESULT_EVENT_TYPE + ":"
                + requestId + ":" + moduleId + ":" + resultStatus;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
