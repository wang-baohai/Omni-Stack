package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * SRM 门户入驻记录实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_enrollment")
public class SrmSupplierEnrollment extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 幂等请求 ID */ private String requestId;
    /** 发起入驻的 Auth 用户 ID */ private Long userId;
    /** 邀请 ID */ private Long inviteId;
    /** 状态 PENDING_ROLE_ASSIGN/ROLE_ASSIGN_FAILED/COMPLETED/CANCELLED */ private String status;
    /** 已发起的重试次数 */ private Integer retryCount;
    /** 最近一次失败错误码 */ private String lastErrorCode;
    /** 建议下次重试时间 */ private LocalDateTime nextRetryTime;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
