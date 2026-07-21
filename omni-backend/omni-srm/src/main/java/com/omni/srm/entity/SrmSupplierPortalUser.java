package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * SRM 门户用户实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_portal_user")
public class SrmSupplierPortalUser extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 用户 ID */ private Long userId;
    /** 关联状态 ACTIVE/INACTIVE */ private String status;
    /** 最近登录时间 */ private LocalDateTime lastLoginTime;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
