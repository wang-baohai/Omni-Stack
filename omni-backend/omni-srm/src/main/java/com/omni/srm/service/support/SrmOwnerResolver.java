package com.omni.srm.service.support;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.AuthInternalClient;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 负责人权威信息和可分配范围校验器。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class SrmOwnerResolver {

    private final AuthInternalClient authInternalClient;

    /**
     * 解析创建命令负责人，指定其他用户时额外校验 assign/transfer 权限范围。
     *
     * @param requestedUserId 请求负责人，可空
     * @param assignmentPermission 分配权限码
     * @return 负责人快照
     */
    public Owner resolveForCreate(Long requestedUserId, String assignmentPermission) {
        Long currentUserId = SrmTenantContext.require().userId();
        Long target = requestedUserId == null ? currentUserId : requestedUserId;
        if (!currentUserId.equals(target)) {
            requireAuthority(assignmentPermission);
            try {
                R<InternalDataScopeDTO> response = authInternalClient.resolveDataScope(
                        currentUserId, SrmTenantContext.requireTenantId(), assignmentPermission);
                if (response == null || response.getCode() != 200 || response.getData() == null) {
                    int code = response != null && response.getCode() == 403 ? 403 : 503;
                    throw new BusinessException(code, code == 403 ? "负责人分配权限不足" : "权限服务暂时不可用");
                }
                validateScope(response.getData(), assignmentPermission);
                return resolveAndCheck(target, response.getData());
            } catch (FeignException.Forbidden exception) {
                throw new BusinessException(403, "负责人分配权限不足");
            } catch (FeignException exception) {
                throw new BusinessException(503, "权限服务暂时不可用");
            }
        }
        return resolveUser(target);
    }

    /**
     * 在当前命令数据范围内解析负责人。
     *
     * @param targetUserId 目标用户
     * @return 负责人快照
     */
    public Owner resolveForCommand(Long targetUserId) {
        return resolveAndCheck(targetUserId, SrmDataScopeContext.require());
    }

    /**
     * 解析负责人转移命令，始终按转移权限的独立数据范围校验目标用户。
     *
     * @param targetUserId 目标负责人
     * @param transferPermission 转移权限码
     * @return 负责人快照
     */
    public Owner resolveForTransfer(Long targetUserId, String transferPermission) {
        requireAuthority(transferPermission);
        SrmTenantContext.RequestIdentity identity = SrmTenantContext.require();
        try {
            R<InternalDataScopeDTO> response = authInternalClient.resolveDataScope(
                    identity.userId(), identity.tenantId(), transferPermission);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                int code = response != null && response.getCode() == 403 ? 403 : 503;
                throw new BusinessException(code, code == 403 ? "负责人转移权限不足" : "权限服务暂时不可用");
            }
            validateScope(response.getData(), transferPermission);
            return resolveAndCheck(targetUserId, response.getData());
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "负责人转移权限不足");
        } catch (FeignException exception) {
            throw new BusinessException(503, "权限服务暂时不可用");
        }
    }

    private Owner resolveAndCheck(Long targetUserId, InternalDataScopeDTO scope) {
        Owner owner = resolveUser(targetUserId);
        if (!isAllowed(scope.getEffectiveScope(), scope.getUserId(), scope.getPrimaryUnitId(),
                scope.getAccessibleUnitIds(), owner)) {
            throw new BusinessException(403, "目标负责人不在可分配组织范围内");
        }
        return owner;
    }

    private Owner resolveAndCheck(Long targetUserId, SrmDataScopeContext.ScopeInfo scope) {
        Owner owner = resolveUser(targetUserId);
        if (!isAllowed(scope.effectiveScope(), scope.userId(), scope.primaryUnitId(),
                scope.accessibleUnitIds(), owner)) {
            throw new BusinessException(403, "目标负责人不在可分配组织范围内");
        }
        return owner;
    }

    private Owner resolveUser(Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(400, "负责人不能为空");
        }
        R<InternalUserDTO> response;
        try {
            response = authInternalClient.getUser(targetUserId, SrmTenantContext.requireTenantId());
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "无权访问目标负责人");
        } catch (FeignException exception) {
            throw new BusinessException(503, "身份服务暂时不可用");
        }
        InternalUserDTO user = response == null ? null : response.getData();
        if (response == null || response.getCode() != 200 || user == null
                || !SrmTenantContext.requireTenantId().equals(user.getTenantId()) || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(400, "目标负责人不存在、已禁用或不属于当前租户");
        }
        if (user.getPrimaryUnitId() == null) {
            throw new BusinessException(400, "目标负责人未配置主组织");
        }
        return new Owner(user.getId(), user.getPrimaryUnitId());
    }

    private boolean isAllowed(String scope, Long currentUserId, Long primaryUnitId,
                              java.util.Set<Long> accessibleUnits, Owner target) {
        return switch (scope) {
            case "ALL", "TENANT" -> true;
            case "SELF" -> target.userId().equals(currentUserId);
            case "DEPT" -> target.unitId().equals(primaryUnitId);
            case "DEPT_AND_BELOW", "CUSTOM" -> accessibleUnits != null && accessibleUnits.contains(target.unitId());
            default -> false;
        };
    }

    private void requireAuthority(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
        if (!granted) {
            throw new BusinessException(403, "指定其他负责人需要额外权限：" + permission);
        }
    }

    private void validateScope(InternalDataScopeDTO scope, String permission) {
        SrmTenantContext.RequestIdentity identity = SrmTenantContext.require();
        if (!identity.userId().equals(scope.getUserId()) || !identity.tenantId().equals(scope.getTenantId())
                || !permission.equals(scope.getPermissionCode()) || scope.getEffectiveScope() == null) {
            throw new BusinessException(403, "权限服务返回了不一致的负责人分配范围");
        }
    }

    /**
     * 负责人业务归属快照。
     *
     * @param userId 用户 ID
     * @param unitId 组织 ID
     */
    public record Owner(Long userId, Long unitId) {
    }
}
