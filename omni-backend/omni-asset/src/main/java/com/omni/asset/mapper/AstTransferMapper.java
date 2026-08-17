package com.omni.asset.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.asset.entity.AstTransfer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 资产调拨 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface AstTransferMapper extends BaseMapper<AstTransfer> {

    /**
     * 锁定当前数据范围内的调拨申请。
     *
     * @param tenantId 租户 ID
     * @param id 申请 ID
     * @return 已锁定申请
     */
    @Select("SELECT * FROM ast_transfer WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0 FOR UPDATE")
    AstTransfer selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 审批视图资格校验前仅读取 Workflow 关联标识。
     *
     * @param tenantId 租户 ID
     * @param id 申请 ID
     * @return Workflow 身份快照
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT id, tenant_id, asset_id, status, workflow_start_status, workflow_business_key, "
            + "process_instance_id FROM ast_transfer WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0")
    AstTransfer selectWorkflowIdentity(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 插入后设置调拨单号，不改变业务版本。
     *
     * @param tenantId 租户 ID
     * @param id 申请 ID
     * @param transferNo 调拨单号
     * @return 受影响行数
     */
    @Update("UPDATE ast_transfer SET transfer_no = #{transferNo} WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0")
    int setTransferNoAfterInsert(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                 @Param("transferNo") String transferNo);
}
