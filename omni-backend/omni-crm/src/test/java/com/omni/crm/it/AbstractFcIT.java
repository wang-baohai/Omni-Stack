package com.omni.crm.it;

import com.omni.common.service.datascope.DataScopeResolver;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.crm.CrmApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * WP-008 FC 真实测试基类：真实 CrmApplication 上下文 + F 正式 crm schema + 真实 f-auth 数据范围解析。
 * <p>身份经 ServiceIdentityContext 注入（等价 ServiceIdentityFilter 产物），
 * @ServiceDataScope AOP 与 Feign→f-auth 均为真实调用；UUID 守卫拒绝误目标。</p>
 *
 * @author Omni-Stack Team
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "WP008_F_ENABLED", matches = "1")
@SpringBootTest(classes = CrmApplication.class,
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.stream.function.autodetect=false",
                "spring.cloud.function.definition=",
                "xxl.job.executor.enabled=false",
                "omni.mqlog.relay.enabled=false",
                "omni.mqlog.relay.auto-register=false",
                "omni.service.internal-api.enabled=true",
                "omni.service.internal-api.token=${WP008_F_INTERNAL_TOKEN:wp008f-it-internal-token}",
                "spring.scheduling.enabled=false"
        })
public abstract class AbstractFcIT {

    /** M 生产 UUID。 */
    protected static final String M_DENY_UUID = "814a6039-ac2d-11f1-aa42-a6e45e360df1";

    /**
     * 宿主测试进程无法访问 Nacos 注册的容器内网 IP。提供 @Primary 静态 DiscoveryClient，
     * 将 omni-auth 固定到宿主映射端口 28100，LoadBalancer 的 DiscoveryClientServiceInstanceListSupplier
     * 会使用该 @Primary Bean，从而真实经 HTTP 调用运行中的 f-auth。
     */
    @TestConfiguration
    static class AuthDiscoveryConfig {
        @Bean
        @Primary
        DiscoveryClient omniAuthStaticDiscovery() {
            DefaultServiceInstance inst = new DefaultServiceInstance(
                    "omni-auth", "wp008f-static-1", "127.0.0.1", 28100, false);
            return new DiscoveryClient() {
                @Override
                public String description() {
                    return "wp008f-static-omni-auth";
                }

                @Override
                public List<ServiceInstance> getInstances(String serviceId) {
                    return "omni-auth".equals(serviceId) ? List.of(inst) : List.of();
                }

                @Override
                public List<String> getServices() {
                    return List.of("omni-auth");
                }
            };
        }
    }

    /** 动态属性。 */
    @DynamicPropertySource
    static void fProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("WP008_F_CRM_MYSQL_URL"));
        registry.add("spring.datasource.username", () -> "omni_app");
        registry.add("spring.datasource.password", () -> System.getenv("WP008_F_OMNI_DB_PASSWORD"));
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "26379");
        registry.add("spring.data.redis.password", () -> System.getenv("WP008_F_REDIS_PASSWORD"));
        registry.add("spring.cloud.stream.rocketmq.binder.name-server", () -> "127.0.0.1:29876");
        // 宿主测试进程无法访问 Nacos 注册的容器内网 IP；关闭发现，用 simple discovery 将 omni-auth 固定到宿主映射端口。
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.omni-auth[0].uri", () -> "http://127.0.0.1:28100");
        registry.add("feign.client.config.omni-auth.connect-timeout", () -> "5000");
    }

    /** 守卫。 */
    @BeforeAll
    static void guardTarget() throws Exception {
        if (!"1".equals(System.getenv("WP008_F_ENABLED"))) {
            throw new IllegalStateException("WP008_F_ENABLED!=1：FC 真实测试不允许未绑定运行");
        }
        try (Connection c = DriverManager.getConnection(
                System.getenv("WP008_F_MYSQL_URL_ADMIN"), "root", System.getenv("WP008_F_ROOT_PASSWORD"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT @@server_uuid")) {
            rs.next();
            String uuid = rs.getString(1);
            if (M_DENY_UUID.equalsIgnoreCase(uuid) || !uuid.equalsIgnoreCase(System.getenv("WP008_F_EXPECT_UUID"))) {
                throw new IllegalStateException("F 目标守卫失败: " + uuid);
            }
        }
    }

    /** 真实数据范围解析器（Feign→f-auth），与 @ServiceDataScope 切面同一入口。 */
    @Autowired
    protected DataScopeResolver dataScopeResolver;

    /** 用于每例前重置 tenant 2002 事务型测试数据，隔离跨用例/跨类累加。 */
    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate resetJdbc;

    /**
     * 每个用例前清空 tenant 2002 事务型 CRM 数据（保留 crm_tenant_config/crm_pipeline/crm_pipeline_stage
     * 与全部 tenant=1 种子），使计数断言确定化。仅作用于 F 隔离库。
     */
    @BeforeEach
    void resetTenant2002TxData() {
        // 按子→父安全序删除，避免外键约束问题（跨连接池会话级开关不可靠，故不依赖）。
        for (String t : new String[]{"crm_opportunity_stage_history", "crm_activity", "crm_opportunity",
                "crm_contact", "crm_lead_conversion", "crm_lead", "crm_customer", "crm_owner_change_log"}) {
            resetJdbc.update("DELETE FROM omni_crm." + t + " WHERE tenant_id = 2002");
        }
        resetJdbc.update("DELETE FROM omni_crm.sys_mq_message WHERE tenant_id = 2002");
    }

    /**
     * 以真实业务身份执行：绑定 ServiceIdentityContext 并经真实 DataScopeResolver（Feign→f-auth）
     * 解析并绑定 ServiceDataScopeContext，镜像 @ServiceDataScope 切面语义，finally 同时清理两层。
     */
    protected <T> T as(Long userId, Long tenantId, String username, Supplier<T> call) {
        return asScoped(userId, tenantId, username, "crm:customer:list", call);
    }

    /** 指定权限码的真实范围绑定执行。 */
    protected <T> T asScoped(Long userId, Long tenantId, String username, String permissionCode, Supplier<T> call) {
        ServiceRequestIdentity identity = new ServiceRequestIdentity(userId, tenantId, username);
        ServiceIdentityContext.set(identity);
        ServiceDataScopeContext.ScopeInfo previous = ServiceDataScopeContext.get();
        try {
            ServiceDataScopeContext.set(dataScopeResolver.resolve(identity, permissionCode));
            return call.get();
        } finally {
            if (previous == null) {
                ServiceDataScopeContext.clear();
            } else {
                ServiceDataScopeContext.set(previous);
            }
            ServiceIdentityContext.clear();
        }
    }

    /** 以真实业务身份执行（无返回）。 */
    protected void asRun(Long userId, Long tenantId, String username, Runnable call) {
        asScoped(userId, tenantId, username, "crm:customer:list", () -> {
            call.run();
            return null;
        });
    }

    /**
     * 在真实身份+数据范围基础上，额外向 SecurityContextHolder 注入与生产网关/过滤器链等价的
     * GrantedAuthority 集合（用于验证后端 PII 解敏/脱敏分支）。finally 同时清理 SecurityContext。
     */
    protected <T> T asAuth(Long userId, Long tenantId, String username, List<String> authorities, Supplier<T> call) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, "N/A",
                authorities == null ? List.<SimpleGrantedAuthority>of()
                        : authorities.stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            return as(userId, tenantId, username, call);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 断言身份上下文在调用链后已清理（finally 语义）。 */
    protected void assertIdentityCleared() {
        try {
            ServiceIdentityContext.require();
            throw new AssertionError("ServiceIdentityContext 未在 finally 清理");
        } catch (IllegalStateException | com.omni.common.core.result.BusinessException expected) {
            // 正确的 fail-closed 空上下文
        }
    }
}
