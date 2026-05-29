package com.omni.gateway.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;

/**
 * RSA 公钥提供者，用于 JWT 签名验证。
 * <p>
 * 该组件是 Gateway JWT 验证链路的基础设施层，负责从 Auth 服务的 JWKS 端点
 * （{@code /oauth2/jwks}）获取 RSA 公钥，并在本地缓存以避免每次请求都发起远程调用。
 * </p>
 *
 * <h3>JWK Set 获取流程</h3>
 * <ol>
 *   <li>Auth 服务在 {@code AuthorizationServerConfig} 中配置了 RSA 密钥对，
 *       Spring Authorization Server 自动暴露 {@code /oauth2/jwks} 端点</li>
 *   <li>本组件通过 WebClient 发起 HTTP GET 请求获取 JWK Set JSON</li>
 *   <li>解析 JSON 提取第一个 RSA 公钥（通常只有一个活跃密钥）</li>
 *   <li>将公钥缓存到内存，记录获取时间</li>
 * </ol>
 *
 * <h3>缓存与刷新策略</h3>
 * <ul>
 *   <li>缓存 TTL 通过 {@code auth.jwks.cache-ttl} 配置，默认 5 分钟</li>
 *   <li>每次调用 {@link #getPublicKey()} 时检查缓存是否过期（基于 {@code lastFetchTime}）</li>
 *   <li>过期后下次调用触发重新获取；未过期则直接返回缓存的公钥</li>
 *   <li>{@code cachedPublicKey} 和 {@code lastFetchTime} 声明为 {@code volatile}，
 *       保证多线程环境下的可见性（Gateway 基于 WebFlux，可能在多个 Reactor 线程中访问）</li>
 * </ul>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>使用 {@code WebClient.create()} 而非注入 {@code WebClient.Builder}，
 *       因为 Spring Cloud Gateway WebFlux 环境不自动配置 {@code WebClient.Builder} bean</li>
 *   <li>返回类型使用 {@code Mono<RSAPublicKey>} 以适配 Gateway 的响应式编程模型</li>
 * </ul>
 *
 * @see com.omni.gateway.filter.AuthFilter 使用本组件获取公钥进行 JWT 验证
 */
@Slf4j
@Component
public class JwkKeyProvider {

    /** WebClient 实例，用于发起 HTTP GET 请求获取 JWK Set */
    private final WebClient webClient;

    /** Auth 服务的 JWKS 端点 URI，默认 http://localhost:9000/oauth2/jwks */
    private final String jwksUri;

    /** 公钥缓存时长，过期后重新获取，默认 5 分钟 */
    private final Duration cacheTtl;

    /** 缓存的 RSA 公钥，volatile 保证多线程可见性 */
    private volatile RSAPublicKey cachedPublicKey;

    /** 上一次成功获取公钥的时间戳，用于判断缓存是否过期 */
    private volatile Instant lastFetchTime;

    /**
     * 构造函数，通过 Spring 配置属性初始化。
     *
     * @param jwksUri  Auth 服务 JWKS 端点地址，配置项 {@code auth.jwks.uri}
     * @param cacheTtl 公钥缓存 TTL，配置项 {@code auth.jwks.cache-ttl}
     */
    public JwkKeyProvider(
            @Value("${auth.jwks.uri:http://localhost:9000/oauth2/jwks}") String jwksUri,
            @Value("${auth.jwks.cache-ttl:5m}") Duration cacheTtl) {
        // 直接创建 WebClient 实例，不依赖 Spring 容器注入的 WebClient.Builder
        this.webClient = WebClient.create();
        this.jwksUri = jwksUri;
        this.cacheTtl = cacheTtl;
    }

    /**
     * 获取 RSA 公钥（响应式）。
     * <p>
     * 优先返回缓存的公钥；如果缓存为空或已过期，则从 Auth 服务重新获取。
     * 调用方（{@link com.omni.gateway.filter.AuthFilter}）在 Mono 链中使用此方法，
     * 保证整个 JWT 验证流程的非阻塞特性。
     * </p>
     *
     * @return 包含 RSA 公钥的 Mono，用于 JWT 签名验证
     */
    public Mono<RSAPublicKey> getPublicKey() {
        // 缓存命中且未过期，直接返回（快速路径）
        if (cachedPublicKey != null && !isExpired()) {
            return Mono.just(cachedPublicKey);
        }
        // 缓存未命中或已过期，发起远程请求
        return fetchJwkSet();
    }

    /**
     * 判断缓存是否已过期。
     *
     * @return true 表示缓存已过期或从未获取
     */
    private boolean isExpired() {
        return lastFetchTime == null || Instant.now().isAfter(lastFetchTime.plus(cacheTtl));
    }

    /**
     * 从 Auth 服务的 JWKS 端点获取 JWK Set 并解析出 RSA 公钥。
     * <p>
     * 请求流程：HTTP GET -> 获取 JSON 字符串 -> 解析为 JWKSet -> 提取第一个 RSAKey -> 转 RSAPublicKey
     * 成功后更新缓存和获取时间；失败时记录错误日志并传播异常。
     * </p>
     *
     * @return 包含新获取的 RSA 公钥的 Mono
     */
    private Mono<RSAPublicKey> fetchJwkSet() {
        return webClient.get()
                .uri(jwksUri)
                .retrieve()
                .bodyToMono(String.class)       // 获取 JWK Set 的 JSON 原始字符串
                .map(this::parseJwkSet)          // 解析 JSON -> RSAPublicKey
                .doOnNext(key -> {
                    // 更新缓存：公钥 + 获取时间
                    cachedPublicKey = key;
                    lastFetchTime = Instant.now();
                    log.info("JWK Set fetched from {}, public key cached", jwksUri);
                })
                .doOnError(e -> log.error("Failed to fetch JWK Set from {}", jwksUri, e));
    }

    /**
     * 解析 JWK Set JSON 字符串，提取第一个 RSA 公钥。
     * <p>
     * Spring Authorization Server 的 JWKS 端点返回标准 JWK Set 格式：
     * <pre>
     * { "keys": [{ "kty":"RSA", "kid":"...", "n":"...", "e":"...", ... }] }
     * </pre>
     * 当前实现取第一个密钥。如果 Auth 服务配置了多个密钥（密钥轮转），
     * 需要根据 JWT header 中的 kid 匹配对应密钥。
     * </p>
     *
     * @param json JWK Set 的 JSON 字符串
     * @return 解析出的 RSA 公钥
     * @throws RuntimeException 如果 JSON 解析失败或格式不正确
     */
    private RSAPublicKey parseJwkSet(String json) {
        try {
            JWKSet jwkSet = JWKSet.parse(json);
            // 取第一个密钥（当前 Auth 服务只配置一个 RSA 密钥对）
            RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
            return rsaKey.toRSAPublicKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWK Set response", e);
        }
    }
}
