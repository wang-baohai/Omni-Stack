package com.omni.asset.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.asset.entity.AstDisposal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 资产处置 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface AstDisposalMapper extends BaseMapper<AstDisposal> {

    /**
     * 锁定当前数据范围内的处置申请。
     *
     * @param tenantId 租户 ID
     * @param id 申请 ID
     * @return 已锁定申请
     */
    @Select("SELECT * FROM ast_disposal WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0 FOR UPDATE")
    AstDisposal selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 审批视图资格校验前仅读取 Workflow 关联标识。
     *
     * @param tenantId 租户 ID
     * @param id 申请 ID
     * @return Workflow 身份快照
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT id, tenant_id, asset_id, status, workflow_start_status, workflow_business_key, "
            + "process_instance_id FROM ast_disposal WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0")
    AstDisposal selectWorkflowIdentity(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 插入后设置处置单号，不改变业务版本。
     *
     * @param tenantId 租户 ID
     * @param id 申请 ID
     * @param disposalNo 处置单号
     * @return 受影响行数
     */
    @Update("UPDATE ast_disposal SET disposal_no = #{disposalNo} WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0")
    int setDisposalNoAfterInsert(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                 @Param("disposalNo") String disposalNo);
}
