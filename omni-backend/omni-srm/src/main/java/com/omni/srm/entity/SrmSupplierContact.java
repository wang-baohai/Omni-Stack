package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 供应商联系人实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_contact")
public class SrmSupplierContact extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 联系人姓名 */ private String name;
    /** 部门 */ private String department;
    /** 职位 */ private String jobTitle;
    /** 手机 */ private String mobile;
    /** 电话 */ private String phone;
    /** 邮箱 */ private String email;
    /** 决策角色 */ private String decisionRole;
    /** 主要联系人标记 */ private Boolean primaryFlag;
    /** 状态 */ private Integer status;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}
