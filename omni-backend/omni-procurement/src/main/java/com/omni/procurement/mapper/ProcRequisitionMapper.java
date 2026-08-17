package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.omni.procurement.entity.ProcRequisition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 请购申请 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcRequisitionMapper extends BaseMapper<ProcRequisition> {

    /**
     * 按租户和主键锁定请购申请。
     *
     * @param tenantId 租户 ID
     * @param id 请购申请 ID
     * @return 已锁定的请购申请
     */
    @Select("SELECT * FROM proc_requisition WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0 FOR UPDATE")
    ProcRequisition selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 仅为审批任务资格校验读取当前 Workflow 关联标识。
     * <p>该查询显式保留 tenant 条件，只忽略普通申请人数据范围；调用方在读取完整业务视图前
     * 必须先向 Workflow 校验 taskId 与当前用户的任务分配关系。</p>
     *
     * @param tenantId 租户 ID
     * @param id 请购申请 ID
     * @return Workflow 关联标识
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT id, tenant_id, status, workflow_start_status, workflow_business_key, "
            + "process_instance_id FROM proc_requisition WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0")
    ProcRequisition selectWorkflowIdentity(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
