package com.omni.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门户角色分配处理结果。
 *
 * @author Omni-Stack Team
 */
@Getter
@Builder
public class PortalRoleAssignmentResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否分配成功。 */
    private final boolean success;
    /** 失败错误码，成功时为空。 */
    private final String errorCode;
}
