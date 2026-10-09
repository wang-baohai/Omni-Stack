package com.omni.auth.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 租户模块初始化状态实体，幂等事实键为 {@code tenantId + moduleId}。
 */
@Data
@TableName("sys_tenant_module_provision")
public class SysTenantModuleProvision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户 ID。 */
    private Long tenantId;
    /** 最近一次初始化请求 ID。 */
    private String requestId;
    /** 脚手架模块 ID。 */
    private String moduleId;
    /** 当前模块初始化状态。 */
    private TenantModuleProvisionStatusEnum status;
    /** 已处理次数。 */
    private Integer attemptCount;
    /** 稳定错误码。 */
    private String errorCode;
    /** 脱敏错误摘要。 */
    private String errorMessage;
    /** 最近开始时间。 */
    private LocalDateTime startedTime;
    /** 最近完成时间。 */
    private LocalDateTime completedTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
    /** 乐观锁版本。 */
    @Version
    private Integer version;
}
