package com.omni.workflow.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.WorkflowModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FW09：真实 Workflow HTTP 提交成功后代理丢响应 → 调用方 UNKNOWN → 同 requestId 重试唯一恢复。
 * <p>被调用方为真实启动的 WorkflowApplication（随机端口）+ F 真实库；代理仅影响测试连接，
 * 属 F_REAL 证据，不属于 mock 返回成功。</p>
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW09 提交后丢响应恢复")
class Fw09LostResponseIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper om;

    /** 代理转发并完整读取响应（远端已提交）后，对调用方丢弃响应；重试必须重放同一实例。 */
    @Test
    @DisplayName("FW09-1 丢响应UNKNOWN后同键恢复")
    void lostResponseRecoversBySameRequestId() throws Exception {
        String key = uniqKey("wp008f-lost");
        PublishResult pr = withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            return modelService.publishModel(m.getId(), "wp008f");
        });
        String requestId = "wp008f-lost-" + UUID.randomUUID();
        String bizKey = "WP008F-LOST-" + key;
        String body = json(requestId, pr.getVersionId(), bizKey);

        // 1) 经“提交后丢响应”代理发起真实调用
        AtomicReference<byte[]> committed = new AtomicReference<>();
        int proxyPort;
        try (ServerSocket proxy = new ServerSocket(0)) {
            proxyPort = proxy.getLocalPort();
            AtomicBoolean done = new AtomicBoolean();
            Thread t = new Thread(() -> {
                try (Socket client = proxy.accept()) {
                    InputStream in = client.getInputStream();
                    byte[] req = readHttp(in);
                    // 转发到真实应用并读完响应（代表远端已提交）
                    try (Socket up = new Socket("127.0.0.1", serverPort)) {
                        up.getOutputStream().write(req);
                        up.getOutputStream().flush();
                        committed.set(readHttp(up.getInputStream()));
                    }
                    // 关键：不把响应写回客户端，直接断开 → 调用方 UNKNOWN
                    client.close();
                } catch (Exception ignored) {
                    // 代理侧异常由断言兜底
                } finally {
                    done.set(true);
                }
            });
            t.setDaemon(true);
            t.start();

            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + proxyPort + "/api/internal/workflow/process-instance/start").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Tenant-Id", "1");
            conn.setRequestProperty("X-Internal-Token", "wp008f-it-internal-token");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            assertThatThrownBy(() -> conn.getResponseCode())
                    .as("代理丢响应，调用方必须观察到连接异常而非成功")
                    .isInstanceOf(java.io.IOException.class);
            t.join(15000);
        }
        assertThat(committed.get()).as("远端真实提交并生成了响应").isNotNull();
        String respText = new String(committed.get(), StandardCharsets.UTF_8);
        assertThat(respText).as("远端应返回成功响应: %s",
                respText.substring(0, Math.min(respText.length(), 300)))
                .startsWith("HTTP/1.1 200").contains("processInstanceId");

        // 2) DB 侧确认唯一 STARTED 记录与实例
        String pid = jdbc.queryForObject(
                "SELECT process_instance_id FROM omni_workflow.wf_process_start_request WHERE request_id=?",
                String.class, requestId);
        assertThat(pid).isNotBlank();
        Integer started = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_workflow.wf_process_start_request WHERE request_id=? AND status='STARTED'",
                Integer.class, requestId);
        assertThat(started).isEqualTo(1);
        assertThat(extCount(bizKey)).isEqualTo(1);

        // 3) 直连重试同 requestId → 重放同实例，无第二实例
        HttpURLConnection retry = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + serverPort + "/api/internal/workflow/process-instance/start").toURL().openConnection();
        retry.setRequestMethod("POST");
        retry.setRequestProperty("Content-Type", "application/json");
        retry.setRequestProperty("X-Tenant-Id", "1");
        retry.setRequestProperty("X-Internal-Token", "wp008f-it-internal-token");
        retry.setDoOutput(true);
        try (OutputStream os = retry.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertThat(retry.getResponseCode()).isEqualTo(200);
        var resp = om.readTree(retry.getInputStream());
        assertThat(resp.path("data").path("processInstanceId").asText()).isEqualTo(pid);
        assertThat(resp.path("data").path("replayed").asBoolean()).isTrue();
        assertThat(extCount(bizKey)).isEqualTo(1);

        // 4) 撤回清理，记录时间点用于交接
        jdbc.queryForList(
                "SELECT create_time, update_time FROM omni_workflow.wf_process_start_request WHERE request_id=?",
                requestId);
    }

    private long extCount(String bizKey) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_workflow.wf_process_instance_ext WHERE business_key=?",
                Long.class, bizKey);
        return n == null ? 0 : n;
    }

    private String json(String requestId, Long versionId, String bizKey) throws Exception {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("requestId", requestId);
        m.put("tenantId", 1L);
        m.put("modelVersionId", versionId);
        m.put("businessType", "WP008FTEST");
        m.put("businessKey", bizKey);
        m.put("startUserId", 2210L);
        m.put("startUserName", "f1_req");
        m.put("title", "fw09");
        return om.writeValueAsString(m);
    }

    /** 读取一个完整 HTTP/1.1 响应或请求（支持 Content-Length 与 chunked 简易处理）。 */
    private static byte[] readHttp(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        StringBuilder head = new StringBuilder();
        // 读到头结束（chunked 响应在此场景体量小，直接读到流结束/超时）
        while ((b = in.read()) != -1) {
            buf.write(b);
            head.append((char) b);
            if (buf.size() > 8 && head.substring(Math.max(0, head.length() - 400)).contains("\r\n\r\n")) {
                break;
            }
        }
        String h = head.toString();
        int len = -1;
        for (String line : h.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(line.split(":")[1].trim());
            }
        }
        if (len >= 0) {
            byte[] rest = in.readNBytes(len);
            buf.write(rest);
        } else if (h.toLowerCase().contains("transfer-encoding: chunked")) {
            // 读到流结束
            byte[] rest = in.readAllBytes();
            buf.write(rest);
        }
        return buf.toByteArray();
    }
}
