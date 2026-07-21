package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.entity.SrmSupplierContact;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SRM 供应商联系人 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmSupplierContactMapper extends BaseMapper<SrmSupplierContact> {

    /** 在供应商删除后软删除其联系人。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE srm_supplier_contact SET deleted = 1, primary_flag = 0, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE supplier_id = #{supplierId} AND deleted = 0")
    int softDeleteBySupplier(@Param("supplierId") Long supplierId, @Param("now") LocalDateTime now,
                             @Param("operator") String operator);
}
