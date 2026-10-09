package com.omni.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 登录/审计安全投影共享规则回归测试（D02 / RF01 / RF02）。
 * <p>这些用例针对 Codex 确认的 P0/必达缺陷，能在旧实现上失败：
 * 旧 IP 掩码按冒号直接分段导致 IPv4-mapped IPv6 泄漏内嵌完整地址、非法 IPv4 未校验数值范围；
 * method 归类须与 SQL 筛选一致。</p>
 *
 * @author Omni-Stack Team
 */
class LoginProjectionRulesTest {

    @Test
    @DisplayName("IPv4 末段掩码，合法")
    void maskIpv4Ok() {
        assertEquals("10.0.0.*", LoginProjectionRules.maskIp("10.0.0.1"));
        assertEquals("192.168.1.*", LoginProjectionRules.maskIp("192.168.1.2"));
    }

    @Test
    @DisplayName("非法 IPv4 数值越界须 unavailable（旧实现仅 \\d{1,3} 会误掩码泄漏）")
    void maskIpv4OutOfRange() {
        assertEquals("unavailable", LoginProjectionRules.maskIp("999.999.999.999"));
        assertEquals("unavailable", LoginProjectionRules.maskIp("256.1.1.1"));
        assertEquals("unavailable", LoginProjectionRules.maskIp("1.2.3"));
        assertEquals("unavailable", LoginProjectionRules.maskIp("abc.def.ghi.jkl"));
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 不得保留内嵌完整地址（旧实现按冒号分段会泄漏 192.168.1.2）")
    void maskIpv4MappedIpv6NoLeak() {
        String masked = LoginProjectionRules.maskIp("::ffff:192.168.1.2");
        assertEquals("0:0:0:0::", masked);
        org.junit.jupiter.api.Assertions.assertFalse(masked.contains("192"), "脱敏结果不得含内嵌 IPv4 前段");
        org.junit.jupiter.api.Assertions.assertFalse(masked.contains("168"), "脱敏结果不得含内嵌 IPv4");
    }

    @Test
    @DisplayName("压缩 IPv6 保留高 64 位")
    void maskIpv6Compressed() {
        assertEquals("2001:db8:1:2::", LoginProjectionRules.maskIp("2001:db8:1:2:0:0:0:1"));
        assertEquals("2001:db8:0:0::", LoginProjectionRules.maskIp("2001:db8::1"));
    }

    @Test
    @DisplayName("空/空白 IP → unavailable")
    void maskEmpty() {
        assertEquals("unavailable", LoginProjectionRules.maskIp(null));
        assertEquals("unavailable", LoginProjectionRules.maskIp("   "));
    }

    @Test
    @DisplayName("method 归类与 SQL 筛选一致：provider 白名单优先，缺/未知归 UNKNOWN")
    void classifyMethodConsistent() {
        assertEquals("SOCIAL", LoginProjectionRules.classifyMethod(Map.of("provider", "GitHub", "method", "password")));
        assertEquals("PASSWORD", LoginProjectionRules.classifyMethod(Map.of("method", "password")));
        assertEquals("SESSION", LoginProjectionRules.classifyMethod(Map.of("method", "session")));
        // 非白名单 provider 且缺 method → UNKNOWN（不再是旧 SQL 误判的 SOCIAL）
        assertEquals("UNKNOWN", LoginProjectionRules.classifyMethod(Map.of("provider", "facebook")));
        // 未知 method 值 → UNKNOWN
        assertEquals("UNKNOWN", LoginProjectionRules.classifyMethod(Map.of("method", "wechat")));
        // 缺 method（含 JSON null 经 Jackson 解析为 null）→ UNKNOWN
        Map<String, Object> withNullMethod = new HashMap<>();
        withNullMethod.put("method", null);
        assertEquals("UNKNOWN", LoginProjectionRules.classifyMethod(withNullMethod));
        assertEquals("UNKNOWN", LoginProjectionRules.classifyMethod(Map.of()));
    }
}
