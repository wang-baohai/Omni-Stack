package com.omni.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.omni.crm.entity.CrmContact;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** CRM 联系人 Mapper。 */
public interface CrmContactMapper extends BaseMapper<CrmContact> {

    /** 在已校验客户访问权后清除主要联系人，不受旧 owner 快照影响。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_contact SET primary_flag = 0, version = version + 1, update_time = #{now}, update_by = #{operator} "
            + "WHERE customer_id = #{customerId} AND primary_flag = 1 AND deleted = 0")
    int clearPrimaryByCustomer(@Param("customerId") Long customerId, @Param("now") LocalDateTime now,
                               @Param("operator") String operator);

    /** 在已校验客户转移权后同步全部联系人 owner 快照。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_contact SET owner_user_id = #{ownerUserId}, owner_unit_id = #{ownerUnitId}, "
            + "version = version + 1, update_time = #{now}, update_by = #{operator} "
            + "WHERE customer_id = #{customerId} AND deleted = 0")
    int syncOwnerByCustomer(@Param("customerId") Long customerId, @Param("ownerUserId") Long ownerUserId,
                            @Param("ownerUnitId") Long ownerUnitId, @Param("now") LocalDateTime now,
                            @Param("operator") String operator);

    /** 在客户删除后软删除其联系人，客户权限必须在调用前完成校验。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_contact SET deleted = 1, primary_flag = 0, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE customer_id = #{customerId} AND deleted = 0")
    int softDeleteByCustomer(@Param("customerId") Long customerId, @Param("now") LocalDateTime now,
                             @Param("operator") String operator);
}
