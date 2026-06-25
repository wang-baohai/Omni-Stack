package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户选项 DTO，用于登录页租户选择器下拉框。
 * <p>由 {@code /api/auth/tenants} 接口返回，仅包含租户的基本展示信息。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysTenant
 */
@Data
@Builder
public class TenantOption implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long id;
    /** 租户名称 */
    private String name;
    /** 租户编码 */
    private String code;
}
