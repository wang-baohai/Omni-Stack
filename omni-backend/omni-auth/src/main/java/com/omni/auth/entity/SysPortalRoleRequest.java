package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门户角色分配请求 Inbox 实体。
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("sys_portal_role_request")
public class SysPortalRoleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户 ID。 */
    private Long tenantId;
    /** Saga 幂等请求 ID。 */
    private String requestId;
    /** 供应商 ID。 */
    private Long supplierId;
    /** Auth 用户 ID。 */
    private Long userId;
    /** 待分配角色编码。 */
    private String roleCode;
    /** PROCESSING/COMPLETED/FAILED。 */
    private String status;
    /** 最近错误码。 */
    private String errorCode;
    /** 乐观锁版本。 */
    @Version
    private Integer version;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
