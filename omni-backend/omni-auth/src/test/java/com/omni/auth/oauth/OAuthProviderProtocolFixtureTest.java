package com.omni.auth.oauth;

import com.omni.auth.config.OAuth2Properties;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 三个真实适配器的受控运输fixture；没有外部账号、网络请求或HTTP服务。 */
class OAuthProviderProtocolFixtureTest {

    private final List<HttpClient> opened = new ArrayList<>();

    /** 关闭构造器创建的真实客户端，fixture不遗留选择器线程或连接。 */
    @AfterEach
    void closeTransports() {
        opened.forEach(HttpClient::close);
    }

    /** 授权URL必须保留已配置回调和state，不能把密钥放入浏览器URL。 */
    @ParameterizedTest
    @ValueSource(strings = {"github", "google", "gitee"})
    void authorizationUrlEncodesStateAndDoesNotExposeSecret(String provider) {
        OAuth2ProviderHandler handler = handler(provider);
        URI url = URI.create(handler.buildAuthorizationUrl("tenant|fixture&state=1"));
        Map<String, String> params = form(url.getRawQuery());

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(params.get("client_id")).isEqualTo("fixture-client");
        assertThat(params.get("redirect_uri")).isEqualTo("http://localhost:8102/api/auth/oauth2/" + provider + "/callback");
        assertThat(params.get("state")).isEqualTo("tenant|fixture&state=1");
        assertThat(url.toString()).doesNotContain("fixture-secret");
    }

    /** 实际HTTP请求须为编码表单，特殊字符不能变成额外参数。 */
    @ParameterizedTest
    @ValueSource(strings = {"github", "google", "gitee"})
    void tokenRequestUsesFormEncodingAndExactConfiguredCallback(String provider) throws Exception {
        OAuth2ProviderHandler handler = handler(provider);
        HttpClient client = transport(handler, 200, "{\"access_token\":\"fixture-access-token\"}");

        assertThat(handler.exchangeCodeForAccessToken("fixture+code&other=中文"))
                .isEqualTo("fixture-access-token");
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(sent.capture(), any());
        HttpRequest request = sent.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Content-Type"))
                .contains("application/x-www-form-urlencoded");
        assertThat(request.uri().getQuery()).isNull();
        Map<String, String> body = form(body(request));
        assertThat(body.get("code")).isEqualTo("fixture+code&other=中文");
        assertThat(body.get("client_id")).isEqualTo("fixture-client");
        assertThat(body.get("client_secret")).isEqualTo("fixture-secret");
        assertThat(body.get("redirect_uri"))
                .isEqualTo("http://localhost:8102/api/auth/oauth2/" + provider + "/callback");
        assertThat(body).doesNotContainKey("other");
        if (!"github".equals(provider)) assertThat(body.get("grant_type")).isEqualTo("authorization_code");
    }

    /** 只解析受控成功资料，用户令牌必须在Bearer头中传递。 */
    @ParameterizedTest
    @ValueSource(strings = {"github", "google", "gitee"})
    void profileMappingAndBearerHeaderAreProviderSpecific(String provider) throws Exception {
        OAuth2ProviderHandler handler = handler(provider);
        String json = "google".equals(provider)
                ? "{\"sub\":\"71\",\"email\":\"fixture@example.invalid\",\"name\":\"Fixture\",\"picture\":\"https://fixture.invalid/avatar\"}"
                : "{\"id\":71,\"login\":\"fixture-login\",\"name\":\"Fixture\",\"avatar_url\":\"https://fixture.invalid/avatar\"}";
        HttpClient client = transport(handler, 200, json);

        var user = handler.fetchUserProfile("fixture-access-token");

        assertThat(user.getProviderUserId()).isEqualTo("71");
        assertThat(user.getUsername()).isEqualTo("google".equals(provider) ? "fixture" : "fixture-login");
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(sent.capture(), any());
        assertThat(sent.getValue().headers().firstValue("Authorization")).contains("Bearer fixture-access-token");
        assertThat(sent.getValue().uri().getQuery()).isNull();
    }

    /** 第三方错误、重定向及缺令牌不能被当作成功授权。 */
    @ParameterizedTest
    @ValueSource(strings = {"github", "google", "gitee"})
    void tokenErrorsRedirectsAndMalformedBodiesFailClosed(String provider) throws Exception {
        for (int status : new int[]{302, 401, 429, 500}) {
            OAuth2ProviderHandler handler = handler(provider);
            transport(handler, status, "{\"error\":\"fixture-error\"}");
            assertThatThrownBy(() -> handler.exchangeCodeForAccessToken("fixture-code"))
                    .isInstanceOf(BusinessException.class);
        }
        for (String body : new String[]{"{}", "{\"access_token\":\"\"}", "not-json"}) {
            OAuth2ProviderHandler handler = handler(provider);
            transport(handler, 200, body);
            assertThatThrownBy(() -> handler.exchangeCodeForAccessToken("fixture-code"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    /** 缺少外部唯一主体不能进入本地账户绑定。 */
    @ParameterizedTest
    @ValueSource(strings = {"github", "google", "gitee"})
    void profileWithoutExternalSubjectIsRejected(String provider) throws Exception {
        OAuth2ProviderHandler handler = handler(provider);
        transport(handler, 200, "{}");

        assertThatThrownBy(() -> handler.fetchUserProfile("fixture-access-token"))
                .isInstanceOf(BusinessException.class);
    }

    /** 运输异常和API拒绝必须返回业务错误，不跟随到任意新端点。 */
    @ParameterizedTest
    @ValueSource(strings = {"github", "google", "gitee"})
    void profileErrorsAndTransportFailuresAreRejected(String provider) throws Exception {
        for (int status : new int[]{302, 403, 429, 500}) {
            OAuth2ProviderHandler handler = handler(provider);
            transport(handler, status, "{\"error\":\"fixture-denied\"}");
            assertThatThrownBy(() -> handler.fetchUserProfile("fixture-access-token"))
                    .isInstanceOf(BusinessException.class);
        }
        OAuth2ProviderHandler handler = handler(provider);
        HttpClient client = transport(handler, 200, "{}");
        when(client.<String>send(any(HttpRequest.class), any())).thenThrow(new IOException("controlled offline fixture"));
        assertThatThrownBy(() -> handler.fetchUserProfile("fixture-access-token"))
                .isInstanceOf(BusinessException.class);
    }

    private OAuth2ProviderHandler handler(String provider) {
        OAuth2Properties config = new OAuth2Properties();
        config.getGithub().setClientId("fixture-client");
        config.getGithub().setClientSecret("fixture-secret");
        config.getGithub().setRedirectUri("http://localhost:8102/api/auth/oauth2/github/callback");
        config.getGoogle().setClientId("fixture-client");
        config.getGoogle().setClientSecret("fixture-secret");
        config.getGoogle().setRedirectUri("http://localhost:8102/api/auth/oauth2/google/callback");
        config.getGitee().setClientId("fixture-client");
        config.getGitee().setClientSecret("fixture-secret");
        config.getGitee().setRedirectUri("http://localhost:8102/api/auth/oauth2/gitee/callback");
        OAuth2ProviderHandler result = switch (provider) {
            case "github" -> new GitHubOAuth2Handler(config);
            case "google" -> new GoogleOAuth2Handler(config);
            case "gitee" -> new GiteeOAuth2Handler(config);
            default -> throw new IllegalArgumentException("未知fixture provider");
        };
        opened.add((HttpClient) ReflectionTestUtils.getField(result, "httpClient"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private HttpClient transport(OAuth2ProviderHandler handler, int status, String body) throws Exception {
        HttpClient original = (HttpClient) ReflectionTestUtils.getField(handler, "httpClient");
        assertThat(original.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(client.<String>send(any(HttpRequest.class), any())).thenReturn(response);
        ReflectionTestUtils.setField(handler, "httpClient", client);
        return client;
    }

    private Map<String, String> form(String encoded) {
        return Arrays.stream(encoded.split("&")).map(part -> part.split("=", 2)).collect(Collectors.toMap(
                pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)));
    }

    private String body(HttpRequest request) {
        CompletableFuture<String> result = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] content = new byte[buffer.remaining()];
                buffer.get(content);
                bytes.writeBytes(content);
            }
            @Override public void onError(Throwable error) { result.completeExceptionally(error); }
            @Override public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.join();
    }
}
