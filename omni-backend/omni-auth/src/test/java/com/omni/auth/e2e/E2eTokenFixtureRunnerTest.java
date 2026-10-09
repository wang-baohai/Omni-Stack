package com.omni.auth.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * WP-014 新增：以 test-only 运行器驱动 {@link E2eTokenFixture}。
 *
 * <p>该类不改变签发语义，只是把夹具入口挂到 surefire 已解析好的 test classpath 上，
 * 用于本地仓库缺少 exec-maven-plugin、无法用 exec:java 启动 main 方法的环境。
 *
 * <p>仅当环境变量 {@code WP014_FIXTURE_RUN=true} 时才执行，普通构建与 CI 不受影响；
 * 仍然不启动 HTTP 服务、不校验或绕过 CAPTCHA、不读取用户密码、不写仓库内路径。
 */
@EnabledIfEnvironmentVariable(named = "WP014_FIXTURE_RUN", matches = "true")
class E2eTokenFixtureRunnerTest {

    /**
     * 委托夹具 main 完成签发；任何失败都原样抛出，不做静默降级。
     *
     * @throws Exception 数据库、Redis、密钥或文件操作失败
     */
    @Test
    void issueControlledTokens() throws Exception {
        E2eTokenFixture.main(new String[0]);
    }
}
