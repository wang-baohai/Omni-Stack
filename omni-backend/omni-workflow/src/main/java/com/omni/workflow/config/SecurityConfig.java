package com.omni.workflow.config;

import com.omni.workflow.security.GatewayPreAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Workflow 服务安全配置。
 * <p>
 * 依赖 Gateway 完成 JWT 验证，通过 {@link GatewayPreAuthFilter} 从请求头中
 * 提取身份信息构建 {@code Authentication}，使 {@code @PreAuthorize} 生效。
 * </p>
 * <p>
 * 采用无状态会话策略（{@code STATELESS}），禁用表单登录和 CSRF，
 * 符合微服务架构下的 API 网关转发模式。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 安全过滤器链。
     * <p>
     * 允许 actuator 和 error 端点匿名访问，其余请求需要认证（由 Gateway 保证）。
     * 注册 {@link GatewayPreAuthFilter} 在 {@link AuthorizationFilter} 之前执行。
     * </p>
     *
     * @param http HttpSecurity 配置对象
     * @return 构建完成的安全过滤器链
     * @throws Exception 配置过程中的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                // 权限不足时返回 JSON 格式的 403 响应（而非默认 HTML 错误页）
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("权限不足: URI={}, error={}", request.getRequestURI(),
                                    accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"code\":403,\"message\":\"权限不足，拒绝访问\",\"data\":null}");
                        })
                );

        // 网关预认证过滤器：从 Gateway 转发的请求头中构建 Authentication
        http.addFilterBefore(new GatewayPreAuthFilter(), AuthorizationFilter.class);

        return http.build();
    }
}
