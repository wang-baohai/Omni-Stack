package com.omni.base.it;

import com.omni.base.BaseApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

/**
 * WP-008 FJ 真实测试基类：绑定 F MySQL/Redis/XXL-Admin；执行由同库的 f-base 容器执行器真实承担（生产 Base+XXL 链）。
 * <p>守卫失败立即抛错；host 不启执行器避免不可达节点干扰 FIRST 路由。</p>
 *
 * @author Omni-Stack Team
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "WP008_F_ENABLED", matches = "1")
@SpringBootTest(classes = BaseApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.stream.function.autodetect=false",
                "spring.cloud.function.definition=",
                "omni.mqlog.relay.enabled=true",
                "omni.mqlog.relay.auto-register=false",
                "omni.internal.api.token=wp008f-it-internal-token",
                "xxl.job.executor.enabled=false",
                "xxl.job.executor.appname=omni-base",
                "xxl.job.executor.ip=host.docker.internal",
                "xxl.job.executor.port=19901",
                "xxl.job.executor.log-path=./target/xxljob-f",
                "spring.scheduling.enabled=false"
        })
public abstract class AbstractFjIT {

    /** M 生产 MySQL UUID。 */
    protected static final String M_DENY_UUID = "814a6039-ac2d-11f1-aa42-a6e45e360df1";

    /** 环境变量值：F 期望 UUID。 */
    protected static final String EXPECT_UUID = System.getenv("WP008_F_EXPECT_UUID");

    /** 动态属性：F 端点注入。 */
    @DynamicPropertySource
    static void fProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("WP008_F_BASE_MYSQL_URL"));
        registry.add("spring.datasource.username", () -> "omni_app");
        registry.add("spring.datasource.password", () -> System.getenv("WP008_F_OMNI_DB_PASSWORD"));
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "26379");
        registry.add("spring.data.redis.password", () -> System.getenv("WP008_F_REDIS_PASSWORD"));
        registry.add("spring.cloud.stream.rocketmq.binder.name-server", () -> "127.0.0.1:29876");
        registry.add("xxl.job.admin.addresses", () -> System.getenv("WP008_F_XXL_URL"));
        registry.add("xxl.job.admin.username", () -> System.getenv("WP008_F_XXL_USER"));
        registry.add("xxl.job.admin.password", () -> System.getenv("WP008_F_XXL_PASS"));
        registry.add("xxl.job.access-token", () -> System.getenv("WP008_F_XXL_ACCESS"));
    }

    /** 守卫。 */
    @BeforeAll
    static void guardTarget() throws Exception {
        if (!"1".equals(System.getenv("WP008_F_ENABLED"))) {
            throw new IllegalStateException("WP008_F_ENABLED!=1：FJ 真实测试不允许未绑定运行");
        }
        try (Connection c = DriverManager.getConnection(
                System.getenv("WP008_F_MYSQL_URL_ADMIN"), "root", System.getenv("WP008_F_ROOT_PASSWORD"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT @@server_uuid")) {
            rs.next();
            String uuid = rs.getString(1);
            if (M_DENY_UUID.equalsIgnoreCase(uuid) || !uuid.equalsIgnoreCase(EXPECT_UUID)) {
                throw new IllegalStateException("F 目标守卫失败: uuid=" + uuid);
            }
        }
    }

    /** 以 root 只读访问 xxl_job 管理库的 JdbcTemplate（omni_app 无权）。 */
    protected org.springframework.jdbc.core.JdbcTemplate adminJdbc() {
        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        System.getenv("WP008_F_MYSQL_URL_ADMIN"), "root",
                        System.getenv("WP008_F_ROOT_PASSWORD"));
        return new org.springframework.jdbc.core.JdbcTemplate(ds);
    }

    /**
     * XXL Admin 故障代理：转发到真实 admin，可按模式破坏响应。
     * 仅测试作用域（test scope），用于 FJ05-07 复现吞错/丢响应分支。
     */
    protected static final class FaultProxy implements AutoCloseable {

        /** 破坏模式。 */
        enum Mode {
            /** 仅对 /jobinfo/insert 读到完整响应后丢弃（模拟提交后丢响应），其余透传。 */
            DROP_INSERT_ONCE,
            /** 返回 HTTP200 + code=500 业务错误。 */
            BUSINESS_ERROR,
            /** 直接拒绝连接（模拟不可达）。 */
            UNREACHABLE,
            /** 全透明转发（默认）。 */
            PASS_THROUGH
        }

        private final ServerSocket server;
        private final int realPort;
        private final String realHost;
        private volatile Mode mode = Mode.PASS_THROUGH;
        private volatile boolean running = true;
        private final java.util.List<String> seenPaths = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        /** 构造代理。 */
        FaultProxy(String realHost, int realPort) throws IOException {
            this.realHost = realHost;
            this.realPort = realPort;
            this.server = new ServerSocket(0);
            Thread t = new Thread(this::loop, "wp008f-fault-proxy");
            t.setDaemon(true);
            t.start();
        }

        /** 代理监听端口。 */
        int port() {
            return server.getLocalPort();
        }

        /** 切换破坏模式。 */
        void setMode(Mode m) {
            this.mode = m;
        }

        /** 已观测到的请求路径（证明请求真实到达远端）。 */
        java.util.List<String> seenPaths() {
            return seenPaths;
        }

        private void loop() {
            while (running) {
                final Socket client;
                try {
                    // 不在 accept 循环里用 try-with-resources 关闭 client：否则 worker 尚未处理就被提前关闭，
                    // 导致登录透传 EOF、故障注入打不到目标写接口（RF04）。
                    client = server.accept();
                } catch (IOException e) {
                    return;
                }
                Thread worker = new Thread(() -> handle(client));
                worker.setDaemon(true);
                worker.start();
            }
        }

        private void handle(Socket client) {
            // worker 线程持有其连接的完整生命周期：try-with-resources 到方法结束才关。
            try (Socket c = client) {
                InputStream in = c.getInputStream();
                byte[] req = readHttp(in);
                String head = new String(req, 0, Math.min(req.length, 400), StandardCharsets.ISO_8859_1);
                String path = head.length() > 0 ? head.split(" ")[1] : "?";
                seenPaths.add(path);
                switch (mode) {
                    case UNREACHABLE -> {
                        return;
                    }
                    case BUSINESS_ERROR -> {
                        byte[] body = "{\"code\":500,\"msg\":\"wp008f injected business error\"}"
                                .getBytes(StandardCharsets.UTF_8);
                        String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                                + "Content-Length: " + body.length + "\r\n\r\n";
                        c.getOutputStream().write(resp.getBytes(StandardCharsets.ISO_8859_1));
                        c.getOutputStream().write(body);
                        c.getOutputStream().flush();
                        return;
                    }
                    case PASS_THROUGH -> {
                        try (Socket up = new Socket(realHost, realPort)) {
                            up.getOutputStream().write(req);
                            up.getOutputStream().flush();
                            byte[] resp = readHttp(up.getInputStream());
                            this.lastResponseLength = resp.length;
                            c.getOutputStream().write(resp);
                            c.getOutputStream().flush();
                        }
                    }
                    default -> {
                        boolean dropThis = path != null && path.contains("/jobinfo/insert") && !insertDropped;
                        try (Socket up = new Socket(realHost, realPort)) {
                            up.getOutputStream().write(req);
                            up.getOutputStream().flush();
                            byte[] resp = readHttp(up.getInputStream());
                            this.lastResponseLength = resp.length;
                            if (!dropThis) {
                                c.getOutputStream().write(resp);
                                c.getOutputStream().flush();
                            }
                        }
                        if (dropThis) {
                            insertDropped = true; // 仅丢一次，后续恢复透传供反查
                        }
                    }
                }
            } catch (Exception ignored) {
                // 代理线程静默，断言兜底
            }
        }

        private volatile int lastResponseLength = -1;
        private volatile boolean insertDropped = false;

        /** 最近一次成功转发并读到的远端响应长度（-1 表示未提交）。 */
        int lastResponseLength() {
            return lastResponseLength;
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws IOException {
            running = false;
            server.close();
        }

        private static byte[] readHttp(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            StringBuilder head = new StringBuilder();
            int b;
            boolean headerDone = false;
            while ((b = in.read()) != -1) {
                buf.write(b);
                head.append((char) b);
                int n = head.length();
                if (n >= 4 && head.charAt(n - 4) == '\r' && head.charAt(n - 3) == '\n'
                        && head.charAt(n - 2) == '\r' && head.charAt(n - 1) == '\n') {
                    headerDone = true;
                    break;
                }
            }
            if (!headerDone) {
                return buf.toByteArray();
            }
            String h = head.toString().toLowerCase(Locale.ROOT);
            int len = -1;
            boolean chunked = false;
            boolean close = false;
            for (String line : h.split("\r\n")) {
                int idx = line.indexOf(':');
                if (idx < 0) {
                    continue;
                }
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                if (k.equals("content-length")) {
                    try {
                        len = Integer.parseInt(v);
                    } catch (NumberFormatException ignore) {
                        len = -1;
                    }
                } else if (k.equals("transfer-encoding") && v.contains("chunked")) {
                    chunked = true;
                } else if (k.equals("connection") && v.contains("close")) {
                    close = true;
                }
            }
            // 优先按 chunked 逐块读取（保留原始分块帧于 buf，供客户端正确解码）；
            // 否则 content-length；否则仅当显式 connection: close 才 readAllBytes，避免 keep-alive 死等。
            if (chunked) {
                readChunkedBody(in, buf);
            } else if (len >= 0) {
                byte[] rest = in.readNBytes(len);
                buf.write(rest, 0, rest.length);
            } else if (close) {
                byte[] rest = in.readAllBytes();
                buf.write(rest, 0, rest.length);
            }
            return buf.toByteArray();
        }

        /**
         * 逐块读取 chunked 传输编码主体，将每一字节（含分块长度行、数据、尾部 CRLF）原样写入 buf，
         * 保证转发给客户端的响应帧结构不变（doLogin 等响应即为 chunked）。
         */
        private static void readChunkedBody(InputStream in, ByteArrayOutputStream buf) throws IOException {
            while (true) {
                ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
                int c;
                boolean sawContent = false;
                while ((c = in.read()) != -1) {
                    buf.write(c);
                    lineBuf.write(c);
                    sawContent = true;
                    if (c == '\n') {
                        break;
                    }
                }
                if (!sawContent) {
                    return;
                }
                String sizeLine = new String(lineBuf.toByteArray(), StandardCharsets.ISO_8859_1).trim();
                int semi = sizeLine.indexOf(';');
                if (semi >= 0) {
                    sizeLine = sizeLine.substring(0, semi);
                }
                int sz;
                try {
                    sz = Integer.parseInt(sizeLine.trim(), 16);
                } catch (NumberFormatException e) {
                    return;
                }
                if (sz == 0) {
                    // 最后一块：读尽 trailer/结束空行（读到 \n 为止）
                    int d;
                    while ((d = in.read()) != -1) {
                        buf.write(d);
                        if (d == '\n') {
                            break;
                        }
                    }
                    return;
                }
                byte[] data = in.readNBytes(sz);
                buf.write(data, 0, data.length);
                for (int i = 0; i < 2; i++) {
                    int d = in.read();
                    if (d == -1) {
                        return;
                    }
                    buf.write(d);
                }
            }
        }
    }
}
