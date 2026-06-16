package com.omni.common.security.xss;

import com.omni.common.core.security.XssSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * XSS 输入净化工具类。
 * <p>
 * 根据黑名单规则列表对输入字符串执行不同类型的清洗操作，
 * 防止存储型 XSS 攻击。
 * </p>
 */
@Slf4j
public final class XssSanitizer {

    private XssSanitizer() {}

    /** HTML 标签类型 */
    private static final String TYPE_HTML_TAG = "HTML_TAG";
    /** 事件处理器类型 */
    private static final String TYPE_EVENT_HANDLER = "EVENT_HANDLER";
    /** 危险协议类型 */
    private static final String TYPE_DANGEROUS_PROTOCOL = "DANGEROUS_PROTOCOL";
    /** 自定义正则类型 */
    private static final String TYPE_CUSTOM_PATTERN = "CUSTOM_PATTERN";

    /**
     * 根据规则列表清洗输入字符串。
     *
     * @param input 原始输入字符串
     * @param rules 已启用的黑名单规则列表
     * @return 清洗后的字符串；若输入为 null 则返回 null
     */
    public static String sanitize(String input, List<XssSettings.XssRule> rules) {
        if (input == null || input.isEmpty() || rules == null || rules.isEmpty()) {
            return input;
        }
        String result = input;
        for (XssSettings.XssRule rule : rules) {
            try {
                result = applyRule(result, rule);
            } catch (PatternSyntaxException e) {
                log.warn("XSS 规则正则表达式无效: ruleId={}, pattern={}, error={}",
                        rule.getId(), rule.getPattern(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 应用单条规则。
     */
    private static String applyRule(String input, XssSettings.XssRule rule) {
        String type = rule.getRuleType();
        String pattern = rule.getPattern();
        if (type == null || pattern == null) {
            return input;
        }
        return switch (type.toUpperCase()) {
            case TYPE_HTML_TAG -> stripHtmlTag(input, pattern);
            case TYPE_EVENT_HANDLER -> stripEventHandler(input, pattern);
            case TYPE_DANGEROUS_PROTOCOL -> stripProtocol(input, pattern);
            case TYPE_CUSTOM_PATTERN -> stripCustomPattern(input, pattern);
            default -> input;
        };
    }

    /**
     * 剥离指定 HTML 标签及其内容。
     * <p>匹配 {@code <tag...>content</tag>} 和自闭合 {@code <tag.../>} 两种形式。</p>
     */
    private static String stripHtmlTag(String input, String tagName) {
        // 匹配成对标签: <tag ...>...</tag>
        String pairRegex = "<" + Pattern.quote(tagName) + "[^>]*>.*?</" + Pattern.quote(tagName) + "\\s*>";
        String result = Pattern.compile(pairRegex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(input).replaceAll("");
        // 匹配自闭合标签: <tag ... />
        String selfCloseRegex = "<" + Pattern.quote(tagName) + "[^>]*/?>";
        result = Pattern.compile(selfCloseRegex, Pattern.CASE_INSENSITIVE).matcher(result).replaceAll("");
        return result;
    }

    /**
     * 剥离事件处理器属性。
     * <p>匹配 {@code onxxx="..."} 和 {@code onxxx=...} 两种形式。</p>
     */
    private static String stripEventHandler(String input, String pattern) {
        // 匹配 onXXX="value" 或 onXXX='value' 或 onXXX=value
        String regex = pattern + "\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]*)";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(input).replaceAll("");
    }

    /**
     * 剥离危险协议字符串（不区分大小写直接替换）。
     */
    private static String stripProtocol(String input, String protocol) {
        return Pattern.compile(Pattern.quote(protocol), Pattern.CASE_INSENSITIVE)
                .matcher(input).replaceAll("");
    }

    /**
     * 使用自定义正则表达式替换。
     */
    private static String stripCustomPattern(String input, String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(input).replaceAll("");
    }
}
