package com.omni.crm.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.crm.client.AuthInternalClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 按端点完整权限码向 Auth 解析并绑定数据范围。
 *
 * @author Omni-Stack Team
 */
@Aspect
@Component
@Order(300)
@RequiredArgsConstructor
public class CrmDataScopeAspect {

    private final AuthInternalClient authInternalClient;

    /**
     * 在业务方法执行期间设置数据范围并在 finally 中清理。
     *
     * @param joinPoint 连接点
     * @param annotation 数据范围注解
     * @return 方法结果
     * @throws Throwable 业务异常
     */
    @Around("@annotation(annotation)")
    public Object bindScope(ProceedingJoinPoint joinPoint, CrmDataScope annotation) throws Throwable {
        CrmTenantContext.RequestIdentity identity = CrmTenantContext.require();
        try {
            R<InternalDataScopeDTO> response = authInternalClient.resolveDataScope(
                    identity.userId(), identity.tenantId(), annotation.permissionCode());
            InternalDataScopeDTO scope = response == null ? null : response.getData();
            if (response == null || response.getCode() != 200 || scope == null) {
                throw new BusinessException(403, "无法解析当前操作的数据权限");
            }
            validate(scope, identity, annotation.permissionCode());
            CrmDataScopeContext.set(scope);
            return joinPoint.proceed();
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "当前用户不具备该操作的数据权限");
        } catch (FeignException exception) {
            throw new BusinessException(503, "权限服务暂时不可用");
        } finally {
            CrmDataScopeContext.clear();
        }
    }

    private void validate(InternalDataScopeDTO scope, CrmTenantContext.RequestIdentity identity,
                          String permissionCode) {
        if (!identity.userId().equals(scope.getUserId())
                || !identity.tenantId().equals(scope.getTenantId())
                || !permissionCode.equals(scope.getPermissionCode())
                || scope.getEffectiveScope() == null) {
            throw new BusinessException(403, "权限服务返回了不一致的数据范围");
        }
    }
}
