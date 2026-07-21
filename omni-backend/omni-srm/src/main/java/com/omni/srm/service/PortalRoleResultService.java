package com.omni.srm.service;

import com.omni.srm.dto.PortalRoleResultEvent;

/**
 * 门户角色分配结果处理服务。
 *
 * @author Omni-Stack Team
 */
public interface PortalRoleResultService {

    /**
     * 幂等处理 Auth 返回的角色分配结果。
     *
     * @param event 结果事件
     */
    void handle(PortalRoleResultEvent event);
}
