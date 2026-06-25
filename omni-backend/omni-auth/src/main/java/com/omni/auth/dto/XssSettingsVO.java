package com.omni.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * XSS 防护设置视图对象，用于 {@code GET /api/auth/xss/settings} 接口响应。
 * <p>包含 XSS 防护开关状态和所有黑名单规则列表，供前端 XSS 管理页面渲染。</p>
 *
 * @author Omni-Stack Team
 * @see BlacklistRuleVO
 * @see com.omni.auth.entity.SysXssConfig
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
