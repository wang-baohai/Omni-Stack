package com.omni.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * XSS 防护设置视图对象，用于 GET /settings 接口响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XssSettingsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否启用 XSS 防护 */
    private boolean enabled;

    /** 黑名单规则列表 */
    private List<BlacklistRuleVO> rules;
}
