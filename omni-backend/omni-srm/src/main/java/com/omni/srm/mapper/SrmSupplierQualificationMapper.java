package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.entity.SrmSupplierQualification;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SRM 供应商资质 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmSupplierQualificationMapper extends BaseMapper<SrmSupplierQualification> {

    /** 在供应商删除后软删除其资质。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE srm_supplier_qualification SET deleted = 1, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE supplier_id = #{supplierId} AND deleted = 0")
    int softDeleteBySupplier(@Param("supplierId") Long supplierId, @Param("now") LocalDateTime now,
                             @Param("operator") String operator);
}
