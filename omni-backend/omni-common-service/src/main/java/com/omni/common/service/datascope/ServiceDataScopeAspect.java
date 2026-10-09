package com.omni.common.service.datascope;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * 在业务方法执行期间绑定权威数据范围，并在 finally 中清理。
 *
 * @author Omni-Stack Team
 */
@Aspect
@Order(300)
@RequiredArgsConstructor
public class ServiceDataScopeAspect {

    private final DataScopeResolver dataScopeResolver;

    /**
     * 解析并绑定当前操作的数据范围。
     *
     * @param joinPoint 连接点
     * @param annotation 数据范围注解
     * @return 方法结果
     * @throws Throwable 业务异常
     */
    @Around("@annotation(annotation)")
    public Object bindScope(ProceedingJoinPoint joinPoint, ServiceDataScope annotation) throws Throwable {
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
    }

    private void validate(InternalDataScopeDTO scope, ServiceRequestIdentity identity, String permissionCode) {
        if (scope == null
                || !identity.userId().equals(scope.getUserId())
                || !identity.tenantId().equals(scope.getTenantId())
                || !permissionCode.equals(scope.getPermissionCode())
                || scope.getEffectiveScope() == null) {
            throw new BusinessException(403, "权限服务返回了不一致的数据范围");
        }
    }
}
