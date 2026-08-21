package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcApprovalRoute;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购审批路由 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcApprovalRouteMapper extends BaseMapper<ProcApprovalRoute> {

    /**
     * 查询租户指定品类的最大管理排序值。
     *
     * @param tenantId 租户 ID
     * @param categoryCode 品类编码
     * @return 当前最大排序值，无记录时为 0
     */
    @Select("SELECT COALESCE(MAX(priority), 0) FROM proc_approval_route "
            + "WHERE tenant_id = #{tenantId} AND category_code = #{categoryCode} AND deleted = 0")
    Integer selectMaxPriority(@Param("tenantId") Long tenantId,
                              @Param("categoryCode") String categoryCode);

    /**
     * 锁定租户配置行，串行化同租户审批路由的区间校验与写入。
     *
     * @param tenantId 租户 ID
     * @return 被锁定的租户配置 ID
     */
    @Select("SELECT id FROM proc_tenant_config "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 LIMIT 1 FOR UPDATE")
    @InterceptorIgnore(tenantLine = "true")
    Long lockTenantConfig(@Param("tenantId") Long tenantId);
}
