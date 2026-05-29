package com.omni.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Spring Authorization Server configuration.
 * <p>Configures the OAuth2 authorization server endpoints, token issuance, and JWT signing.
 * In SAS 7 (Spring Security 7), the configurer classes moved to
 * {@code org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization}.</p>
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    /**
     * Request matcher for OAuth2 authorization server endpoints.
     * <p>Uses explicit path matching since {@code getEndpointsMatcher()} requires
     * the configurer to be initialized via {@code HttpSecurity} lifecycle.</p>
     */
    private static RequestMatcher oauth2EndpointsMatcher() {
        return new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/oauth2/authorize"),
                PathPatternRequestMatcher.pathPattern("/oauth2/token"),
                PathPatternRequestMatcher.pathPattern("/oauth2/jwks"),
                PathPatternRequestMatcher.pathPattern("/.well-known/openid-configuration"),
                PathPatternRequestMatcher.pathPattern("/oauth2/token/introspect"),
                PathPatternRequestMatcher.pathPattern("/oauth2/token/revocation"),
                PathPatternRequestMatcher.pathPattern("/oauth2/register"),
                PathPatternRequestMatcher.pathPattern("/connect/register"),
                PathPatternRequestMatcher.pathPattern("/connect/logout"),
                PathPatternRequestMatcher.pathPattern("/userinfo"),
                PathPatternRequestMatcher.pathPattern("/oauth2/par"),
                PathPatternRequestMatcher.pathPattern("/oauth2/device_authorization"),
                PathPatternRequestMatcher.pathPattern("/oauth2/device_verification")
        );
    }

    /**
     * Authorization Server Security Filter Chain for OAuth2 endpoints.
     * <p>Matches only OAuth2 authorization server endpoints (authorize, token, jwks, etc.).</p>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher endpointsMatcher = oauth2EndpointsMatcher();
        http.securityMatcher(endpointsMatcher);
        http.with(new OAuth2AuthorizationServerConfigurer(), Customizer.withDefaults());

        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        endpointsMatcher
                )
        );

        return http.build();
    }

    /**
     * Default Security Filter Chain for non-OAuth2 endpoints.
     * <p>Matches all requests that are NOT OAuth2 authorization server endpoints.</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new NegatedRequestMatcher(oauth2EndpointsMatcher()));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * JWK Source for JWT signing.
     * <p>Generates an RSA key pair on startup. In production, use a persistent key store.</p>
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * JWT Decoder for verifying signed tokens.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * In-memory registered client repository with a default OAuth2 client.
     * <p>In production, use {@code JdbcRegisteredClientRepository} backed by database.</p>
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient defaultClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("omni-frontend")
                .clientSecret("{noop}omni-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://localhost:3000/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(defaultClient);
    }

    /**
     * Authorization server settings with default endpoint URLs.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }
}
