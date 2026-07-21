package com.omni.srm.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Auth 返回的门户角色分配结果事件。
 *
 * @author Omni-Stack Team
 */
@Data
public class PortalRoleResultEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件 ID。 */
    private String eventId;
    /** 事件类型。 */
    private String eventType;
    /** Saga 幂等请求 ID。 */
    private String requestId;
    /** 租户 ID。 */
    private Long tenantId;
    /** 供应商 ID。 */
    private Long supplierId;
    /** Auth 用户 ID。 */
    private Long userId;
    /** 角色编码。 */
    private String roleCode;
    /** SUCCESS/FAILED。 */
    private String result;
    /** 失败错误码。 */
    private String errorCode;
}
