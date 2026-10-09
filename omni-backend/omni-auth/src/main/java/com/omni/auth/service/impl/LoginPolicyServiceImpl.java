package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.auth.dto.AuthLoginPolicyVO;
import com.omni.auth.dto.LoginPolicyUpdateRequest;
import com.omni.auth.entity.SysAuthLoginPolicy;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysAuthLoginPolicyMapper;
import com.omni.auth.security.LoginPolicyProvider;
import com.omni.auth.service.AuditLogService;
import com.omni.auth.service.LoginPolicyService;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录保护策略服务实现（D01）。
 * <p>乐观并发：首次 INSERT 依赖 tenant_id 主键判冲突；后续 {@code WHERE tenant_id=? AND version=?}
 * 更新且受影响行必须为 1，否则 code409（陈旧版本，不 last-write-wins、不自动重试覆盖）。
 * 配置与 AUTH_CONFIG_UPDATED 安全审计在同一事务内保存，审计失败回滚配置。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginPolicyServiceImpl implements LoginPolicyService {

    private final SysAuthLoginPolicyMapper policyMapper;
    private final AuditLogService auditLogService;

    /** {@inheritDoc} */
    @Override
    public AuthLoginPolicyVO getPolicy(Long tenantId) {
        SysAuthLoginPolicy row = policyMapper.selectById(tenantId);
        if (row == null) {
            return AuthLoginPolicyVO.builder()
                    .failureThreshold(LoginPolicyProvider.DEFAULT_THRESHOLD)
                    .failureWindowSeconds(LoginPolicyProvider.DEFAULT_WINDOW_SECONDS)
                    .version(0L)
                    .source("DEFAULT")
                    .build();
        }
        return toVO(row);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginPolicyVO updatePolicy(Long tenantId, LoginPolicyUpdateRequest request, String operator) {
        SysAuthLoginPolicy current = policyMapper.selectById(tenantId);
        LocalDateTime now = LocalDateTime.now();

        Integer beforeThreshold;
        Integer beforeWindow;
        long newVersion;

        if (current == null) {
            // 首次创建：期望版本必须为 0
            if (request.getExpectedVersion() != 0L) {
                throw new BusinessException(409, "策略版本冲突，请刷新后重试");
            }
            beforeThreshold = LoginPolicyProvider.DEFAULT_THRESHOLD;
            beforeWindow = LoginPolicyProvider.DEFAULT_WINDOW_SECONDS;
            newVersion = 1L;
            SysAuthLoginPolicy entity = new SysAuthLoginPolicy();
            entity.setTenantId(tenantId);
            entity.setFailureThreshold(request.getFailureThreshold());
            entity.setFailureWindowSeconds(request.getFailureWindowSeconds());
            entity.setVersion(newVersion);
            entity.setCreateBy(operator);
            entity.setUpdateBy(operator);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            try {
                policyMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                // 并发首次插入：唯一 tenant 主键冲突 → 409，不覆盖
                throw new BusinessException(409, "策略已被并发创建，请刷新后重试");
            }
        } else {
            if (!current.getVersion().equals(request.getExpectedVersion())) {
                throw new BusinessException(409, "策略版本冲突，请刷新后重试");
            }
            beforeThreshold = current.getFailureThreshold();
            beforeWindow = current.getFailureWindowSeconds();
            newVersion = current.getVersion() + 1;
            LambdaUpdateWrapper<SysAuthLoginPolicy> wrapper = new LambdaUpdateWrapper<SysAuthLoginPolicy>()
                    .eq(SysAuthLoginPolicy::getTenantId, tenantId)
                    .eq(SysAuthLoginPolicy::getVersion, request.getExpectedVersion())
                    .set(SysAuthLoginPolicy::getFailureThreshold, request.getFailureThreshold())
                    .set(SysAuthLoginPolicy::getFailureWindowSeconds, request.getFailureWindowSeconds())
                    .set(SysAuthLoginPolicy::getVersion, newVersion)
                    .set(SysAuthLoginPolicy::getUpdateBy, operator)
                    .set(SysAuthLoginPolicy::getUpdateTime, now);
            int affected = policyMapper.update(null, wrapper);
            if (affected != 1) {
                throw new BusinessException(409, "策略版本冲突，请刷新后重试");
            }
        }

        // 同一事务写入非秘密安全审计；审计失败将回滚上面的配置写入
        auditLogService.save(AuditEvent.of(AuditEvent.AUTH_CONFIG_UPDATED)
                .tenantId(tenantId)
                .description("更新登录保护策略")
                .createBy(operator)
                .extra("before_threshold", beforeThreshold)
                .extra("before_window_seconds", beforeWindow)
                .extra("after_threshold", request.getFailureThreshold())
                .extra("after_window_seconds", request.getFailureWindowSeconds())
                .extra("policy_version", newVersion)
                .build());

        SysAuthLoginPolicy fresh = policyMapper.selectById(tenantId);
        log.info("登录保护策略已更新: tenantId={}, version={}, operator={}", tenantId, newVersion, operator);
        return toVO(fresh);
    }

    private AuthLoginPolicyVO toVO(SysAuthLoginPolicy row) {
        return AuthLoginPolicyVO.builder()
                .failureThreshold(row.getFailureThreshold())
                .failureWindowSeconds(row.getFailureWindowSeconds())
                .version(row.getVersion())
                .source("TENANT_OVERRIDE")
                .updateTime(row.getUpdateTime())
                .updatedBy(row.getUpdateBy())
                .build();
    }
}
