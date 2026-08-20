package com.omni.common.mqlog.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 租户模块初始化消费回执。
 */
@Data
@TableName("sys_tenant_provision_receipt")
public class SysTenantProvisionReceipt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 初始化请求 ID。 */
    private String requestId;
    /** 租户 ID。 */
    private Long tenantId;
    /** 模块 ID。 */
    private String moduleId;
    /** 终态：SUCCESS 或 FAILED。 */
    private String status;
    /** 稳定错误码。 */
    private String errorCode;
    /** 脱敏错误摘要。 */
    private String errorMessage;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
