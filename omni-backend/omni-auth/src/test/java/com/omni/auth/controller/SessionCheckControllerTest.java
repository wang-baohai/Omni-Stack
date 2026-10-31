package com.omni.auth.controller;

import com.omni.auth.service.AccountLockoutService;
import com.omni.auth.service.CaptchaService;
import com.omni.auth.service.JwtTokenService;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.TenantService;
import com.omni.auth.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** SAS 会话探针读取边界：既有会话与无状态 JWT 上下文不能互相替代。 */
class SessionCheckControllerTest {

    private final AuthController controller = new AuthController(mock(UserService.class),
            mock(CaptchaService.class), mock(TenantService.class), mock(JwtTokenService.class),
            mock(OnlineUserService.class), mock(AuthenticationManager.class),
            mock(AccountLockoutService.class), mock(ApplicationEventPublisher.class));

    /** 每例结束清理线程上下文，避免把测试 JWT 身份带入下一例。 */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** 真实 SAS 会话上下文可被读取，且不会被写入 REST 线程认证。 */
    @Test
    void readsExistingAuthenticatedSessionWithoutPromotingRestContext() {
        MockHttpServletRequest request = requestWithSession(
                new TestingAuthenticationToken("tenant-user", null, "ROLE_EMPLOYEE"));

        var response = controller.sessionCheck(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo("tenant-user");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 单独 JWT 线程身份不应冒充已经完成 SAS 会话认证。 */
    @Test
    void rejectsJwtOnlyContextAndDoesNotCreateSession() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("jwt-user", null, "ROLE_EMPLOYEE"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(controller.sessionCheck(request).getCode()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
    }

    /** 匿名会话即使 isAuthenticated 为真也不能被当作已登录。 */
    @Test
    void rejectsAnonymousSession() {
        MockHttpServletRequest request = requestWithSession(new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(controller.sessionCheck(request).getCode()).isEqualTo(401);
    }

    /** 未完成认证的口令令牌不能获得会话成功结果。 */
    @Test
    void rejectsUnauthenticatedSession() {
        MockHttpServletRequest request = requestWithSession(
                UsernamePasswordAuthenticationToken.unauthenticated("pending", null));

        assertThat(controller.sessionCheck(request).getCode()).isEqualTo(401);
    }

    /** 已失效的会话不能继续通过检查。 */
    @Test
    void rejectsInvalidatedSession() {
        MockHttpServletRequest request = requestWithSession(
                new TestingAuthenticationToken("tenant-user", null, "ROLE_EMPLOYEE"));
        request.getSession(false).invalidate();

        assertThat(controller.sessionCheck(request).getCode()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
    }

    private MockHttpServletRequest requestWithSession(
            org.springframework.security.core.Authentication authentication) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        request.setSession(session);
        return request;
    }
}
