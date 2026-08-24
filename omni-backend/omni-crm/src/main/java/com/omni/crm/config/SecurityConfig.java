package com.omni.crm.config;

import com.omni.common.service.identity.GatewayPreAuthenticationFilter;
import com.omni.common.service.identity.ServiceIdentityFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * CRM 无状态预认证安全配置。
 *
 * @author Omni-Stack Team
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayPreAuthenticationFilter gatewayPreAuthenticationFilter;
    private final ServiceIdentityFilter serviceIdentityFilter;

    /**
     * 构建安全过滤器链。
     *
     * @param http 安全构建器
     * @return 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, 401, "未认证"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, 403, "权限不足，拒绝访问")));
        http.addFilterBefore(gatewayPreAuthenticationFilter, AuthorizationFilter.class);
        http.addFilterAfter(serviceIdentityFilter, GatewayPreAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse response, int code, String message) throws java.io.IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
