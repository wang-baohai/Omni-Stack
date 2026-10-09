package com.omni.gateway.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.omni.gateway.config.JwkKeyProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AuthFilter} 网关签名断言（HMAC-SHA256）测试。
 * <p>
 * 验证网关在 JWT 校验通过后签发可被下游验证的身份断言，并在密钥缺失时 fail-closed。
 * </p>
 */
class AuthFilterHmacTest {

    private static final String SECRET = "unit-test-gateway-assertion-secret";
    private static final String PROTECTED_PATH = "/api/base/dict/types";

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    /** 有效 JWT 通过后必须签发格式正确、可被同一密钥重算验证的断言头。 */
    @Test
    void shouldIssueVerifiableHmacAssertionAfterJwtValidation() throws Exception {
        AuthFilter filter = filter(SECRET, "");
        MockServerWebExchange exchange = protectedExchange();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chainCapturing(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        String assertion = forwarded.get().getRequest().getHeaders().getFirst("X-Gateway-Assertion");
        assertThat(assertion).isNotBlank();
        String[] segments = assertion.split(":");
        assertThat(segments).hasSize(2);

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(segments[0]), StandardCharsets.UTF_8);
        byte[] providedMac = decoder.decode(segments[1]);
        String[] fields = payload.split("\\|");
        assertThat(fields).hasSize(4);
        assertThat(fields[0]).isEqualTo("7");
        assertThat(fields[1]).isEqualTo("1");
        assertThat(fields[2]).isEqualTo("alice");
        long issuedAt = Long.parseLong(fields[3]);
        assertThat(Math.abs(System.currentTimeMillis() / 1000L - issuedAt)).isLessThanOrEqualTo(5L);
        assertThat(hmac(payload, SECRET)).isEqualTo(providedMac);

        // 向后兼容：明文身份头与转发标记仍需保留
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Gateway-Forwarded")).isEqualTo("true");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("7");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("1");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("alice");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("Authorization")).isNull();
    }

    /** 客户端伪造的 X-Gateway-Assertion 必须在网关注入前被剥离替换。 */
    @Test
    void shouldOverrideClientForgedAssertionHeader() throws Exception {
        AuthFilter filter = filter(SECRET, "");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + signedToken())
                        .header("X-Gateway-Assertion", "Zm9yZ2Vk:Zm9yZ2Vk")
                        .header("X-User-Id", "999"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chainCapturing(forwarded)).block();

        List<String> assertions = forwarded.get().getRequest().getHeaders().get("X-Gateway-Assertion");
        assertThat(assertions).hasSize(1);
        String assertion = assertions.get(0);
        String[] segments = assertion.split(":");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(segments[0]), StandardCharsets.UTF_8);
        // 身份取自 JWT 而非客户端头，且签名可被密钥重算验证
        assertThat(payload.split("\\|")[0]).isEqualTo("7");
        assertThat(decoder.decode(segments[1])).isEqualTo(hmac(payload, SECRET));
    }

    /** 密钥未配置且非 test profile 时必须 fail-closed 返回 503，不转发任何身份。 */
    @Test
    void shouldFailClosedWith503WhenSecretMissing() {
        AuthFilter filter = filter("", "");
        MockServerWebExchange exchange = protectedExchange();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chainCapturing(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":503");
    }

    /** test profile 允许密钥缺失（切片测试），此时不签发断言但保持放行。 */
    @Test
    void shouldSkipAssertionInTestProfileWithoutSecret() {
        AuthFilter filter = filter("", "test");
        MockServerWebExchange exchange = protectedExchange();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chainCapturing(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Gateway-Assertion")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("7");
    }

    /** 弱密钥（不足 32 字节）同样 fail-closed，避免签名被爆破后断言失去意义。 */
    @Test
    void shouldFailClosedWith503WhenSecretTooWeak() {
        AuthFilter filter = filter("short-secret", "");
        MockServerWebExchange exchange = protectedExchange();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chainCapturing(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private GatewayFilterChain chainCapturing(AtomicReference<ServerWebExchange> holder) {
        return current -> {
            holder.set(current);
            return Mono.empty();
        };
    }

    private MockServerWebExchange protectedExchange() {
        try {
            return MockServerWebExchange.from(MockServerHttpRequest.get(PROTECTED_PATH)
                    .header("Authorization", "Bearer " + signedToken()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private AuthFilter filter(String secret, String profiles) {
        JwkKeyProvider keyProvider = mock(JwkKeyProvider.class);
        when(keyProvider.getPublicKey()).thenReturn(Mono.just(publicKey));
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        AuthFilter filter = new AuthFilter(keyProvider, redis);
        ReflectionTestUtils.setField(filter, "gatewayAssertionSecret", secret);
        ReflectionTestUtils.setField(filter, "activeProfiles", profiles);
        return filter;
    }

    private String signedToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("7")
                .claim("tenant_id", "1")
                .claim("username", "alice")
                .claim("roles", List.of("USER"))
                .claim("scope", "dict:type:list")
                .jwtID("jti-hmac-test")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000L))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    private byte[] hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
