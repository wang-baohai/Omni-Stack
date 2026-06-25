package com.omni.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * XSS 黑名单规则视图对象，用于规则详情展示。
 * <p>作为 {@link XssSettingsVO} 的内嵌对象，包含单条 XSS 过滤规则的完整信息。</p>
 *
 * @author Omni-Stack Team
 * @see XssSettingsVO
 * @see com.omni.auth.entity.SysXssBlacklistRule
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则 ID */
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 匹配正则表达式 */
    private String pattern;

    /** 是否启用（1-启用，0-禁用） */
    private Integer enabled;

    /** 规则描述 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;
}
