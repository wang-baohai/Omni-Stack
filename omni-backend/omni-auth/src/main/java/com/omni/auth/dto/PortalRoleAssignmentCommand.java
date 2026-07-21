package com.omni.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * SRM 门户角色分配命令。
 *
 * @author Omni-Stack Team
 */
@Getter
@Builder
public class PortalRoleAssignmentCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 幂等请求 ID。 */
    private final String requestId;
    /** 租户 ID。 */
    private final Long tenantId;
    /** 供应商 ID。 */
    private final Long supplierId;
    /** 用户 ID。 */
    private final Long userId;
    /** 角色编码。 */
    private final String roleCode;
}
