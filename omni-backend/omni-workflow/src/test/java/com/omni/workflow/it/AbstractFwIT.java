package com.omni.workflow.it;

import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.WorkflowApplication;
import com.omni.workflow.dto.CreateModelRequest;
import com.omni.workflow.dto.SaveDraftRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * WP-008 FW 真实测试基类：绑定 F 隔离 MySQL/Redis 上的正式 WorkflowApplication 上下文。
 * <p>
 * 仅当 WP008_F_ENABLED=1 且连接 UUID 命中白名单（且≠M 生产 UUID）时运行；
 * 任一守卫失败立即抛异常，绝不回落为“跳过即通过”。静态注解/Mockito 结论不得升级为本类结果。</p>
 *
 * @author Omni-Stack Team
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "WP008_F_ENABLED", matches = "1")
@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.stream.function.autodetect=false",
                "spring.cloud.function.definition=",
                "xxl.job.executor.enabled=false",
                "omni.mqlog.relay.enabled=false",
                "omni.mqlog.relay.auto-register=false",
                "omni.internal.api.token=wp008f-it-internal-token",
                "flowable.database-schema-update=false",
                "flowable.async-executor-activate=false",
                "spring.scheduling.enabled=false"
        })
public abstract class AbstractFwIT {

    /** M 生产 MySQL UUID，出现即视为误目标，立即失败。 */
    protected static final String M_DENY_UUID = "814a6039-ac2d-11f1-aa42-a6e45e360df1";

    @Autowired
    protected DataSource dataSource;
    @Autowired
    protected Environment env;
    /** 随机应用端口（FW09 真实 HTTP 链用）。 */
    @org.springframework.beans.factory.annotation.Value("${local.server.port:0}")
    protected int serverPort;

    /** 环境变量值：F 期望 UUID。 */
    protected static final String EXPECT_UUID = System.getenv("WP008_F_EXPECT_UUID");

    /** 测试上下文里停用 Flowable 异步执行器（生产 configurer 强制 activate，属性覆盖无效）。 */
    @org.springframework.beans.factory.annotation.Autowired
    protected org.flowable.engine.ProcessEngine processEngine;

    /** 每个用例前确保异步执行器停用，排除后台线程对执行行的并发干扰。 */
    @org.junit.jupiter.api.BeforeEach
    void deactivateAsyncExecutor() {
        try {
            Object executor = processEngine.getClass().getMethod("getProcessEngineConfiguration")
                    .invoke(processEngine);
            Object asyncExecutor = executor.getClass().getMethod("getAsyncExecutor").invoke(executor);
            if (asyncExecutor != null) {
                for (String m : new String[]{"shutdown", "deactivate", "setActivated"}) {
                    try {
                        if ("setActivated".equals(m)) {
                            asyncExecutor.getClass().getMethod(m, boolean.class).invoke(asyncExecutor, false);
                        } else {
                            asyncExecutor.getClass().getMethod(m).invoke(asyncExecutor);
                        }
                        System.out.println("WP008F async-executor disabled via " + m);
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // try next
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("WP008F async-executor disable skipped: " + e);
        }
    }

    /** 动态属性：F 数据源/Redis/Stream 全部指向隔离端点。 */
    @DynamicPropertySource
    static void fProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("WP008_F_MYSQL_URL"));
        registry.add("spring.datasource.username", () -> "omni_app");
        registry.add("spring.datasource.password", () -> System.getenv("WP008_F_OMNI_DB_PASSWORD"));
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "26379");
        registry.add("spring.data.redis.password", () -> System.getenv("WP008_F_REDIS_PASSWORD"));
        registry.add("spring.cloud.stream.rocketmq.binder.name-server", () -> "127.0.0.1:29876");
    }

    /** 启动守卫：环境开关 + 连接目标 UUID 白名单/黑名单。 */
    @BeforeAll
    static void guardTarget() throws Exception {
        if (!"1".equals(System.getenv("WP008_F_ENABLED"))) {
            throw new IllegalStateException("WP008_F_ENABLED!=1：FW 真实测试不允许在未绑定 F 环境时运行");
        }
        try (Connection c = java.sql.DriverManager.getConnection(
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

    /** 每个用例后清理租户 ThreadLocal，防串线程。 */
    @AfterEach
    void clearTenant() {
        TenantInfoHolder.clear();
    }

    /** 在指定租户上下文内执行服务调用（保存/恢复 previous，嵌套安全）。 */
    protected <T> T withTenant(Long tenantId, Callable<T> call) throws Exception {
        String prev = TenantInfoHolder.getTenantId();
        TenantInfoHolder.setTenantId(String.valueOf(tenantId));
        try {
            return call.call();
        } finally {
            restore(prev);
        }
    }

    /** 生成唯一模型 key（满足 ^[a-zA-Z][a-zA-Z0-9-]*$）。 */
    protected static String uniqKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).replace("-", "x");
    }

    /** 构造带 omni:assignment 与 MI 的单审批节点 BPMN。 */
    protected static String bpmn(String processKey, String taskName, String assignmentJson) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:omni="http://omni.com/workflow"
                  id="Definitions_%s" targetNamespace="wp008f">
                  <process id="%s" name="WP008F %s" isExecutable="true">
                    <startEvent id="start" name="start" flowable:initiator="initiator"/>
                    <userTask id="approve" name="%s" flowable:assignee="${userId}">
                      <extensionElements>
                        <omni:assignment>%s</omni:assignment>
                      </extensionElements>
                      <multiInstanceLoopCharacteristics isSequential="false"
                        flowable:collection="${candidateResolver.resolve(execution)}"
                        flowable:elementVariable="userId">
                        <completionCondition xsi:type="tFormalExpression">${rejectedCount &gt; 0 || approvedCount &gt;= requiredApprovals}</completionCondition>
                      </multiInstanceLoopCharacteristics>
                    </userTask>
                    <endEvent id="end" name="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(processKey, processKey, processKey, taskName, assignmentJson);
        }

    /** assignment JSON 快捷构造。 */
    protected static String assign(String roleCode, String scopeMode, String fallback, String approvalMode) {
        return ("{\"roleCode\":\"" + roleCode + "\",\"anchorType\":\"START_USER_PRIMARY_UNIT\","
                + "\"anchorParams\":{},\"scopeMode\":\"" + scopeMode + "\",\"fallbackStrategy\":\""
                + fallback + "\",\"approvalMode\":\"" + approvalMode + "\"}");
    }

    /** 创建模型 + 保存草稿（走正式 WorkflowModelService）。 */
    protected com.omni.workflow.entity.WfProcessModel newDraftModel(
            com.omni.workflow.service.WorkflowModelService modelService,
            Long tenantId, String userName, String key, String category, String xml, String designerJson) {
        com.omni.workflow.entity.WfProcessModel model = withTenantRuntime(tenantId, () -> {
            CreateModelRequest cr = new CreateModelRequest();
            cr.setModelKey(key);
            cr.setModelName("WP008F " + key);
            cr.setCategory(category);
            cr.setDesignerJson(designerJson == null ? "{\"nodes\":[]}" : designerJson);
            return modelService.createModel(cr, tenantId, userName);
        });
        final Long modelId = model.getId();
        withTenantRuntime(tenantId, () -> {
            SaveDraftRequest sd = new SaveDraftRequest();
            sd.setBpmnXml(xml);
            sd.setDesignerJson(designerJson == null ? "{\"nodes\":[]}" : designerJson);
            return modelService.saveDraft(modelId, sd, userName);
        });
        return withTenantRuntime(tenantId, () -> modelService.getModel(modelId));
    }

    /** 与 withTenant 等价的 Supplier 版（测试内便捷）。 */
    protected <T> T withTenantRuntime(Long tenantId, Supplier<T> call) {
        String prev = TenantInfoHolder.getTenantId();
        TenantInfoHolder.setTenantId(String.valueOf(tenantId));
        try {
            return call.get();
        } finally {
            restore(prev);
        }
    }

    /** 恢复之前的租户上下文。 */
    private static void restore(String prev) {
        if (prev == null) {
            TenantInfoHolder.clear();
        } else {
            TenantInfoHolder.setTenantId(prev);
        }
    }

    /** 极简 HTTP（FW09 代理路径用）。 */
    protected static HttpResponse<String> httpPost(String url, String body, String token) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
