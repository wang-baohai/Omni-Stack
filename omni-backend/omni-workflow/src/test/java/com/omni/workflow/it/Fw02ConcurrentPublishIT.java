package com.omni.workflow.it;

import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.WorkflowModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FW02：两个独立事务同时发布同一草稿，真实行锁下只有一个成功，无孤儿部署/重复当前版本。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW02 并发发布行锁")
class Fw02ConcurrentPublishIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 双线程发布同一模型草稿：恰一成功、一个部署、一个 PUBLISHED 当前版本。 */
    @Test
    @DisplayName("FW02-1 并发发布仅一个成功")
    void concurrentPublishSingleWinner() throws Exception {
        String key = uniqKey("wp008f-cp");
        Long modelId = withTenantRuntime(1L, () -> {
            WfProcessModel m = modelService.createModel(createReq(key), 1L, "wp008f");
            com.omni.workflow.dto.SaveDraftRequest sd = new com.omni.workflow.dto.SaveDraftRequest();
            sd.setBpmnXml(bpmn(key, "WP008F审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")));
            sd.setDesignerJson("{\"nodes\":[]}");
            modelService.saveDraft(m.getId(), sd, "wp008f");
            return m.getId();
        });
        long depBefore = deployments();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<FutureOutcome> task = () -> {
            try {
                PublishResult pr = withTenantRuntime(1L, () -> modelService.publishModel(modelId, "wp008f"));
                return new FutureOutcome(true, pr.getBusinessVersion(), null);
            } catch (Exception e) {
                return new FutureOutcome(false, null, e.getClass().getSimpleName() + ":" + e.getMessage());
            }
        };
        // 两线程同时起跑，等待栅栏后一起提交，制造真实 SELECT FOR UPDATE 竞争
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        Future<FutureOutcome> f1 = pool.submit(() -> { barrier.await(); return task.call(); });
        Future<FutureOutcome> f2 = pool.submit(() -> { barrier.await(); return task.call(); });
        FutureOutcome o1 = f1.get(120, java.util.concurrent.TimeUnit.SECONDS);
        FutureOutcome o2 = f2.get(120, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        int success = (o1.ok ? 1 : 0) + (o2.ok ? 1 : 0);
        assertThat(success).as("并发发布只允许一个成功 o1=%s o2=%s", o1, o2).isEqualTo(1);
        assertThat(deployments() - depBefore).isEqualTo(1);
        List<Integer> published = jdbc.queryForList(
                "SELECT version FROM omni_workflow.wf_process_model_version WHERE model_id=? AND status='PUBLISHED'",
                Integer.class, modelId);
        assertThat(published).containsExactly(1);
        Long currentPub = jdbc.queryForObject(
                "SELECT current_published_version_id FROM omni_workflow.wf_process_model WHERE id=?",
                Long.class, modelId);
        assertThat(currentPub).isNotNull();
    }

    /** 串行第二次发布（草稿指针已非 DRAFT）按契约拒绝且无新部署。 */
    @Test
    @DisplayName("FW02-2 指针非DRAFT再发布拒绝")
    void republishWithoutDraftRejected() {
        String key = uniqKey("wp008f-rp");
        withTenantRuntime(1L, () -> {
            WfProcessModel m = modelService.createModel(createReq(key), 1L, "wp008f");
            com.omni.workflow.dto.SaveDraftRequest sd = new com.omni.workflow.dto.SaveDraftRequest();
            sd.setBpmnXml(bpmn(key, "WP008F审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")));
            sd.setDesignerJson("{\"nodes\":[]}");
            modelService.saveDraft(m.getId(), sd, "wp008f");
            long depBefore = deployments();
            modelService.publishModel(m.getId(), "wp008f");
            assertThat(deployments()).isEqualTo(depBefore + 1);
            try {
                modelService.publishModel(m.getId(), "wp008f");
                throw new AssertionError("二次发布应被拒绝");
            } catch (RuntimeException expected) {
                assertThat(expected.getMessage()).containsIgnoringCase("DRAFT");
            }
            assertThat(deployments()).isEqualTo(depBefore + 1);
            return null;
        });
    }

    private long deployments() {
        AtomicLong n = new AtomicLong();
        jdbc.query("SELECT COUNT(*) c FROM omni_workflow.ACT_RE_DEPLOYMENT", rs -> { n.set(rs.getLong("c")); });
        return n.get();
    }

    private com.omni.workflow.dto.CreateModelRequest createReq(String key) {
        com.omni.workflow.dto.CreateModelRequest cr = new com.omni.workflow.dto.CreateModelRequest();
        cr.setModelKey(key);
        cr.setModelName("WP008F " + key);
        cr.setCategory("wp008f_it");
        cr.setDesignerJson("{\"nodes\":[]}");
        return cr;
    }

    /** 线程结果。 */
    private record FutureOutcome(boolean ok, Integer businessVersion, String error) {
    }
}
