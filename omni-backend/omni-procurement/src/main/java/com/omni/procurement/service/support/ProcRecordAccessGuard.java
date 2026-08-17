package com.omni.procurement.service.support;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcOwnedEntity;
import org.springframework.stereotype.Component;

/**
 * 采购详情与写命令统一行级访问守卫基础。
 * <p>业务服务必须先执行受 TenantLine 和 DataPermission 保护的查询，
 * 再通过本守卫将不可见记录统一转换为 404，避免泄露记录是否存在。</p>
 *
 * @author Omni-Stack Team
 */
@Component
public class ProcRecordAccessGuard {

    /**
     * 要求查询结果可见且存在。
     *
     * @param value 受数据权限保护的查询结果
     * @param message 不存在提示
     * @param <T> 结果类型
     * @return 原查询结果
     */
    public <T> T requireVisible(T value, String message) {
        if (value == null) {
            throw new BusinessException(404, message);
        }
        return value;
    }

    /**
     * 要求条件写入实际命中记录。
     *
     * @param affected 受影响行数
     * @param message 未命中提示
     */
    public void requireAffected(int affected, String message) {
        if (affected != 1) {
            throw new BusinessException(409, message);
        }
    }

    /**
     * 获取授权实体的负责人快照。
     *
     * @param entity 授权实体
     * @return 负责人快照
     */
    public Owner ownerOf(ProcOwnedEntity entity) {
        return new Owner(entity.getOwnerUserId(), entity.getOwnerUnitId());
    }

    /**
     * 负责人不可变快照。
     *
     * @param userId 用户 ID
     * @param unitId 组织 ID
     */
    public record Owner(Long userId, Long unitId) {
    }
}
