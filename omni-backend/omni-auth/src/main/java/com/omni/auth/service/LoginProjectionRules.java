package com.omni.auth.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 登录/审计安全投影共享规则（D02 / RF01 / RF02）。
 * <p>供登录记录投影与旧审计入口共用同一套必要脱敏与 method 归类规则，避免新页保护被旧入口绕过：
 * 可靠解析 IP 文字地址（不触发 DNS）后按合同掩码，UA 仅保留白名单类别与平台，
 * method 归类与 SQL 筛选保持一致（provider 白名单优先，缺/未知 method 归 UNKNOWN）。</p>
 *
 * @author Omni-Stack Team
 */
public final class LoginProjectionRules {

    /** 合法社交登录 provider 白名单（小写）。 */
    public static final Set<String> PROVIDER_WHITELIST = Set.of("github", "google", "gitee");

    /** SQL 端 provider 白名单字面量（小写），与 {@link #PROVIDER_WHITELIST} 单一来源保持一致。 */
    public static final String PROVIDER_SQL_LIST = "'github','google','gitee'";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final Pattern HEX_GROUP = Pattern.compile("^[0-9a-fA-F]{1,4}$");

    private LoginProjectionRules() {
    }

    /**
     * IP 脱敏：IPv4 隐末段；IPv6 展开后按 64 位掩码（保留高 4 段）；IPv4-mapped IPv6 不得保留内嵌完整地址；
     * 非法/未知一律 unavailable。仅接受文字地址，绝不做 DNS 解析。
     *
     * @param ip 原始 IP 字符串
     * @return 脱敏后的展示值
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unavailable";
        }
        String value = ip.trim();
        if (value.indexOf(':') >= 0) {
            return maskIpv6(value);
        }
        return maskIpv4(value);
    }

    private static String maskIpv4(String value) {
        if (!IPV4.matcher(value).matches()) {
            return "unavailable";
        }
        String[] octets = value.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int n;
            try {
                n = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                return "unavailable";
            }
            if (n < 0 || n > 255) {
                // 数值越界视为非法地址，不掩码不回显
                return "unavailable";
            }
            if (i < 3) {
                sb.append(n);
                sb.append('.');
            }
        }
        return sb.append('*').toString();
    }

    private static String maskIpv6(String value) {
        if (!IPV6_CHARS.matcher(value).matches()) {
            return "unavailable";
        }
        int dc = value.indexOf("::");
        String head = dc >= 0 ? value.substring(0, dc) : value;
        String[] headGroups = head.isEmpty() ? new String[0] : head.split(":");
        // 仅需高 64 位（前 4 段）；内嵌 IPv4 恒位于低 64 位，故不参与掩码输出，杜绝泄漏。
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String g = i < headGroups.length ? headGroups[i] : "0";
            // 末段若是内嵌 IPv4 文字（含点），因位于高 4 段之外的正常情况已排除；此处再防御性校验
            if (g.isEmpty() || !HEX_GROUP.matcher(g).matches()) {
                return "unavailable";
            }
            sb.append(Integer.toHexString(Integer.parseInt(g, 16)));
            if (i < 3) {
                sb.append(':');
            }
        }
        return sb.append("::").toString();
    }

    /** 客户端摘要：仅浏览器/平台类别，不回传可注入原文或完整 UA。 */
    public static String summarizeUserAgent(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown";
        }
        String lower = ua.toLowerCase(Locale.ROOT);
        String browser = "Other";
        if (lower.contains("edg/")) {
            browser = "Edge";
        } else if (lower.contains("opr/") || lower.contains("opera")) {
            browser = "Opera";
        } else if (lower.contains("firefox")) {
            browser = "Firefox";
        } else if (lower.contains("chrome")) {
            browser = "Chrome";
        } else if (lower.contains("safari")) {
            browser = "Safari";
        }
        String platform = "Other";
        if (lower.contains("windows")) {
            platform = "Windows";
        } else if (lower.contains("mac os x") || lower.contains("macintosh")) {
            platform = "macOS";
        } else if (lower.contains("android")) {
            platform = "Android";
        } else if (lower.contains("iphone") || lower.contains("ipad")) {
            platform = "iOS";
        } else if (lower.contains("linux")) {
            platform = "Linux";
        }
        return browser + " / " + platform;
    }

    /**
     * method 归类（与 SQL 筛选共享同一规则）：provider 命中白名单→SOCIAL；
     * 否则 method 小写命中 password/session→对应值；其余（缺 method、JSON null、未知方法）→UNKNOWN。
     *
     * @param extra 已解析的 extra map
     * @return PASSWORD|SESSION|SOCIAL|UNKNOWN
     */
    public static String classifyMethod(Map<String, Object> extra) {
        Object p = extra.get("provider");
        if (p != null && PROVIDER_WHITELIST.contains(String.valueOf(p).toLowerCase(Locale.ROOT))) {
            return "SOCIAL";
        }
        Object m = extra.get("method");
        if (m != null) {
            String mv = String.valueOf(m).toLowerCase(Locale.ROOT);
            if ("password".equals(mv)) {
                return "PASSWORD";
            }
            if ("session".equals(mv)) {
                return "SESSION";
            }
        }
        return "UNKNOWN";
    }
}
