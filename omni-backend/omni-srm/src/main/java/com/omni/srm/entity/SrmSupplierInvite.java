package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * SRM 供应商邀请实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_invite")
public class SrmSupplierInvite extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 邀请码 SHA-256 哈希 */ private String inviteCodeHash;
    /** 状态 ACTIVE/REVOKED/EXPIRED */ private String status;
    /** 过期时间 */ private LocalDateTime expiresTime;
    /** 最大使用次数 */ private Integer maxUses;
    /** 已使用次数 */ private Integer usedCount;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
