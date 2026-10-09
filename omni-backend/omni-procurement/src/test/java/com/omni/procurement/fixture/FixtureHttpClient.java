package com.omni.procurement.fixture;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购 fixture 工具受控 HTTP 客户端（WP-005 返修合同 3.4 节）。
 *
 * <p>统一响应合同：</p>
 * <ul>
 *   <li>除 help 外所有请求均携带 Bearer（含品类列表，不关闭权限兼容匿名调用）</li>
 *   <li>先验 HTTP 状态码，再验 R.code，再验 data 结构；任何失败抛 {@link ApiException}</li>
 *   <li>DELETE 返回 R&lt;Void&gt;：data 可为 null，不把 data 空当失败</li>
 *   <li>物料列表用 page/size 且 size≤100（正式 ProcPageQuery 合同），keyword 是模糊匹配，
 *       编码查询结果必须客户端精确比较并检查完整分页</li>
 *   <li>业务失败/解析失败不得退化为空 records</li>
 * </ul>
 *
 * @author Omni-Stack WP-005 Repair
 */
public final class FixtureHttpClient {

    /** 分页合同上限（正式 ProcPageQuery @Max(100)）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /** R.ok 的业务码。 */
    private static final int R_OK = 200;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String token;

    /**
     * 构造受控客户端。
     *
     * @param baseUrl API 基础地址
     * @param token Bearer token（必需）
     */
    public FixtureHttpClient(String baseUrl, String token) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * API 调用异常：携带 HTTP 状态与 R.code。
     */
    public static final class ApiException extends IOException {

        private static final long serialVersionUID = 1L;

        private final int httpStatus;
        private final Integer rCode;
        private final String responseBody;

        ApiException(int httpStatus, Integer rCode, String responseBody, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.rCode = rCode;
            this.responseBody = responseBody;
        }

        /**
         * HTTP 状态码。
         *
         * @return 状态码；未到达 HTTP 层为 -1
         */
        public int httpStatus() {
            return httpStatus;
        }

        /**
         * R.code。
         *
         * @return 业务码；未解析为 null
         */
        public Integer rCode() {
            return rCode;
        }

        /**
         * 原始响应体。
         *
         * @return 响应体
         */
        public String responseBody() {
            return responseBody;
        }
    }

    /**
     * 查询品类树（R&lt;List&lt;CategoryVO&gt;&gt;）。
     *
     * @return 品类树
     * @throws IOException HTTP/业务/解析失败
     */
    public List<Map<String, Object>> listCategories() throws IOException {
        Map<String, Object> body = execute("GET", "/api/procurement/material/category/list", null);
        Object data = body.get("data");
        if (!(data instanceof List<?> list)) {
            throw new ApiException(200, R_OK, null,
                    "品类列表 data 不是数组，疑似业务失败被包装为空");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(asMap(item, "品类树节点"));
        }
        return result;
    }

    /**
     * 物料详情（R&lt;MaterialVO&gt;）。
     *
     * @param id 物料 ID（十进制字符串，保持精度）
     * @return 详情；R.code=404 且 data 空时返回 null（配合 DB 历史判定）
     * @throws IOException HTTP/业务/解析失败
     */
    public Map<String, Object> getMaterial(String id) throws IOException {
        try {
            Map<String, Object> body = execute("GET", "/api/procurement/material/" + id, null);
            Object data = body.get("data");
            if (data == null) {
                throw new ApiException(200, R_OK, null,
                        "物料详情 data 为空但 HTTP/R.code 正常，无法确认存在性");
            }
            return asMap(data, "物料详情");
        } catch (ApiException e) {
            // 404 只有在确认是"不存在"语义时才由调用方结合 DB 历史判定；
            // 这里区分权限/路由故障与业务不存在
            if (e.httpStatus() == 404 && e.rCode() == null) {
                return null;
            }
            if (e.rCode() != null && e.rCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 创建物料（R&lt;MaterialVO&gt;）。
     *
     * @param requestBody 创建请求体
     * @return 新物料 VO（含 id/version）
     * @throws IOException HTTP/业务/解析失败
     */
    public Map<String, Object> createMaterial(Map<String, Object> requestBody) throws IOException {
        Map<String, Object> body = execute("POST", "/api/procurement/material", requestBody);
        Object data = body.get("data");
        if (data == null) {
            throw new ApiException(200, R_OK, null, "创建物料响应 data 为空");
        }
        return asMap(data, "创建物料响应");
    }

    /**
     * 删除物料（R&lt;Void&gt;，data 允许为空）。
     *
     * @param id 物料 ID
     * @param version 乐观锁版本（调用方必须先取最新值，不得用 registry 旧值）
     * @throws IOException HTTP/业务失败
     */
    public void deleteMaterial(String id, int version) throws IOException {
        execute("DELETE", "/api/procurement/material/" + id + "?version=" + version, null);
    }

    /**
     * 按 keyword 分页查询并做完整分页拉取，客户端对编码做精确比较。
     *
     * <p>keyword 是模糊匹配（LIKE %kw%），所以返回记录必须逐条精确比对 materialCode。
     * total 与已拉取记录数核对，分页拉满为止；HTTP/业务失败直接抛异常，
     * 不退化为空列表。</p>
     *
     * @param keyword 关键词（会被 URL 编码）
     * @param maxRecords 防御性上限（防止 total 异常导致的死循环）
     * @return materialCode 精确等于 keyword 的记录列表
     * @throws IOException HTTP/业务/解析失败
     */
    public List<Map<String, Object>> listMaterialsByExactCode(String keyword, int maxRecords)
            throws IOException {
        List<Map<String, Object>> exact = new ArrayList<>();
        int page = 1;
        int fetched = 0;
        while (true) {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String path = "/api/procurement/material/list?page=" + page
                    + "&size=" + MAX_PAGE_SIZE + "&keyword=" + encoded;
            Map<String, Object> body = execute("GET", path, null);
            Object data = body.get("data");
            if (!(data instanceof Map<?, ?> pageData)) {
                throw new ApiException(200, R_OK, null,
                        "物料列表 data 不是对象，疑似业务失败被包装为空 records");
            }
            Object totalObj = ((Map<?, ?>) pageData).get("total");
            Object recordsObj = ((Map<?, ?>) pageData).get("records");
            if (!(recordsObj instanceof List<?> records)) {
                throw new ApiException(200, R_OK, null, "物料列表 records 缺失");
            }
            long total = totalObj instanceof Number n ? n.longValue() : -1;
            for (Object item : records) {
                Map<String, Object> row = asMap(item, "物料列表记录");
                if (keyword.equals(row.get("materialCode"))) {
                    exact.add(row);
                }
            }
            fetched += records.size();
            if (records.isEmpty() || fetched >= total || fetched >= maxRecords) {
                break;
            }
            page++;
        }
        return exact;
    }

    private Map<String, Object> execute(String method, String path,
                                        Map<String, Object> requestBody) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
        HttpRequest request;
        if ("POST".equals(method)) {
            String json = FixtureSupport.MAPPER.writeValueAsString(requestBody);
            request = builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
        } else if ("DELETE".equals(method)) {
            request = builder.DELETE().build();
        } else {
            request = builder.GET().build();
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(-1, null, null, "HTTP 调用被中断: " + path);
        } catch (IOException e) {
            // 超时/断连：请求可能已到达服务端，调用方按 UNKNOWN 处理
            throw new ApiException(-1, null, null, "HTTP 调用失败（结果不确定）: " + path
                    + " 原因: " + e.getMessage());
        }

        int status = response.statusCode();
        if (status != 200) {
            throw new ApiException(status, null, response.body(),
                    "HTTP " + status + " 于 " + path);
        }

        Map<String, Object> body;
        try {
            body = FixtureSupport.MAPPER.readValue(response.body(), Map.class);
        } catch (IOException e) {
            throw new ApiException(status, null, response.body(),
                    "响应 JSON 解析失败于 " + path + ": " + e.getMessage());
        }
        Object codeObj = body.get("code");
        if (!(codeObj instanceof Number code)) {
            throw new ApiException(status, null, response.body(),
                    "响应缺少 R.code 于 " + path);
        }
        if (code.intValue() != R_OK) {
            throw new ApiException(status, code.intValue(), response.body(),
                    "业务失败 R.code=" + code.intValue() + " 于 " + path);
        }
        return body;
    }

    private static Map<String, Object> asMap(Object value, String context) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(context + " 不是对象结构");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
