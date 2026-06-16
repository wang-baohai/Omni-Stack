package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新 XSS 黑名单规则请求 DTO，所有字段可选（null 表示不修改）。
 */
@Data
public class UpdateXssRuleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 匹配正则表达式 */
    private String pattern;

    /** 规则描述 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;

    /** 是否启用（1-启用，0-禁用） */
    private Integer enabled;
}
