package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.entity.SrmSupplierBankAccount;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SRM 供应商银行账户 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmSupplierBankAccountMapper extends BaseMapper<SrmSupplierBankAccount> {

    /** 在供应商删除后软删除其银行账户。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE srm_supplier_bank_account SET deleted = 1, primary_flag = 0, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE supplier_id = #{supplierId} AND deleted = 0")
    int softDeleteBySupplier(@Param("supplierId") Long supplierId, @Param("now") LocalDateTime now,
                             @Param("operator") String operator);
}
