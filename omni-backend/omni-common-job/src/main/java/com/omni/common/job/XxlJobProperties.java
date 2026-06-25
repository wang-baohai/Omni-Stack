package com.omni.common.job;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-JOB 执行器配置属性。
 * <p>
 * 通过 {@code xxl.job} 前缀绑定 {@code application.yml} 中的配置项，
 * 包含调度中心地址、认证信息、执行器网络参数及日志策略。
 * 由 {@link com.omni.common.job.config.XxlJobAutoConfiguration} 自动装配时消费。</p>
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * xxl:
 *   job:
 *     access-token: your-secret-token
 *     admin:
 *       addresses: http://xxl-job-admin:18080/xxl-job-admin
 *       username: admin
 *       password: 123456
 *     executor:
 *       appname: omni-auth
 *       port: 9999
 *       log-retention-days: 30
 * }</pre>
 *
 * @author Omni-Stack Team
 * @see com.omni.common.job.config.XxlJobAutoConfiguration
 */
@Data
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /** 调度中心配置（{@link Admin}） */
    private Admin admin = new Admin();

    /**
     * 执行器与调度中心之间的通信令牌。
     * <p>两端必须配置相同值，否则调度中心拒绝执行器注册。</p>
     */
    private String accessToken = "";

    /** 执行器配置（{@link Executor}） */
    private Executor executor = new Executor();

    /**
     * XXL-JOB 调度中心配置。
     * <p>包含调度中心地址和管理员认证信息，用于执行器注册和 HTTP API 调用。</p>
     */
    @Data
    public static class Admin {

        /** 调度中心地址（多个用逗号分隔） */
        private String addresses = "http://127.0.0.1:18080/xxl-job-admin";

        /** 管理员用户名（用于 HTTP API 调用认证） */
        private String username = "admin";

        /** 管理员密码 */
        private String password = "123456";
    }

    /**
     * XXL-JOB 执行器配置。
     * <p>包含执行器网络参数（AppName、地址、IP、端口）及日志策略。</p>
     */
    @Data
    public static class Executor {

        /** 是否启用执行器（默认开启） */
        private boolean enabled = true;

        /** 执行器 AppName（为空则取 spring.application.name） */
        private String appname = "";

        /** 执行器地址（为空则自动获取） */
        private String address = "";

        /** 执行器 IP（为空则自动获取） */
        private String ip = "";

        /** 执行器端口 */
        private int port = 9999;

        /** 日志路径 */
        private String logPath = "/data/applogs/xxl-job/jobhandler";

        /** 日志保留天数（-1 表示永久保留） */
        private int logRetentionDays = 30;
    }
}
