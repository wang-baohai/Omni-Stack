package com.omni.auth.controller;

import com.omni.auth.config.OAuth2Properties;
import com.omni.auth.dto.LoginResult;
import com.omni.auth.service.SocialLoginService;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 仅控制器协议fixture，不产生页面成功截图，也不声称外部账号已授权。 */
class SocialLoginControllerProtocolTest {

    private final SocialLoginService service = mock(SocialLoginService.class);
    private final OAuth2Properties properties = new OAuth2Properties();
    private final SocialLoginController controller = new SocialLoginController(service, properties);

    /** 用户拒绝在调用第三方/数据库之前返回明确错误页。 */
    @Test
    void denialDoesNotAttemptTokenExchange() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.handleCallback("github", null, null, "access_denied", response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=user_denied");
        verifyNoInteractions(service);
    }

    /** 缺码或缺state均不进入登录服务。 */
    @Test
    void missingCallbackInputsDoNotReachService() throws Exception {
        for (String[] input : new String[][]{{null, "fixture-state"}, {"fixture-code", null}, {"", ""}}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.handleCallback("google", input[0], input[1], null, response);
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=invalid_callback");
        }
        verifyNoInteractions(service);
    }

    /** 已处理的业务拒绝不携带成功令牌或原code/state跳转。 */
    @Test
    void invalidStateErrorRedirectNeverCarriesSuccessToken() throws Exception {
        when(service.handleCallback("gitee", "fixture-code", "fixture-state"))
                .thenThrow(new BusinessException(400, "受控无效状态"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.handleCallback("gitee", "fixture-code", "fixture-state", null, response);

        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:3000/login?error=social_login_failed&message=")
                .doesNotContain("#token=", "fixture-code", "fixture-state");
    }

    /** 编码协议fixture仅验证令牌放fragment、用户名UTF-8编码，不算真实社交登录成功。 */
    @Test
    void successProtocolFixtureUsesFragmentInsteadOfQueryToken() throws Exception {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"991\",\"username\":\"受控用户\"}".getBytes(StandardCharsets.UTF_8));
        String token = "fixture-header." + payload + ".fixture-signature";
        when(service.handleCallback("github", "fixture-code", "fixture-state"))
                .thenReturn(LoginResult.builder().accessToken(token).build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.handleCallback("github", "fixture-code", "fixture-state", null, response);

        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:3000/callback#token=")
                .contains("&username=%E5%8F%97%E6%8E%A7%E7%94%A8%E6%88%B7")
                .doesNotContain("?token=", "fixture-code", "fixture-state");
    }
}
