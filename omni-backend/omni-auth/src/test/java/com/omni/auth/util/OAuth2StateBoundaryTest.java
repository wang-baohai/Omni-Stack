package com.omni.auth.util;

import com.omni.auth.config.OAuth2Properties;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实际HMAC state的租户、完整性和有效期边界；仅使用显式fixture密钥。 */
class OAuth2StateBoundaryTest {

    private static final String SECRET = "controlled-social-state-fixture-secret";

    /** 两个租户的合法state保留各自租户，篡改租户或签名均拒绝。 */
    @Test
    void signedStateBindsTenantAndRejectsTampering() {
        OAuth2StateUtils utils = utils();
        String first = utils.createState(1L);
        String second = utils.createState(2L);

        assertThat(utils.extractTenantId(first)).isEqualTo(1L);
        assertThat(utils.extractTenantId(second)).isEqualTo(2L);
        assertThatThrownBy(() -> utils.extractTenantId(first.replaceFirst("^1", "2")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> utils.extractTenantId(first + "0"))
                .isInstanceOf(BusinessException.class);
    }

    /** 有效签名也不能恢复超过10分钟的state。 */
    @Test
    void correctlySignedExpiredStateIsRejected() throws Exception {
        String payload = "1|" + (System.currentTimeMillis() - 11 * 60 * 1000);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String state = payload + "|" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> utils().extractTenantId(state)).isInstanceOf(BusinessException.class);
    }

    /** 空白、分段错误和伪签名失败关闭。 */
    @Test
    void missingAndMalformedStatesAreRejected() {
        for (String state : new String[]{null, "", " ", "1|123", "1|123|forged", "1|2|3|4"}) {
            assertThatThrownBy(() -> utils().extractTenantId(state)).isInstanceOf(BusinessException.class);
        }
    }

    private OAuth2StateUtils utils() {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setStateSecret(SECRET);
        return new OAuth2StateUtils(properties);
    }
}
