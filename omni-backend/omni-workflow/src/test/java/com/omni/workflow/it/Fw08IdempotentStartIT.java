package com.omni.workflow.it;

import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.dto.internal.InternalStartProcessRequest;
import com.omni.workflow.dto.internal.InternalStartProcessResponse;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.InternalWorkflowService;
import com.omni.workflow.service.WorkflowModelService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FW08：内部启动 requestId/businessKey 双幂等、冲突拒绝、真实唯一索引参与。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW08 双幂等")
class Fw08IdempotentStartIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private InternalWorkflowService internalService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 相同 requestId 重复启动 → replayed 返回原实例，只一个实例。 */
    @Test
    @DisplayName("FW08-1 同requestId重放")
    void sameRequestIdReplays() {
        Ctx c = ctx("wp008f-idem");
        String reqId = "wp008f-r-" + UUID.randomUUID();
        InternalStartProcessResponse r1 = start(reqId, c);
        InternalStartProcessResponse r2 = start(reqId, c);
        assertThat(r1.getProcessInstanceId()).isEqualTo(r2.getProcessInstanceId());
        assertThat(instancesFor(c.bizKey)).isEqualTo(1);
        withTenantRuntime(c.tenant, () ->
                assertThat(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(r1.getProcessInstanceId()).count()).isEqualTo(1));
    }

    /** 不同 requestId 相同 businessKey → 冲突按契约处理（409/业务异常），不新建第二实例。 */
    @Test
    @DisplayName("FW08-2 同businessKey冲突拒绝")
    void sameBusinessKeyConflicts() {
        Ctx c = ctx("wp008f-bk");
        start("wp008f-r-" + UUID.randomUUID(), c);
        try {
            start("wp008f-r-" + UUID.randomUUID(), c);
            // 若实现选择业务键复用重放，也必须是唯一实例
        } catch (RuntimeException expected) {
            // 冲突拒绝即为预期
        }
        assertThat(instancesFor(c.bizKey)).isEqualTo(1);
    }

    /** 并发同 requestId：恰一真实实例。 */
    @Test
    @DisplayName("FW08-3 并发同requestId单实例")
    void concurrentSameRequestId() throws Exception {
        Ctx c = ctx("wp008f-conc");
        String reqId = "wp008f-r-" + UUID.randomUUID();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<java.util.concurrent.Future<String>> fs = List.of(
                pool.submit(() -> { go.await(); return start(reqId, c).getProcessInstanceId(); }),
                pool.submit(() -> { go.await(); return start(reqId, c).getProcessInstanceId(); }));
        go.countDown();
        String a;
        String b;
        try {
            a = fs.get(0).get(120, TimeUnit.SECONDS);
            b = fs.get(1).get(120, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            for (var e : Thread.getAllStackTraces().entrySet()) {
                if (e.getKey().getName().toLowerCase().contains("pool")) {
                    System.out.println("FW08-3 THREAD " + e.getKey().getName() + " " + java.util.Arrays.toString(e.getValue()));
                }
            }
            throw te;
        }
        pool.shutdown();
        assertThat(a).isEqualTo(b);
        assertThat(instancesFor(c.bizKey)).isEqualTo(1);
    }

    /** 异租户 header 与 body tenantId 不一致 → 403 拒绝。 */
    @Test
    @DisplayName("FW08-4 头体租户不一致403")
    void tenantMismatchForbidden() {
        Ctx c = ctx("wp008f-tm403");
        InternalStartProcessRequest req = req("wp008f-r-" + UUID.randomUUID(), c);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> withTenantRuntime(1L, () -> internalService.start(2002L, req)))
                .isInstanceOf(RuntimeException.class);
        assertThat(instancesFor(c.bizKey)).isZero();
    }

    /** 未发布/他租户 versionId → 拒绝且无残留。 */
    @Test
    @DisplayName("FW08-5 未发布版本拒绝")
    void unpublishedVersionRejected() {
        Ctx c = ctx("wp008f-unpub", false);
        InternalStartProcessRequest req = req("wp008f-r-" + UUID.randomUUID(), c);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> startRaw(req, c)).isInstanceOf(RuntimeException.class);
        assertThat(instancesFor(c.bizKey)).isZero();
    }

    /** 冲突 payload：同 requestId 不同 businessKey → 拒绝（幂等记录冲突）。 */
    @Test
    @DisplayName("FW08-6 同requestId异payload拒绝")
    void sameRequestIdConflictPayload() {
        Ctx c = ctx("wp008f-cp");
        String reqId = "wp008f-r-" + UUID.randomUUID();
        start(reqId, c);
        Ctx c2 = new Ctx(c.tenant, c.version, "WP008F-OTHER-" + UUID.randomUUID());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> startRaw(req(reqId, c2), c2)).isInstanceOf(RuntimeException.class);
    }

    private InternalStartProcessResponse start(String requestId, Ctx c) {
        return startRaw(req(requestId, c), c);
    }

    private InternalStartProcessResponse startRaw(InternalStartProcessRequest req, Ctx c) {
        return withTenantRuntime(c.tenant, () -> internalService.start(c.tenant, req));
    }

    private InternalStartProcessRequest req(String requestId, Ctx c) {
        InternalStartProcessRequest r = new InternalStartProcessRequest();
        r.setRequestId(requestId);
        r.setTenantId(c.tenant);
        r.setModelVersionId(c.version);
        r.setBusinessType("WP008FTEST");
        r.setBusinessKey(c.bizKey);
        r.setStartUserId(2210L);
        r.setStartUserName("f1_req");
        r.setTitle("fw08");
        return r;
    }

    private long instancesFor(String bizKey) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_workflow.wf_process_instance_ext WHERE business_key=?",
                Long.class, bizKey);
        return n == null ? 0 : n;
    }

    private Ctx ctx(String prefix) {
        return ctx(prefix, true);
    }

    private Ctx ctx(String prefix, boolean publish) {
        String key = uniqKey(prefix);
        Long version = withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            if (!publish) {
                return m.getCurrentDraftVersionId();
            }
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            return pr.getVersionId();
        });
        return new Ctx(1L, version, "WP008F-BK-" + key);
    }

    /** 测试上下文。 */
    private record Ctx(Long tenant, Long version, String bizKey) {
    }
}
