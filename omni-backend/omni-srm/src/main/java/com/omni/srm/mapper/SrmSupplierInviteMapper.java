package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.entity.SrmSupplierInvite;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SRM 供应商邀请 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmSupplierInviteMapper extends BaseMapper<SrmSupplierInvite> {

    /**
     * 以版本和剩余次数为条件原子消费一次邀请。
     *
     * @param id 邀请 ID
     * @param tenantId 租户 ID
     * @param version 当前版本
     * @param now 当前时间
     * @param updateBy 更新人
     * @return 受影响行数
     */
    @Update("UPDATE srm_supplier_invite "
            + "SET status = CASE WHEN max_uses IS NOT NULL AND used_count + 1 >= max_uses "
            + "THEN 'USED' ELSE status END, used_count = used_count + 1, version = version + 1, "
            + "update_time = #{now}, update_by = #{updateBy} "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND version = #{version} "
            + "AND deleted = 0 AND status = 'ACTIVE' "
            + "AND (expires_time IS NULL OR expires_time > #{now}) "
            + "AND (max_uses IS NULL OR used_count < max_uses)")
    int consume(@Param("id") Long id,
                @Param("tenantId") Long tenantId,
                @Param("version") Integer version,
                @Param("now") LocalDateTime now,
                @Param("updateBy") String updateBy);

    /**
     * 以版本条件撤销活跃邀请。
     *
     * @param id 邀请 ID
     * @param tenantId 租户 ID
     * @param version 当前版本
     * @param now 当前时间
     * @param updateBy 更新人
     * @return 受影响行数
     */
    @Update("UPDATE srm_supplier_invite "
            + "SET status = 'REVOKED', version = version + 1, "
            + "update_time = #{now}, update_by = #{updateBy} "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND version = #{version} "
            + "AND deleted = 0 AND status = 'ACTIVE'")
    int revoke(@Param("id") Long id,
               @Param("tenantId") Long tenantId,
               @Param("version") Integer version,
               @Param("now") LocalDateTime now,
               @Param("updateBy") String updateBy);
}
