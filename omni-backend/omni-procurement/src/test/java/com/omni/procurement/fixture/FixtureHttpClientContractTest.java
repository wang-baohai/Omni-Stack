package com.omni.procurement.fixture;

import com.omni.procurement.fixture.FixtureHttpClient.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FixtureHttpClient 响应合同测试（R04，T03/T04 本地段）。
 *
 * <p>使用 JDK 内置 HttpServer 绑定 127.0.0.1 随机端口（仅本机模拟地址，
 * 不触任何真实服务），真实执行 {@link FixtureHttpClient} 的传输与解析路径：
 * Bearer 必带（含品类列表）、HTTP→R.code→data 三层校验、R&lt;Void&gt; 允许空 data、
 * 模糊 keyword 客户端精确比较与完整分页（page/size≤100）、
 * 业务/解析失败不退化为空 records、超 2^53 的 ID 精度保留。</p>
 *
 * @author Omni-Stack WP-005 Repair
 */
@DisplayName("FixtureHttpClient 响应合同（合成 HTTP，仅本机）")
class FixtureHttpClientContractTest {

    /** 一次被记录的请求（method/path/query/Authorization）。 */
    private record Recorded(String method, String path, String query, String authorization) {
    }

    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 回复 JSON 响应体；status=404 且 body 空时发送无响应体（HTTP 语义 404）。 */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (status != 200 && body.isEmpty()) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String param(String query, String name) {
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv.length > 1 ? kv[1] : "";
            }
        }
        return "";
    }

    // ==================== Bearer 合同 ====================

    @Test
    @DisplayName("品类列表请求必须携带 Bearer（权限不关闭兼容匿名）")
    void categoriesCarryBearerToken() throws Exception {
        ConcurrentLinkedQueue<Recorded> requests = new ConcurrentLinkedQueue<>();
        HttpServer server = startServer(exchange -> {
            requests.add(new Recorded(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            respond(exchange, 200, "{\"code\":200,\"msg\":\"ok\",\"data\":[]}");
        });
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok-abc");
            List<Map<String, Object>> categories = client.listCategories();
            assertEquals(0, categories.size());
        } finally {
            server.stop(0);
        }
        assertEquals(1, requests.size());
        Recorded recorded = requests.iterator().next();
        assertEquals("GET", recorded.method());
        assertEquals("/api/procurement/material/category/list", recorded.path());
        assertEquals("Bearer tok-abc", recorded.authorization(),
                "品类请求同样必须携带 Bearer");
    }

    // ==================== 三层校验 ====================

    @Test
    @DisplayName("HTTP 非 200 抛 ApiException 且携带状态码")
    void httpErrorRaisesApiException() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 500, "boom"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class, client::listCategories);
            assertEquals(500, ex.httpStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP 401 未认证显式拒绝，HTTP 层先于 R 层")
    void http401RaisesApiException() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 401,
                "{\"code\":401,\"msg\":\"unauthorized\"}"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "invalid-token");
            ApiException ex = assertThrows(ApiException.class, client::listCategories);
            assertEquals(401, ex.httpStatus());
            assertNull(ex.rCode(), "HTTP 层先拒绝，不进入 R 解码（rCode 为 null）");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("R.code 非 200 抛 ApiException 且携带业务码")
    void businessErrorCodeRaisesApiException() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 200,
                "{\"code\":409,\"msg\":\"conflict\"}"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class, client::listCategories);
            assertEquals(200, ex.httpStatus());
            assertEquals(409, ex.rCode(), "业务码必须保留供调用方分类");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("响应体不是 JSON 抛 ApiException 而非静默空结果")
    void malformedBodyRaisesApiException() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 200, "<html>gateway</html>"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class, client::listCategories);
            assertTrue(ex.getMessage().contains("JSON 解析失败"),
                    "解析失败必须显式报错: " + ex.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("品类 data 非数组（业务失败包装空）必须失败")
    void categoriesDataNotListRejected() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 200,
                "{\"code\":200,\"msg\":\"ok\"}"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class, client::listCategories);
            assertTrue(ex.getMessage().contains("不是数组"),
                    "业务失败包装为空必须显式报错: " + ex.getMessage());
        } finally {
            server.stop(0);
        }
    }

    // ==================== getMaterial / deleteMaterial ====================

    @Test
    @DisplayName("getMaterial 双 404 语义返回 null（HTTP 404 与 R.code=404）")
    void getMaterialNotFoundSemantics() throws Exception {
        HttpServer server = startServer(ex -> {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/material/404a")) {
                respond(ex, 404, "");
            } else {
                respond(ex, 200, "{\"code\":404,\"msg\":\"不存在\"}");
            }
        });
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            assertNull(client.getMaterial("404a"), "HTTP 404 视为不存在");
            assertNull(client.getMaterial("404b"), "R.code=404 视为不存在");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("getMaterial 非 404 失败重抛不吞异常")
    void getMaterialOtherErrorRethrows() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 403, "forbidden"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class, () -> client.getMaterial("1"));
            assertEquals(403, ex.httpStatus(), "权限/路由故障不得当作不存在");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("getMaterial data 为空但 R 正常必须失败（无法确认存在性）")
    void getMaterialEmptyDataRejected() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 200,
                "{\"code\":200,\"msg\":\"ok\"}"));
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class, () -> client.getMaterial("1"));
            assertTrue(ex.getMessage().contains("data 为空"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("deleteMaterial R<Void> data 为空不算失败且路径带 version")
    void deleteMaterialAcceptsVoidData() throws Exception {
        ConcurrentLinkedQueue<Recorded> requests = new ConcurrentLinkedQueue<>();
        HttpServer server = startServer(ex -> {
            requests.add(new Recorded(ex.getRequestMethod(), ex.getRequestURI().toString(),
                    null, ex.getRequestHeaders().getFirst("Authorization")));
            respond(ex, 200, "{\"code\":200,\"msg\":\"ok\"}");
        });
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            assertDoesNotThrow(() -> client.deleteMaterial("55", 3),
                    "R<Void> 的空 data 不是失败");
        } finally {
            server.stop(0);
        }
        Recorded recorded = requests.iterator().next();
        assertEquals("DELETE", recorded.method(), "应为 DELETE 方法");
        assertTrue(recorded.path().contains("/api/procurement/material/55?version=3"),
                "删除必须携带乐观锁 version: " + recorded.path());
    }

    // ==================== 分页与精确比较 ====================

    @Test
    @DisplayName("模糊 keyword 完整分页后仅保留精确匹配且核对 total")
    void exactCodePaginationAndFiltering() throws Exception {
        ConcurrentLinkedQueue<Recorded> requests = new ConcurrentLinkedQueue<>();
        String exactCode = "WP005FIX_ASET_TESTWP005AGEN1";
        HttpServer server = startServer(ex -> {
            String query = ex.getRequestURI().getRawQuery();
            requests.add(new Recorded(ex.getRequestMethod(),
                    ex.getRequestURI().getPath() + "?" + query,
                    query, ex.getRequestHeaders().getFirst("Authorization")));
            String page = param(query, "page");
            StringBuilder records = new StringBuilder();
            if ("1".equals(page)) {
                for (int i = 0; i < 100; i++) {
                    if (i > 0) {
                        records.append(',');
                    }
                    records.append("{\"id\":").append(i + 1)
                            .append(",\"materialCode\":\"NOISE-").append(i).append("\"}");
                }
            } else {
                records.append("{\"id\":999,\"materialCode\":\"").append(exactCode).append("\"}");
            }
            respond(ex, 200, "{\"code\":200,\"msg\":\"ok\",\"data\":{\"total\":101,"
                    + "\"records\":[" + records + "]}}");
        });
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            List<Map<String, Object>> exact = client.listMaterialsByExactCode(exactCode, 1000);
            assertEquals(1, exact.size(), "模糊噪声不得混入精确结果");
            assertEquals(exactCode, exact.get(0).get("materialCode"));
        } finally {
            server.stop(0);
        }
        assertEquals(2, requests.size(), "应按 page=1,2 size=100 翻满两页");
        List<Recorded> all = new ArrayList<>(requests);
        assertTrue(all.get(0).path().contains("page=1") && all.get(0).path().contains("size=100"));
        assertTrue(all.get(1).path().contains("page=2"));
    }

    @Test
    @DisplayName("分页中途失败必须抛异常而非返回部分结果")
    void listFailureNotDegradedToPartial() throws Exception {
        HttpServer server = startServer(ex -> {
            String page = param(ex.getRequestURI().getRawQuery(), "page");
            if ("1".equals(page)) {
                StringBuilder records = new StringBuilder();
                for (int i = 0; i < 100; i++) {
                    if (i > 0) {
                        records.append(',');
                    }
                    records.append("{\"id\":").append(i + 1)
                            .append(",\"materialCode\":\"N").append(i).append("\"}");
                }
                respond(ex, 200, "{\"code\":200,\"msg\":\"ok\",\"data\":{\"total\":300,"
                        + "\"records\":[" + records + "]}}");
            } else {
                respond(ex, 500, "boom");
            }
        });
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            ApiException ex = assertThrows(ApiException.class,
                    () -> client.listMaterialsByExactCode("WP005FIX_ANY", 1000));
            assertEquals(500, ex.httpStatus(), "翻页失败不得退化为部分列表");
        } finally {
            server.stop(0);
        }
    }

    // ==================== createMaterial 与 ID 精度 ====================

    @Test
    @DisplayName("createMaterial 解析 data 返回新物料且大 ID 精度保留")
    void createMaterialParsesVoWithLongId() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = startServer(ex -> {
            bodyRef.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(ex, 200, "{\"code\":200,\"msg\":\"ok\",\"data\":{\"id\":9007199254740993,"
                    + "\"version\":0,\"materialCode\":\"WP005FIX_ASET_X\"}}");
        });
        try {
            FixtureHttpClient client = new FixtureHttpClient(baseUrl(server), "tok");
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("categoryId", 5L);
            request.put("materialCode", "WP005FIX_ASET_X");
            Map<String, Object> vo = client.createMaterial(request);
            assertEquals("9007199254740993", String.valueOf(vo.get("id")),
                    "超 2^53 的 ID 必须精确保留");
            assertTrue(bodyRef.get().contains("WP005FIX_ASET_X"), "请求体应包含编码");
        } finally {
            server.stop(0);
        }
    }
}
