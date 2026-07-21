package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.auth.dto.PortalRoleAssignmentCommand;
import com.omni.auth.dto.PortalRoleAssignmentResult;
import com.omni.auth.entity.SysPortalRoleRequest;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysPortalRoleRequestMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.service.PortalRoleAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 门户角色分配服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalRoleAssignmentServiceImpl implements PortalRoleAssignmentService {

    private static final String PORTAL_ROLE_CODE = "SUPPLIER";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ASSIGNED_EVENT = "auth.portal-role.assigned.v1";
    private static final String FAILED_EVENT = "auth.portal-role.assign-failed.v1";

    private final SysPortalRoleRequestMapper requestMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PortalRoleAssignmentResult assign(PortalRoleAssignmentCommand command) {
        validateCommand(command);
        SysPortalRoleRequest receipt = requestMapper.selectOne(
                new LambdaQueryWrapper<SysPortalRoleRequest>()
                        .eq(SysPortalRoleRequest::getTenantId, command.getTenantId())
                        .eq(SysPortalRoleRequest::getRequestId, command.getRequestId()));
        if (receipt != null) {
            requireSameCommand(receipt, command);
            if (STATUS_COMPLETED.equals(receipt.getStatus())) {
                PortalRoleAssignmentResult result = success();
                publishResult(command, result);
                return result;
            }
            markProcessing(receipt);
        } else {
            receipt = createReceipt(command);
        }

        if (!PORTAL_ROLE_CODE.equals(command.getRoleCode())) {
            return fail(receipt, command, "ROLE_NOT_ALLOWED");
        }
        SysUser user = userMapper.selectEnabledByIdAndTenantId(command.getUserId(), command.getTenantId());
        if (user == null) {
            return fail(receipt, command, "USER_NOT_FOUND_OR_DISABLED");
        }
        SysRole role = roleMapper.selectByTenantIdAndRoleCode(command.getTenantId(), PORTAL_ROLE_CODE);
        if (role == null) {
            return fail(receipt, command, "ROLE_NOT_FOUND_OR_DISABLED");
        }

        userRoleMapper.insertIgnore(command.getUserId(), role.getId());
        markCompleted(receipt);
        log.info("Auth 门户角色分配完成: tenantId={}, userId={}, requestId={}",
                command.getTenantId(), command.getUserId(), command.getRequestId());
        PortalRoleAssignmentResult result = success();
        publishResult(command, result);
        return result;
    }

    private SysPortalRoleRequest createReceipt(PortalRoleAssignmentCommand command) {
        LocalDateTime now = LocalDateTime.now();
        SysPortalRoleRequest receipt = new SysPortalRoleRequest();
        receipt.setTenantId(command.getTenantId());
        receipt.setRequestId(command.getRequestId());
        receipt.setSupplierId(command.getSupplierId());
        receipt.setUserId(command.getUserId());
        receipt.setRoleCode(command.getRoleCode());
        receipt.setStatus(STATUS_PROCESSING);
        receipt.setVersion(0);
        receipt.setCreateTime(now);
        receipt.setUpdateTime(now);
        try {
            requestMapper.insert(receipt);
        } catch (DuplicateKeyException exception) {
            // 同一 requestId 的并发消费者由唯一键仲裁；抛出后由 MQ 重投并读取已落库回执。
            throw new IllegalStateException("门户角色请求正在并发处理，请重试", exception);
        }
        return receipt;
    }

    private void validateCommand(PortalRoleAssignmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("门户角色分配命令不能为空");
        }
        if (command.getRequestId() == null || command.getRequestId().isBlank()
                || command.getRequestId().length() > 64) {
            throw new IllegalArgumentException("门户角色分配 requestId 无效");
        }
        if (command.getTenantId() == null || command.getTenantId() <= 0
                || command.getSupplierId() == null || command.getSupplierId() <= 0
                || command.getUserId() == null || command.getUserId() <= 0) {
            throw new IllegalArgumentException("门户角色分配身份 ID 必须为正整数");
        }
        if (command.getRoleCode() == null || command.getRoleCode().isBlank()
                || command.getRoleCode().length() > 50) {
            throw new IllegalArgumentException("门户角色分配 roleCode 无效");
        }
    }

    private void markProcessing(SysPortalRoleRequest receipt) {
        int rows = requestMapper.update(null, new LambdaUpdateWrapper<SysPortalRoleRequest>()
                .eq(SysPortalRoleRequest::getId, receipt.getId())
                .eq(SysPortalRoleRequest::getTenantId, receipt.getTenantId())
                .eq(SysPortalRoleRequest::getVersion, receipt.getVersion())
                .ne(SysPortalRoleRequest::getStatus, STATUS_COMPLETED)
                .set(SysPortalRoleRequest::getStatus, STATUS_PROCESSING)
                .set(SysPortalRoleRequest::getErrorCode, null)
                .set(SysPortalRoleRequest::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new IllegalStateException("门户角色请求已被并发处理");
        }
        receipt.setStatus(STATUS_PROCESSING);
        receipt.setErrorCode(null);
        receipt.setVersion(receipt.getVersion() + 1);
    }

    private void markCompleted(SysPortalRoleRequest receipt) {
        int rows = requestMapper.update(null, new LambdaUpdateWrapper<SysPortalRoleRequest>()
                .eq(SysPortalRoleRequest::getId, receipt.getId())
                .eq(SysPortalRoleRequest::getTenantId, receipt.getTenantId())
                .eq(SysPortalRoleRequest::getVersion, receipt.getVersion())
                .eq(SysPortalRoleRequest::getStatus, STATUS_PROCESSING)
                .set(SysPortalRoleRequest::getStatus, STATUS_COMPLETED)
                .set(SysPortalRoleRequest::getErrorCode, null)
                .set(SysPortalRoleRequest::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new IllegalStateException("门户角色请求完成状态写入失败");
        }
    }

    private PortalRoleAssignmentResult fail(SysPortalRoleRequest receipt,
                                            PortalRoleAssignmentCommand command,
                                            String errorCode) {
        int rows = requestMapper.update(null, new LambdaUpdateWrapper<SysPortalRoleRequest>()
                .eq(SysPortalRoleRequest::getId, receipt.getId())
                .eq(SysPortalRoleRequest::getTenantId, receipt.getTenantId())
                .eq(SysPortalRoleRequest::getVersion, receipt.getVersion())
                .eq(SysPortalRoleRequest::getStatus, STATUS_PROCESSING)
                .set(SysPortalRoleRequest::getStatus, STATUS_FAILED)
                .set(SysPortalRoleRequest::getErrorCode, errorCode)
                .set(SysPortalRoleRequest::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new IllegalStateException("门户角色请求失败状态写入失败");
        }
        log.warn("Auth 门户角色分配被拒绝: requestId={}, errorCode={}", receipt.getRequestId(), errorCode);
        PortalRoleAssignmentResult result = PortalRoleAssignmentResult.builder()
                .success(false)
                .errorCode(errorCode)
                .build();
        publishResult(command, result);
        return result;
    }

    private void requireSameCommand(SysPortalRoleRequest receipt, PortalRoleAssignmentCommand command) {
        if (!receipt.getSupplierId().equals(command.getSupplierId())
                || !receipt.getUserId().equals(command.getUserId())
                || !receipt.getRoleCode().equals(command.getRoleCode())) {
            throw new IllegalArgumentException("同一 requestId 对应的门户角色请求不一致");
        }
    }

    private PortalRoleAssignmentResult success() {
        return PortalRoleAssignmentResult.builder().success(true).build();
    }

    private void publishResult(PortalRoleAssignmentCommand command, PortalRoleAssignmentResult result) {
        String eventType = result.isSuccess() ? ASSIGNED_EVENT : FAILED_EVENT;
        String eventId = deterministicResultEventId(command.getRequestId(), eventType);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", command.getRequestId());
        payload.put("tenantId", command.getTenantId());
        payload.put("supplierId", command.getSupplierId());
        payload.put("userId", command.getUserId());
        payload.put("roleCode", command.getRoleCode());
        payload.put("result", result.isSuccess() ? "SUCCESS" : "FAILED");
        payload.put("errorCode", result.isSuccess() ? "" : result.getErrorCode());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", LocalDateTime.now());
        envelope.put("tenantId", command.getTenantId());
        envelope.put("producer", "omni-auth");
        envelope.put("aggregateType", "SUPPLIER");
        envelope.put("aggregateId", command.getSupplierId());
        envelope.put("actorUserId", command.getUserId());
        envelope.put("correlationId", command.getRequestId());
        envelope.put("payload", payload);
        reliableMessageRelay.send("authPortalRoleResult-out-0", envelope, command.getTenantId(), eventId);
    }

    private String deterministicResultEventId(String requestId, String eventType) {
        String source = "portal-role-result:" + requestId + ":" + eventType;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
