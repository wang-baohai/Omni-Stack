package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户选项 DTO，用于登录页租户选择器下拉框。
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
