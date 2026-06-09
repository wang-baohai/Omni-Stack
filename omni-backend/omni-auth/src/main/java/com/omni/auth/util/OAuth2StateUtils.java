package com.omni.auth.util;

import com.omni.auth.config.OAuth2Properties;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * OAuth2 State 参数工具类。
 * <p>
 * 使用 HMAC-SHA256 算法对 state 参数进行签名，确保：
 * <ul>
 *   <li>防 CSRF：第三方无法伪造合法的 state</li>
 *   <li>携带租户上下文：state 中编码 tenantId，回调时可还原</li>
 *   <li>防过期：state 包含时间戳，超过 10 分钟自动失效</li>
 * </ul>
 * 格式：{@code tenantId|timestamp|hex(HMAC-SHA256(tenantId|timestamp, secret))}
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2StateUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** state 有效期：10 分钟（毫秒） */
    private static final long STATE_TTL_MS = 10 * 60 * 1000L;

    /** OAuth2 配置属性，提供 state 签名所需的 HMAC 密钥 */
    private final OAuth2Properties oauth2Properties;

    /**
     * 生成带 HMAC 签名的 state 参数。
     *
     * @param tenantId 租户 ID
     * @return 格式为 {@code tenantId|timestamp|hex(hmac)} 的 state 字符串
     */
    public String createState(Long tenantId) {
        long timestamp = System.currentTimeMillis();
        String payload = tenantId + "|" + timestamp;
        String signature = computeHmac(payload);
        return payload + "|" + signature;
    }

    /**
     * 验证 state 参数并提取租户 ID。
     * <p>校验 HMAC 签名完整性和时间戳新鲜度（10 分钟窗口）。</p>
     *
     * @param state 回调传入的 state 参数
     * @return 从 state 中提取的租户 ID
     * @throws BusinessException state 格式错误、签名不匹配或已过期时抛出
     */
    public Long extractTenantId(String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(400, "OAuth2 授权状态参数缺失");
        }

        String[] parts = state.split("\\|");
        if (parts.length != 3) {
            throw new BusinessException(400, "OAuth2 授权状态格式无效");
        }

        String tenantIdStr = parts[0];
        String timestampStr = parts[1];
        String signature = parts[2];

        // 验证 HMAC 签名
        String payload = tenantIdStr + "|" + timestampStr;
        String expectedSignature = computeHmac(payload);
        if (!expectedSignature.equals(signature)) {
            log.warn("OAuth2 state HMAC 验证失败: state={}", state);
            throw new BusinessException(400, "OAuth2 授权状态无效或已过期");
        }

        // 验证时间戳新鲜度
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "OAuth2 授权状态时间戳无效");
        }
        if (System.currentTimeMillis() - timestamp > STATE_TTL_MS) {
            log.warn("OAuth2 state 已过期: timestamp={}", timestampStr);
            throw new BusinessException(400, "OAuth2 授权已过期，请重新登录");
        }

        // 提取并返回 tenantId
        try {
            return Long.parseLong(tenantIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "OAuth2 授权状态中租户 ID 无效");
        }
    }

    /**
     * 计算 HMAC-SHA256 签名。
     *
     * @param data 待签名的数据
     * @return 十六进制编码的签名结果
     */
    private String computeHmac(String data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    oauth2Properties.getStateSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            log.error("HMAC-SHA256 计算失败", e);
            throw new BusinessException("OAuth2 签名计算失败");
        }
    }
}
