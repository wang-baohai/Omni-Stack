package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 供应商银行账户实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_bank_account")
public class SrmSupplierBankAccount extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 账户名 */ private String accountName;
    /** 账号 */ private String accountNo;
    /** 银行名称 */ private String bankName;
    /** 银行支行 */ private String bankBranch;
    /** 银行编码 */ private String bankCode;
    /** 主要账户标记 */ private Boolean primaryFlag;
    /** 状态 */ private Integer status;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}
