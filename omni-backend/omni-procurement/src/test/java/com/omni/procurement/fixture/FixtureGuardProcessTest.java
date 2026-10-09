package com.omni.procurement.fixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实进程行为保护测试（R04，T02 本地段 + main 入口退出码合同）。
 *
 * <p>通过 ProcessBuilder 启动独立 JVM（复用当前测试 classpath）：</p>
 * <ul>
 *   <li>双真实进程争抢同一物理目标锁，验证 OS 级互斥（FileChannel.tryLock）；
 *       后到者立即被拒绝（exit 3），不排队不静默共写</li>
 *   <li>持锁进程正常释放后，新进程可获取同一目标锁（可重入语义为"释放后"而非"并发中"）</li>
 *   <li>main 入口以真实子进程验证退出码合同：help=0；未知模式/缺必需输入/
 *       缺显式写开关/DB 连接失败=2，且这些失败都发生在任何业务写之前</li>
 * </ul>
 *
 * @author Omni-Stack WP-005 Repair
 */
@DisplayName("真实进程锁互斥与 main 入口退出码（R04）")
class FixtureGuardProcessTest {

    /** 子进程 java 可执行（运行 Runner 的 JVM 即 JDK25）；按操作系统解析二进制名，
     *  避免在 Linux 容器构建门禁下因写死 java.exe 而定位失败。 */
    private static final String JAVA = Path.of(System.getProperty("java.home"),
            "bin", System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "java.exe" : "java").toString();

    /** 子进程退出码：锁被占用拒绝。 */
    private static final int EXIT_LOCK_REJECTED = 3;

    /** 子进程退出码：参数错误。 */
    private static final int EXIT_USAGE = 2;

    private record ProcessResult(int exitCode, String output) {
    }

    private static String classpath() {
        return System.getProperty("java.class.path");
    }

    /** 运行子进程并捕获合并输出（readAllBytes 读尽后再 waitFor，避免管道缓冲死锁）。 */
    private static ProcessResult run(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] out;
        try (var in = process.getInputStream()) {
            out = in.readAllBytes();
        }
        int exit = process.waitFor();
        return new ProcessResult(exit, new String(out, StandardCharsets.UTF_8));
    }

    /** 构造子 JVM 命令：强制 UTF-8 stdout；classpath 经 @argfile 传递（见下方说明）。 */
    private static ProcessBuilder javaProcess(String mainClass, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(JAVA);
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        // surefire fork 的测试 classpath 可能超过 Windows CreateProcess 32KB 上限（error=206）。
        // 以 @argfile 传递 -cp（JDK 9+）；argfile 内路径统一正斜杠并整体加引号
        // （argfile 解析会把孤立反斜杠当转义吞掉，与 R05 验证过的 argfile 规则一致）。
        Path argFile = Files.createTempFile("r04-subproc-cp", ".args");
        String cp = classpath().replace('\\', '/');
        Files.write(argFile, List.of("-cp", "\"" + cp + "\""), StandardCharsets.UTF_8);
        argFile.toFile().deleteOnExit();
        command.add(("@" + argFile).replace('\\', '/'));
        command.add(mainClass);
        command.addAll(List.of(args));
        return new ProcessBuilder(command);
    }

    /** 构造工具子进程命令：显式剥离父环境 FIXTURE_* 变量保证测试前提可控。 */
    private static ProcessBuilder toolCommand(String mode, Map<String, String> env) throws IOException {
        ProcessBuilder pb = javaProcess(
                "com.omni.procurement.fixture.ProcurementFixtureTool", mode);
        Map<String, String> environment = pb.environment();
        environment.keySet().removeIf(name -> name.startsWith("FIXTURE_"));
        environment.putAll(env);
        return pb;
    }

    /** 轮询等待子进程输出文件包含期望串（超时抛断言失败并附实际内容）。 */
    private static void waitUntilOutputContains(Path file, String expected, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)
                    && Files.readString(file, StandardCharsets.UTF_8).contains(expected)) {
                return;
            }
            Thread.sleep(200);
        }
        String actual = Files.exists(file)
                ? Files.readString(file, StandardCharsets.UTF_8) : "<no output>";
        throw new AssertionError("等待子进程输出 '" + expected + "' 超时，实际: " + actual);
    }

    // ==================== T02：OS 级锁互斥 ====================

    @Test
    @DisplayName("双真实进程争抢同目标：后者立即被拒绝（T02）")
    void lockMutexAcrossRealProcesses(@TempDir Path tempDir) throws Exception {
        String targetKey = FixtureSupport.physicalTargetKey("uuid-proc", "omni_procurement", 1L);
        Path holderOut = tempDir.resolve("holder-out.txt");
        ProcessBuilder holderCommand = javaProcess(
                "com.omni.procurement.fixture.FixtureGuardDriver",
                "lock", tempDir.toString(), targetKey, "60000");
        holderCommand.redirectErrorStream(true);
        holderCommand.redirectOutput(holderOut.toFile());
        Process holderProcess = holderCommand.start();
        try {
            waitUntilOutputContains(holderOut, "LOCK-ACQUIRED", Duration.ofSeconds(60));
            ProcessResult result = run(javaProcess(
                    "com.omni.procurement.fixture.FixtureGuardDriver",
                    "lock", tempDir.toString(), targetKey, "100"));
            assertEquals(EXIT_LOCK_REJECTED, result.exitCode(),
                    "被占用目标必须立即拒绝而非等待: " + result.output());
            assertTrue(result.output().contains("LOCK-REJECTED"));
        } finally {
            holderProcess.destroyForcibly();
            holderProcess.waitFor();
        }
    }

    @Test
    @DisplayName("持锁进程正常释放后，新进程可获取同一目标锁")
    void lockReusableAfterRelease(@TempDir Path tempDir) throws Exception {
        String targetKey = FixtureSupport.physicalTargetKey("uuid-reuse", "omni_procurement", 1L);
        ProcessResult first = run(javaProcess(
                "com.omni.procurement.fixture.FixtureGuardDriver",
                "lock", tempDir.toString(), targetKey, "200"));
        assertEquals(0, first.exitCode(), "首次持锁应成功: " + first.output());
        assertTrue(first.output().contains("LOCK-RELEASED"), "应正常释放");

        ProcessResult second = run(javaProcess(
                "com.omni.procurement.fixture.FixtureGuardDriver",
                "lock", tempDir.toString(), targetKey, "100"));
        assertEquals(0, second.exitCode(), "释放后应可重取: " + second.output());
    }

    @Test
    @DisplayName("Driver 参数错误退出 " + EXIT_USAGE)
    void driverUsageErrorExitsTwo(@TempDir Path tempDir) throws Exception {
        ProcessResult result = run(javaProcess(
                "com.omni.procurement.fixture.FixtureGuardDriver", "bad-mode",
                tempDir.toString(), "key", "1"));
        assertEquals(EXIT_USAGE, result.exitCode());
        assertTrue(result.output().contains("usage:"));
    }

    // ==================== main 入口退出码合同 ====================

    @Test
    @DisplayName("main help 真实子进程退出 0 并输出合同字段")
    void toolHelpExitsZero() throws Exception {
        ProcessResult result = run(toolCommand("help", Map.of()));
        assertEquals(0, result.exitCode(), "help 必须零依赖成功: " + result.output());
        assertTrue(result.output().contains("\"fixtureVersion\""));
        assertTrue(result.output().contains("\"responseContract\""));
        assertTrue(result.output().contains("\"idempotencyContract\""));
    }

    @Test
    @DisplayName("main 未知模式退出 2 且输出结构化 FAILED")
    void toolUnknownModeExitsTwo() throws Exception {
        ProcessResult result = run(toolCommand("no-such-mode", Map.of()));
        assertEquals(2, result.exitCode());
        assertTrue(result.output().contains("\"status\" : \"FAILED\""),
                "失败必须输出结构化 JSON: " + result.output());
        assertTrue(result.output().contains("未知模式"));
    }

    @Test
    @DisplayName("apply 缺 FIXTURE_REGISTRY 在连接前退出 2")
    void toolApplyWithoutRegistryExitsTwoBeforeConnection() throws Exception {
        ProcessResult result = run(toolCommand("apply", Map.of()));
        assertEquals(2, result.exitCode());
        assertTrue(result.output().contains("缺少必填环境变量: FIXTURE_REGISTRY"),
                "应在读取 registry/连接 DB 之前失败: " + result.output());
    }

    @Test
    @DisplayName("apply 缺显式写开关在连接前退出 2")
    void toolApplyWithoutWriteSwitchExitsTwoBeforeConnection(@TempDir Path tempDir) throws Exception {
        Path registry = tempDir.resolve("registry.json");
        Files.writeString(registry, "{}", StandardCharsets.UTF_8);
        ProcessResult result = run(toolCommand("apply",
                Map.of("FIXTURE_REGISTRY", registry.toString())));
        assertEquals(2, result.exitCode());
        assertTrue(result.output().contains("FIXTURE_CONFIRM_WRITE"),
                "缺写开关必须在取 token/连接 DB 前失败: " + result.output());
        assertTrue(result.output().contains("\"status\" : \"FAILED\""));
    }

    @Test
    @DisplayName("inspect DB 连接失败退出 2（零业务写）")
    void toolInspectWithoutDbUrlExitsTwo(@TempDir Path tempDir) throws Exception {
        Path registry = tempDir.resolve("registry.json");
        Files.writeString(registry, "{}", StandardCharsets.UTF_8);
        ProcessResult result = run(toolCommand("inspect",
                Map.of("FIXTURE_REGISTRY", registry.toString())));
        assertEquals(2, result.exitCode(), "空 DB URL 必须连接失败退出 2: " + result.output());
        assertTrue(result.output().contains("\"status\" : \"FAILED\""));
    }
}
