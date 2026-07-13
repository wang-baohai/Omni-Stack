package com.omni.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.omni.crm.entity.CrmActivity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** CRM 跟进活动 Mapper。 */
public interface CrmActivityMapper extends BaseMapper<CrmActivity> {

    /** 查询已授权访问根对象下最早的待办活动时间。 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT MIN(planned_start_time) FROM crm_activity "
            + "WHERE root_type = #{rootType} AND root_id = #{rootId} "
            + "AND status = 'PLANNED' AND planned_start_time IS NOT NULL AND deleted = 0")
    LocalDateTime selectEarliestPlannedTime(@Param("rootType") String rootType, @Param("rootId") Long rootId);

    /** 查询已授权访问根对象下最近完成活动所约定的下一行动时间。 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT next_action_time FROM crm_activity "
            + "WHERE root_type = #{rootType} AND root_id = #{rootId} AND status = 'COMPLETED' AND deleted = 0 "
            + "ORDER BY completed_time DESC, id DESC LIMIT 1")
    LocalDateTime selectLatestCompletedNextActionTime(@Param("rootType") String rootType,
                                                      @Param("rootId") Long rootId);

    /** 在根聚合授权完成后同步该根全部活动 owner 快照。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_activity SET owner_user_id = #{ownerUserId}, owner_unit_id = #{ownerUnitId}, "
            + "version = version + 1, update_time = #{now}, update_by = #{operator} "
            + "WHERE root_type = #{rootType} AND root_id = #{rootId} AND deleted = 0")
    int syncOwnerByRoot(@Param("rootType") String rootType, @Param("rootId") Long rootId,
                        @Param("ownerUserId") Long ownerUserId, @Param("ownerUnitId") Long ownerUnitId,
                        @Param("now") LocalDateTime now, @Param("operator") String operator);

    /** 同步一组商机根的活动 owner 快照。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update({"<script>",
            "UPDATE crm_activity SET owner_user_id = #{ownerUserId}, owner_unit_id = #{ownerUnitId},",
            "version = version + 1, update_time = #{now}, update_by = #{operator}",
            "WHERE root_type = 'OPPORTUNITY' AND deleted = 0 AND root_id IN",
            "<foreach collection='rootIds' item='rootId' open='(' separator=',' close=')'>#{rootId}</foreach>",
            "</script>"})
    int syncOwnerByOpportunityRoots(@Param("rootIds") List<Long> rootIds,
                                    @Param("ownerUserId") Long ownerUserId,
                                    @Param("ownerUnitId") Long ownerUnitId,
                                    @Param("now") LocalDateTime now,
                                    @Param("operator") String operator);

    /** 在根对象删除后软删除其活动，根对象权限必须在调用前完成校验。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_activity SET deleted = 1, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE root_type = #{rootType} AND root_id = #{rootId} AND deleted = 0")
    int softDeleteByRoot(@Param("rootType") String rootType, @Param("rootId") Long rootId,
                         @Param("now") LocalDateTime now, @Param("operator") String operator);

    /** 在联系人删除后解除活动上的联系人引用。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_activity SET contact_id = NULL, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE contact_id = #{contactId} AND deleted = 0")
    int clearContactReference(@Param("contactId") Long contactId, @Param("now") LocalDateTime now,
                              @Param("operator") String operator);

    /** 在客户删除后解除其联系人在其他根活动上的引用。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_activity SET contact_id = NULL, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE contact_id IN (SELECT id FROM crm_contact WHERE customer_id = #{customerId}) AND deleted = 0")
    int clearContactReferencesByCustomer(@Param("customerId") Long customerId,
                                         @Param("now") LocalDateTime now,
                                         @Param("operator") String operator);
}
