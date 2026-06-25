package com.omni.common.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * XXL-JOB Admin HTTP API 客户端。
 * <p>
 * 封装调度中心的管理接口（登录、任务增删启停触发等），
 * 通过 session cookie 认证，支持自动续期。
 * 所有 API 调用均通过 {@code /xxl-job-admin} 路径前缀访问。
 * </p>
 *
 * <p>认证机制：通过 {@code /auth/doLogin} 登录获取 {@code xxl_job_login_token} cookie，
 * 后续请求携带此 cookie。认证失效时自动重新登录并重试一次。</p>
 *
 * @author Omni-Stack Team
 * @see XxlJobProperties
 */
@Slf4j
public class XxlJobAdminClient {

    private final String adminAddress;
    private final String username;
    private final String password;
    private final RestTemplate restTemplate;

    /** 缓存的 session cookie */
    private volatile String sessionCookie;

    private static final String LOGIN_TOKEN_COOKIE = "xxl_job_login_token";

    public XxlJobAdminClient(String adminAddress, String username, String password) {
        // 去除末尾斜杠
        this.adminAddress = adminAddress.endsWith("/")
                ? adminAddress.substring(0, adminAddress.length() - 1) : adminAddress;
        this.username = username;
        this.password = password;
        this.restTemplate = new RestTemplate();
    }

    // ─── 登录 ───

    /**
     * 登录 XXL-JOB Admin 获取 session cookie。
     * <p>通过 {@code /auth/doLogin} 发送表单登录请求，
     * 从响应 {@code Set-Cookie} 头中提取 {@code xxl_job_login_token}。</p>
     *
     * @return session cookie 字符串
     * @throws RuntimeException 登录失败或未获取到 cookie 时抛出
     */
    private String login() {
        String url = adminAddress + "/auth/doLogin";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userName", username);
        form.add("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(url), HttpMethod.POST, request, String.class);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.contains(LOGIN_TOKEN_COOKIE)) {
                    String value = cookie.split(";")[0];
                    log.debug("XXL-JOB 登录成功，获取 session cookie");
                    return value;
                }
            }
        }
        throw new RuntimeException("XXL-JOB 登录失败：未获取到 session cookie");
    }

    /**
     * 获取可用的 session cookie（双重检查锁缓存 + 自动续期）。
     * <p>首次调用时触发登录，后续调用直接返回缓存的 cookie。
     * 使用 {@code volatile} + {@code synchronized} 保证线程安全。</p>
     *
     * @return 有效的 session cookie
     */
    private String getCookie() {
        if (sessionCookie == null) {
            synchronized (this) {
                if (sessionCookie == null) {
                    sessionCookie = login();
                }
            }
        }
        return sessionCookie;
    }

    /**
     * 使当前 cookie 失效，下次请求时重新登录。
     */
    private void invalidateCookie() {
        sessionCookie = null;
    }

    // ─── 请求工具方法 ───

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.COOKIE, getCookie());
        return headers;
    }

    /**
     * 执行带认证的 HTTP 请求，认证失败自动重试一次。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doRequest(String path, HttpMethod method,
                                           MultiValueMap<String, String> form) {
        String url = adminAddress + path;
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, createHeaders());

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(URI.create(url), method, request, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("XXL-JOB API 请求失败: " + path, e);
        }

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("XXL-JOB API 响应为空: " + path);
        }

        // 检查是否认证失败（xxl-sso 返回 code=500 + "not login" 消息）
        Object code = body.get("code");
        Object msg = body.get("msg");
        boolean authFailed = (code instanceof Number && ((Number) code).intValue() != 200)
                || (msg instanceof String && ((String) msg).contains("not login"));
        if (authFailed) {
            log.debug("XXL-JOB 认证失效，重新登录后重试");
            invalidateCookie();
            request = new HttpEntity<>(form, createHeaders());
            response = restTemplate.exchange(URI.create(url), method, request, Map.class);
            body = response.getBody();
            if (body == null) {
                throw new RuntimeException("XXL-JOB API 重试后响应仍为空: " + path);
            }
        }

        return body;
    }

    private Map<String, Object> postForm(String path, MultiValueMap<String, String> form) {
        return doRequest(path, HttpMethod.POST, form);
    }

    // ─── 执行器管理 ───

    /**
     * 根据 appname 查询执行器 ID。
     *
     * @param appname 执行器 appname
     * @return 执行器 ID，未找到时返回 -1
     */
    public int getJobGroupId(String appname) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("start", "0");
        form.add("length", "100");
        form.add("appname", appname);

        Map<String, Object> body = postForm("/jobgroup/pageList", form);
        Object data = body.get("data");
        if (data instanceof Map<?, ?> pageData) {
            Object dataArray = pageData.get("data");
            if (dataArray instanceof List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                if (first instanceof Map<?, ?> group) {
                    Object id = group.get("id");
                    if (id instanceof Number) {
                        return ((Number) id).intValue();
                    }
                }
            }
        }
        return -1;
    }

    // ─── 任务管理 ───

    /**
     * 查询已注册的任务列表。
     *
     * @param jobGroup        执行器 ID
     * @param executorHandler Handler 名称（可选）
     * @return 任务列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> pageList(int jobGroup, String executorHandler) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("start", "0");
        form.add("length", "100");
        form.add("jobGroup", String.valueOf(jobGroup));
        form.add("triggerStatus", "-1");
        form.add("jobDesc", "");
        form.add("executorHandler", executorHandler != null ? executorHandler : "");
        form.add("author", "");

        Map<String, Object> body = postForm("/jobinfo/pageList", form);
        Object data = body.get("data");
        if (data instanceof Map<?, ?> pageData) {
            Object dataArray = pageData.get("data");
            if (dataArray instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        }
        return Collections.emptyList();
    }

    /**
     * 新增任务到 XXL-JOB 调度中心。
     * <p>创建任务并立即启动（{@code triggerStatus=1}）。
     * 默认使用 BEAN 模式、串行执行、DO_NOTHING 失活策略。</p>
     *
     * @param jobGroup        执行器 ID
     * @param jobDesc         任务描述
     * @param cron            Cron 表达式
     * @param routeStrategy   路由策略（FIRST/LAST/ROUND 等）
     * @param executorHandler Handler 名称
     * @param executorParam   执行器参数（JSON 字符串，可为空）
     * @return XXL-JOB 返回的任务 ID（data 字段）
     */
    public String addJob(int jobGroup, String jobDesc, String cron,
                         String routeStrategy, String executorHandler, String executorParam) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("jobGroup", String.valueOf(jobGroup));
        form.add("jobDesc", jobDesc);
        form.add("scheduleType", "CRON");
        form.add("scheduleConf", cron);
        form.add("glueType", "BEAN");
        form.add("executorHandler", executorHandler);
        form.add("executorRouteStrategy", routeStrategy);
        form.add("executorBlockStrategy", "SERIAL_EXECUTION");
        form.add("misfireStrategy", "DO_NOTHING");
        form.add("triggerStatus", "1");
        form.add("author", "admin");
        if (executorParam != null && !executorParam.isBlank()) {
            form.add("executorParam", executorParam);
        }

        Map<String, Object> body = postForm("/jobinfo/insert", form);
        Object data = body.get("data");
        return data != null ? data.toString() : "";
    }

    /**
     * 更新已有任务的调度配置（cron/参数）。
     *
     * @param xxlJobId      XXL-JOB 任务 ID
     * @param cron          新的 Cron 表达式
     * @param executorParam 新的执行器参数（JSON 字符串）
     */
    public void updateJob(int xxlJobId, String cron, String executorParam) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(xxlJobId));
        form.add("scheduleType", "CRON");
        form.add("scheduleConf", cron);
        if (executorParam != null && !executorParam.isBlank()) {
            form.add("executorParam", executorParam);
        }
        postForm("/jobinfo/update", form);
    }

    /**
     * 启动任务，使其按 Cron 表达式定期触发。
     *
     * @param jobId XXL-JOB 任务 ID
     */
    public void startJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ids[]", String.valueOf(jobId));
        postForm("/jobinfo/start", form);
    }

    /**
     * 停止任务，暂停 Cron 调度。
     *
     * @param jobId XXL-JOB 任务 ID
     */
    public void stopJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ids[]", String.valueOf(jobId));
        postForm("/jobinfo/stop", form);
    }

    /**
     * 立即触发任务执行，不等待下次 Cron 调度。
     *
     * @param jobId         XXL-JOB 任务 ID
     * @param executorParam 执行器参数（JSON 字符串，可为 null）
     */
    public void triggerJob(int jobId, String executorParam) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(jobId));
        form.add("executorParam", executorParam != null ? executorParam : "");
        form.add("addressList", "");
        postForm("/jobinfo/trigger", form);
    }

    /**
     * 删除任务，从 XXL-JOB 调度中心移除。
     *
     * @param jobId XXL-JOB 任务 ID
     */
    public void removeJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ids[]", String.valueOf(jobId));
        postForm("/jobinfo/delete", form);
    }
}
