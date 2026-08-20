package com.omni.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.omni.auth.service.TenantProvisionService;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.tenant.TenantProvisionContracts;
import com.omni.common.core.tenant.TenantProvisionContracts.ModuleResultStatus;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 租户模块化初始化协调服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisionServiceImpl implements TenantProvisionService {

    /** 租户初始化请求 Outbox binding。 */
    static final String REQUEST_BINDING = "tenantProvisionRequested-out-0";
    /** 错误摘要最大长度。 */
    private static final int MAX_ERROR_LENGTH = 500;
    /** 错误码最大长度。 */
    private static final int MAX_ERROR_CODE_LENGTH = 64;

    /** 模块目录加载器。 */
    private final ModuleCatalogLoader moduleCatalogLoader;
    /** Auth 本地初始化器。 */
    private final TenantLocalProvisioner localProvisioner;
    /** 租户 Mapper。 */
    private final SysTenantMapper tenantMapper;
    /** 模块初始化状态 Mapper。 */
    private final SysTenantModuleProvisionMapper moduleProvisionMapper;
    /** 可靠消息 Outbox。 */
    private final ReliableMessageRelay reliableMessageRelay;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void startProvisioning(SysTenant tenant, String encodedAdminPassword) {
        requirePersistedTenant(tenant);
        String requestId = UUID.randomUUID().toString();
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
        tenant.setProvisioningRequestId(requestId);
        tenant.setProvisioningError(null);
        tenantMapper.updateById(tenant);

        localProvisioner.provisionLocal(tenant.getId(), tenant.getTenantName(), encodedAdminPassword);
        LocalDateTime now = LocalDateTime.now();
        List<ModuleDefinition> provisioningModules = provisioningModules();
        for (ModuleDefinition module : provisioningModules) {
            TenantModuleProvisionStatusEnum status = TenantProvisioningMode.LOCAL == module.tenantProvisioning()
                    ? TenantModuleProvisionStatusEnum.SUCCESS
                    : TenantModuleProvisionStatusEnum.PENDING;
            upsertModuleState(tenant.getId(), requestId, module.id(), status, now);
        }

        List<String> eventModuleIds = eventModuleIds();
        if (eventModuleIds.isEmpty()) {
            tenant.setProvisioningStatus(TenantProvisionStatusEnum.ACTIVE);
            tenantMapper.updateById(tenant);
            return;
        }
        publishRequest(tenant, requestId, eventModuleIds);
        log.info("租户模块化初始化已启动: tenantId={}, requestId={}, modules={}",
                tenant.getId(), requestId, eventModuleIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void handleResult(ProvisionResultEvent event) {
        validateResult(event);
        SysTenant tenant = tenantMapper.selectByIdForUpdate(event.tenantId());
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }
        if (!event.requestId().equals(tenant.getProvisioningRequestId())) {
            log.info("忽略过期租户初始化结果: tenantId={}, moduleId={}, requestId={}",
                    event.tenantId(), event.moduleId(), event.requestId());
            return;
        }
        SysTenantModuleProvision state = requireCurrentState(event);
        TenantModuleProvisionStatusEnum targetStatus = ModuleResultStatus.SUCCESS == event.status()
                ? TenantModuleProvisionStatusEnum.SUCCESS
                : TenantModuleProvisionStatusEnum.FAILED;
        state.setStatus(targetStatus);
        state.setErrorCode(targetStatus == TenantModuleProvisionStatusEnum.FAILED
                ? normalizeErrorCode(event.errorCode()) : null);
        state.setErrorMessage(targetStatus == TenantModuleProvisionStatusEnum.FAILED
                ? sanitizeError(event.errorMessage()) : null);
        state.setCompletedTime(LocalDateTime.now());
        moduleProvisionMapper.updateById(state);
        aggregateTenantStatus(tenant);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void retryFailedModules(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("租户 ID 无效");
        }
        SysTenant tenant = tenantMapper.selectByIdForUpdate(tenantId);
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }
        if (TenantProvisionStatusEnum.FAILED != tenant.getProvisioningStatus()) {
            throw new BusinessException("只有初始化失败的租户可以重试");
        }
        List<SysTenantModuleProvision> states = listStates(tenantId);
        if (states.stream().anyMatch(state -> TenantModuleProvisionStatusEnum.PENDING == state.getStatus())) {
            throw new BusinessException("仍有模块正在初始化，请等待其返回结果后重试");
        }
        List<String> failedModuleIds = states.stream()
                .filter(state -> TenantModuleProvisionStatusEnum.FAILED == state.getStatus())
                .map(SysTenantModuleProvision::getModuleId)
                .filter(this::isEventModule)
                .toList();
        if (failedModuleIds.isEmpty()) {
            throw new BusinessException("没有可重试的失败模块");
        }

        String requestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        for (SysTenantModuleProvision state : states) {
            state.setRequestId(requestId);
            if (failedModuleIds.contains(state.getModuleId())) {
                state.setStatus(TenantModuleProvisionStatusEnum.PENDING);
                state.setAttemptCount(state.getAttemptCount() + 1);
                state.setErrorCode(null);
                state.setErrorMessage(null);
                state.setStartedTime(now);
                state.setCompletedTime(null);
            }
            moduleProvisionMapper.updateById(state);
        }
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
        tenant.setProvisioningRequestId(requestId);
        tenant.setProvisioningError(null);
        tenantMapper.updateById(tenant);
        publishRequest(tenant, requestId, failedModuleIds);
        log.info("租户初始化失败模块已重试: tenantId={}, requestId={}, modules={}",
                tenantId, requestId, failedModuleIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SysTenantModuleProvision> listModuleStates(Long tenantId) {
        if (tenantId == null || tenantId <= 0 || tenantMapper.selectById(tenantId) == null) {
            throw new BusinessException(404, "租户不存在");
        }
        return listStates(tenantId);
    }

    /**
     * 新建或重置单个模块状态。
     */
    private void upsertModuleState(
            Long tenantId,
            String requestId,
            String moduleId,
            TenantModuleProvisionStatusEnum status,
            LocalDateTime now) {
        SysTenantModuleProvision state = moduleProvisionMapper.selectOne(
                new LambdaQueryWrapper<SysTenantModuleProvision>()
                        .eq(SysTenantModuleProvision::getTenantId, tenantId)
                        .eq(SysTenantModuleProvision::getModuleId, moduleId));
        if (state == null) {
            state = new SysTenantModuleProvision();
            state.setTenantId(tenantId);
            state.setModuleId(moduleId);
            state.setAttemptCount(1);
            state.setCreateTime(now);
            state.setVersion(0);
        } else {
            state.setAttemptCount(state.getAttemptCount() + 1);
        }
        state.setRequestId(requestId);
        state.setStatus(status);
        state.setErrorCode(null);
        state.setErrorMessage(null);
        state.setStartedTime(now);
        state.setCompletedTime(TenantModuleProvisionStatusEnum.SUCCESS == status ? now : null);
        state.setUpdateTime(now);
        if (state.getId() == null) {
            moduleProvisionMapper.insert(state);
        } else {
            moduleProvisionMapper.updateById(state);
        }
    }

    /**
     * 读取并验证当前请求的模块状态。
     */
    private SysTenantModuleProvision requireCurrentState(ProvisionResultEvent event) {
        SysTenantModuleProvision state = moduleProvisionMapper.selectOne(
                new LambdaQueryWrapper<SysTenantModuleProvision>()
                        .eq(SysTenantModuleProvision::getTenantId, event.tenantId())
                        .eq(SysTenantModuleProvision::getModuleId, event.moduleId()));
        if (state == null || !event.requestId().equals(state.getRequestId()) || !isEventModule(event.moduleId())) {
            throw new BusinessException("租户初始化结果不属于当前模块请求");
        }
        return state;
    }

    /**
     * 汇总模块终态并更新租户。
     */
    private void aggregateTenantStatus(SysTenant tenant) {
        List<SysTenantModuleProvision> states = listStates(tenant.getId());
        SysTenantModuleProvision failed = states.stream()
                .filter(state -> TenantModuleProvisionStatusEnum.FAILED == state.getStatus())
                .findFirst()
                .orElse(null);
        if (failed != null) {
            tenant.setProvisioningStatus(TenantProvisionStatusEnum.FAILED);
            tenant.setProvisioningError(sanitizeError(
                    failed.getModuleId() + ": " + String.valueOf(failed.getErrorMessage())));
        } else if (!states.isEmpty()
                && states.stream().allMatch(state -> TenantModuleProvisionStatusEnum.SUCCESS == state.getStatus())) {
            tenant.setProvisioningStatus(TenantProvisionStatusEnum.ACTIVE);
            tenant.setProvisioningError(null);
        } else {
            tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
            tenant.setProvisioningError(null);
        }
        tenantMapper.updateById(tenant);
    }

    /**
     * 查询租户全部模块状态。
     */
    private List<SysTenantModuleProvision> listStates(Long tenantId) {
        return moduleProvisionMapper.selectList(
                new LambdaQueryWrapper<SysTenantModuleProvision>()
                        .eq(SysTenantModuleProvision::getTenantId, tenantId)
                        .orderByAsc(SysTenantModuleProvision::getId));
    }

    /**
     * 写入不含管理员凭据的初始化请求 Outbox。
     */
    private void publishRequest(SysTenant tenant, String requestId, List<String> moduleIds) {
        String eventId = deterministicEventId(requestId);
        ProvisionRequestedEvent event = new ProvisionRequestedEvent(
                eventId,
                requestId,
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getTenantName(),
                moduleIds,
                Instant.now());
        reliableMessageRelay.send(REQUEST_BINDING, event, tenant.getId(), eventId);
    }

    /**
     * 返回需要状态追踪的本地和事件模块。
     */
    private List<ModuleDefinition> provisioningModules() {
        return moduleCatalogLoader.catalog().modules().stream()
                .filter(module -> TenantProvisioningMode.NONE != module.tenantProvisioning())
                .toList();
    }

    /**
     * 返回需要跨服务事件初始化的模块 ID。
     */
    private List<String> eventModuleIds() {
        return moduleCatalogLoader.catalog().eventProvisioningModuleIds();
    }

    /**
     * 判断模块是否是当前目录中的事件模块。
     */
    private boolean isEventModule(String moduleId) {
        return eventModuleIds().contains(moduleId);
    }

    /**
     * 校验新租户初始化输入。
     */
    private static void requirePersistedTenant(SysTenant tenant) {
        if (tenant == null || tenant.getId() == null || tenant.getId() <= 0) {
            throw new BusinessException("租户必须先持久化再开始初始化");
        }
        if (!StringUtils.hasText(tenant.getTenantCode()) || !StringUtils.hasText(tenant.getTenantName())) {
            throw new BusinessException("租户编码和名称不能为空");
        }
    }

    /**
     * 校验模块结果事件。
     */
    private static void validateResult(ProvisionResultEvent event) {
        if (event == null || event.tenantId() == null || event.tenantId() <= 0
                || !StringUtils.hasText(event.requestId()) || !StringUtils.hasText(event.moduleId())
                || event.status() == null) {
            throw new BusinessException("租户初始化结果事件无效");
        }
    }

    /**
     * 归一稳定错误码。
     */
    private static String normalizeErrorCode(String errorCode) {
        String normalized = StringUtils.hasText(errorCode) ? errorCode.trim() : "MODULE_PROVISION_FAILED";
        normalized = normalized.replaceAll("[^A-Za-z0-9_:-]", "_");
        return truncate(normalized, MAX_ERROR_CODE_LENGTH);
    }

    /**
     * 脱敏并截断错误摘要。
     */
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
        return truncate(sanitized, MAX_ERROR_LENGTH);
    }

    /**
     * 按最大长度截断文本。
     */
    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 根据请求 ID 生成确定性事件 ID，保证同一请求重放使用同一业务键。
     */
    private static String deterministicEventId(String requestId) {
        String source = TenantProvisionContracts.REQUESTED_EVENT_TYPE + ":" + requestId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
