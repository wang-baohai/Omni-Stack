package com.omni.common.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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

    /** 默认连接超时 */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 默认读取超时 */
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    public XxlJobAdminClient(String adminAddress, String username, String password) {
        // 去除末尾斜杠
        this.adminAddress = adminAddress.endsWith("/")
                ? adminAddress.substring(0, adminAddress.length() - 1) : adminAddress;
        this.username = username;
        this.password = password;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        factory.setReadTimeout(DEFAULT_READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
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

    /**
     * 写操作专用：认证重试后仍为业务失败（code!=200）时抛出异常，不得静默吞掉。
     *
     * @param path 接口路径
     * @param form 表单参数
     */
    private void postFormChecked(String path, MultiValueMap<String, String> form) {
        Map<String, Object> body = postForm(path, form);
        Object code = body.get("code");
        if (!(code instanceof Number) || ((Number) code).intValue() != 200) {
            throw new RuntimeException("XXL-JOB API 业务失败: " + path + ", code=" + code + ", msg=" + body.get("msg"));
        }
    }

    // ─── 执行器管理 ───

    /**
     * 确保执行器组存在于 XXL-JOB 调度中心。
     * <p>先查询 appname 是否已注册，未注册则通过 API 自动创建。
     * 采用自动注册模式（addressType=0），执行器上线后自动上报地址。</p>
     *
     * @param appname 执行器 AppName
     * @param title   执行器显示名称
     * @return 执行器组 ID
     */
    public int ensureExecutorGroup(String appname, String title) {
        int groupId = getJobGroupId(appname);
        if (groupId >= 0) {
            log.debug("执行器组已存在: appname={}, groupId={}", appname, groupId);
            return groupId;
        }

        log.info("执行器组不存在，自动创建: appname={}, title={}", appname, title);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("appname", appname);
        form.add("title", title);
        form.add("addressType", "0");  // 0=自动注册
        form.add("addressList", "");

        Map<String, Object> body = postForm("/jobgroup/insert", form);
        Object code = body.get("code");
        if (code instanceof Number && ((Number) code).intValue() == 200) {
            log.info("执行器组创建成功: appname={}", appname);
            return getJobGroupId(appname);
        }
        Object msg = body.get("msg");
        log.warn("执行器组创建响应: code={}, msg={}", code, msg);
        return getJobGroupId(appname);
    }

    /**
     * 根据 appname 查询执行器 ID。
     *
     * @param appname 执行器 appname
     * @return 执行器 ID，未找到时返回 -1
     */
    public int getJobGroupId(String appname) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("offset", "0");
        form.add("pagesize", "100");
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

    /** 分页硬性预算上限（防实现/契约变化后仍出现无上限循环；正常量级远不到此上限）。 */
    private static final int MAX_PAGE_LIST_PAGES = 100;

    /**
     * 查询已注册的任务列表（完整分页，杜绝首 100 条截断误判目标不存在）。
     * <p>逐页遍历直到取满 {@code total}（XXL-JOB 3.3.1 {@code PageModel} 字段为 {@code offset/pagesize/data/total}，
     * 不是 {@code start/length/recordsTotal}）；响应结构异常/内层非列表时显式抛错，
     * 不得把解析异常或空返回当作“远端不存在”，交由上层保留可恢复关联。</p>
     * <p>若 Admin 连续两次返回相同的一页内容（通常意味着请求参数名与 Admin 实际字段不匹配，Admin 忽略了
     * 不认识的分页参数），或翻页次数超过硬性预算，均显式抛错终止——本方法被 {@code ApplicationRunner}
     * 同步调用，一旦不返回会阻断 {@code ApplicationReadyEvent}，导致 readiness 永久不翻转。</p>
     *
     * @param jobGroup        执行器 ID
     * @param executorHandler Handler 名称（可选）
     * @return 该执行器组下的全量任务列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> pageList(int jobGroup, String executorHandler) {
        List<Map<String, Object>> all = new ArrayList<>();
        int offset = 0;
        final int pagesize = 100;
        List<Object> lastPageIds = null;
        int pages = 0;
        while (true) {
            if (++pages > MAX_PAGE_LIST_PAGES) {
                throw new RuntimeException("XXL-JOB pageList 翻页次数超过预算上限 " + MAX_PAGE_LIST_PAGES + "，终止以避免无限循环: jobGroup=" + jobGroup);
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("offset", String.valueOf(offset));
            form.add("pagesize", String.valueOf(pagesize));
            form.add("jobGroup", String.valueOf(jobGroup));
            form.add("triggerStatus", "-1");
            form.add("jobDesc", "");
            form.add("executorHandler", executorHandler != null ? executorHandler : "");
            form.add("author", "");

            Map<String, Object> body = postForm("/jobinfo/pageList", form);
            Object data = body.get("data");
            if (!(data instanceof Map<?, ?> pageData)) {
                throw new RuntimeException("XXL-JOB pageList 响应结构异常，无法确认远端任务集合: jobGroup=" + jobGroup);
            }
            Object inner = pageData.get("data");
            if (!(inner instanceof List<?> list)) {
                throw new RuntimeException("XXL-JOB pageList data 非列表，无法确认远端任务集合: jobGroup=" + jobGroup);
            }
            if (list.isEmpty()) {
                break;
            }
            // 重复页保护：若本页 id 集合与上一页完全相同，说明 Admin 未按 offset 翻页（典型为请求参数名不匹配），
            // 显式抛错终止，而不是无限累加重复行。
            List<Object> thisPageIds = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    thisPageIds.add(m.get("id"));
                }
            }
            if (lastPageIds != null && !thisPageIds.isEmpty() && thisPageIds.equals(lastPageIds)) {
                throw new RuntimeException("XXL-JOB pageList 检测到重复页，疑似分页参数与 Admin 实际字段不匹配，" +
                        "终止以避免无限循环: jobGroup=" + jobGroup + ", offset=" + offset);
            }
            lastPageIds = thisPageIds;
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    all.add((Map<String, Object>) m);
                }
            }
            Object totalField = pageData.get("total");
            long total = totalField instanceof Number n ? n.longValue() : -1L;
            if (total >= 0 && all.size() >= total) {
                break;
            }
            offset += pagesize;
        }
        return all;
    }

    /**
     * 按可信关联精确定位远端任务 ID：执行器组 + Handler + executorParam（内含 tenantId+本地 jobId）。
     * <p>不依赖任务名称（同名不同用户/租户会误伤）；pageList 异常时向上抛出，交由调用方保留恢复关联。</p>
     *
     * @param jobGroup        执行器组 ID
     * @param executorHandler Handler 名称
     * @param executorParam   执行器参数 JSON（与本地构造一致）
     * @return 命中的 XXL 任务 ID；确认不存在时返回 null
     */
    public Integer findJobIdByTrustedCorrelation(int jobGroup, String executorHandler, String executorParam) {
        for (Map<String, Object> job : pageList(jobGroup, executorHandler)) {
            String eh = str(job.get("executorHandler"));
            String ep = str(job.get("executorParam"));
            if (executorHandler != null && executorHandler.equals(eh) && sameParam(executorParam, ep)) {
                Object id = job.get("id");
                if (id instanceof Number n) {
                    return n.intValue();
                }
            }
        }
        return null;
    }

    /**
     * 按精确 ID 判断远端任务是否存在（完整分页后判定；pageList 异常向上抛出）。
     *
     * @param jobGroup 执行器组 ID
     * @param xxlJobId XXL 任务 ID
     * @return 存在返回 true，完整遍历后确认不存在返回 false
     */
    public boolean jobExistsById(int jobGroup, int xxlJobId) {
        for (Map<String, Object> job : pageList(jobGroup, null)) {
            Object id = job.get("id");
            if (id instanceof Number n && n.intValue() == xxlJobId) {
                return true;
            }
        }
        return false;
    }

    /** 归一化比较 executorParam：去空白后比对，容忍序列化空白差异。 */
    private static boolean sameParam(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.replaceAll("\\s+", "").equals(actual.replaceAll("\\s+", ""));
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
        Object code = body.get("code");
        if (!(code instanceof Number) || ((Number) code).intValue() != 200) {
            throw new RuntimeException("XXL-JOB 新增任务业务失败: code=" + code + ", msg=" + body.get("msg"));
        }
        Object data = body.get("data");
        if (data == null) {
            // 受理成功但无 ID：不可静默返回空串造成调用方回滚后孤儿，显式失败交由上层反查恢复
            throw new RuntimeException("XXL-JOB 新增任务未返回任务ID，需反查调度中心确认");
        }
        return data.toString();
    }

    /**
     * 更新已有任务的调度配置（cron/参数）。
     * <p>XXL-JOB 3.3.1 的 /jobinfo/update 要求全字段表单（实测：最小表单返回
     * “请输入任务描述”业务错）。先按 jobGroup+id 反查现有行全量配置，再仅覆盖
     * cron/executorParam 整体提交；业务失败（code!=200）抛异常，绝不静默假报成功。</p>
     *
     * @param jobGroup      执行器 ID（反查用）
     * @param xxlJobId      XXL-JOB 任务 ID
     * @param cron          新的 Cron 表达式
     * @param executorParam 新的执行器参数（JSON 字符串）
     */
    public void updateJob(int jobGroup, int xxlJobId, String cron, String executorParam) {
        Map<String, Object> existing = null;
        for (Map<String, Object> job : pageList(jobGroup, null)) {
            Object id = job.get("id");
            if (id instanceof Number num && num.intValue() == xxlJobId) {
                existing = job;
                break;
            }
        }
        if (existing == null) {
            throw new RuntimeException("XXL-JOB 待更新任务反查不到: id=" + xxlJobId + ", jobGroup=" + jobGroup);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(xxlJobId));
        form.add("jobGroup", str(existing.getOrDefault("jobGroup", jobGroup)));
        form.add("jobDesc", str(existing.get("jobDesc")));
        form.add("author", str(existing.getOrDefault("author", "admin")));
        form.add("alarmEmail", str(existing.get("alarmEmail")));
        form.add("scheduleType", "CRON");
        form.add("scheduleConf", cron);
        form.add("glueType", "BEAN");
        form.add("glueRemark", str(existing.getOrDefault("glueRemark", "DEFAULT")));
        form.add("childJobid", str(existing.get("childJobid")));
        form.add("executorHandler", str(existing.get("executorHandler")));
        form.add("executorParam", executorParam != null ? executorParam : str(existing.get("executorParam")));
        form.add("executorRouteStrategy", str(existing.getOrDefault("executorRouteStrategy", "FIRST")));
        form.add("executorBlockStrategy", str(existing.getOrDefault("executorBlockStrategy", "SERIAL_EXECUTION")));
        form.add("executorTimeout", str(existing.getOrDefault("executorTimeout", 0)));
        form.add("executorFailRetryCount", str(existing.getOrDefault("executorFailRetryCount", 0)));
        form.add("misfireStrategy", str(existing.getOrDefault("misfireStrategy", "DO_NOTHING")));
        form.add("triggerStatus", str(existing.get("triggerStatus")));
        postFormChecked("/jobinfo/update", form);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * 启动任务，使其按 Cron 表达式定期触发。
     *
     * @param jobId XXL-JOB 任务 ID
     */
    public void startJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ids[]", String.valueOf(jobId));
        postFormChecked("/jobinfo/start", form);
    }

    /**
     * 停止任务，暂停 Cron 调度。
     *
     * @param jobId XXL-JOB 任务 ID
     */
    public void stopJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ids[]", String.valueOf(jobId));
        postFormChecked("/jobinfo/stop", form);
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
        postFormChecked("/jobinfo/trigger", form);
    }

    /**
     * 删除任务，从 XXL-JOB 调度中心移除。
     *
     * @param jobId XXL-JOB 任务 ID
     */
    public void removeJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ids[]", String.valueOf(jobId));
        postFormChecked("/jobinfo/delete", form);
    }
}
