package com.omni.procurement.fixture;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

/**
 * JUnit Platform 启动器（R04 守卫测试隔离运行用）：显式选择本包测试类，
 * 逐项输出成功/失败与摘要，失败时退出 1。
 *
 * <p>不进入标准 Maven surefire 流程（R08 全量构建才运行完整测试），
 * 用于 R04 在受控 classpath 下真实执行行为测试并保存原始结果。</p>
 *
 * @author Omni-Stack WP-005 Repair
 */
public final class FixtureGuardRunner {

    private static final int EXIT_OK = 0;

    private static final int EXIT_FAILED = 1;

    private FixtureGuardRunner() {
    }

    /**
     * 入口：无参数时选择 fixture 包全部测试类。
     *
     * @param args 可选的测试类简单名列表
     */
    public static void main(String[] args) {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
        if (args.length == 0) {
            builder.selectors(DiscoverySelectors.selectPackage("com.omni.procurement.fixture"));
        } else {
            for (String arg : args) {
                builder.selectors(DiscoverySelectors.selectClass(
                        "com.omni.procurement.fixture." + arg));
            }
        }
        LauncherDiscoveryRequest request = builder.build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        out.println("TESTS-RUN=" + summary.getTestsSucceededCount()
                + " FAILED=" + summary.getTestsFailedCount()
                + " SKIPPED=" + summary.getTestsSkippedCount()
                + " TOTAL=" + summary.getTestsFoundCount());
        summary.printFailuresTo(out);
        summary.printTo(out);
        out.flush();
        System.exit(summary.getTestsFailedCount() == 0 ? EXIT_OK : EXIT_FAILED);
    }
}
