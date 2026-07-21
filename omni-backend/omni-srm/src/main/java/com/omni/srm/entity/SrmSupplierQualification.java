package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * SRM 供应商资质实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_qualification")
public class SrmSupplierQualification extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 资质名称 */ private String qualificationName;
    /** 证书编号 */ private String certificateNo;
    /** 发证机关 */ private String issuingAuthority;
    /** 发证日期 */ private LocalDate issueDate;
    /** 到期日期 */ private LocalDate expiryDate;
    /** 状态 */ private String status;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}
