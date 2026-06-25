package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建 XSS 黑名单规则请求 DTO。
 * <p>用于新增 XSS 过滤规则，规则名称和正则表达式为必填项。</p>
 *
 * @author Omni-Stack Team
 * @see UpdateXssRuleRequest
 * @see com.omni.auth.entity.SysXssBlacklistRule
 */
@Data
public class CreateXssRuleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    /** 规则类型 */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /** 匹配正则表达式 */
    @NotBlank(message = "匹配规则不能为空")
    private String pattern;

    /** 规则描述 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;
}
