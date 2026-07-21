package com.omni.auth.service;

import com.omni.auth.dto.PortalRoleAssignmentCommand;
import com.omni.auth.dto.PortalRoleAssignmentResult;

/**
 * 门户角色分配服务。
 *
 * @author Omni-Stack Team
 */
public interface PortalRoleAssignmentService {

    /**
     * 按 requestId 幂等分配 SUPPLIER 角色。
     *
     * @param command 分配命令
     * @return 分配结果
     */
    PortalRoleAssignmentResult assign(PortalRoleAssignmentCommand command);
}
