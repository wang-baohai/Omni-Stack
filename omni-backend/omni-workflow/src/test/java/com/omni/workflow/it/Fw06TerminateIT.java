package com.omni.workflow.it;

import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.ApprovalRequest;
import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowApprovalService;
import com.omni.workflow.service.WorkflowModelService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FW06：实际发起人 terminate、非发起人拒绝、重复/并发 complete 只产生一个结果。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW06 终止与重复完成")
class Fw06TerminateIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private ProcessInstanceService instanceService;
    @Autowired
    private WorkflowApprovalService approvalService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RuntimeService runtimeService;

    /** 本人 terminate：实例消失、待办清空；重复 terminate 报错。 */
    @Test
    @DisplayName("FW06-1 发起人撤回")
    void starterTerminate() {
        String pid = start("wp008f-tm");
        withTenantRuntime(1L, () -> {
            instanceService.terminate(pid, "撤回", 2210L, 1L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count()).isZero();
            assertThat(taskService.createTaskQuery().processInstanceId(pid).count()).isZero();
            assertThatThrownBy(() -> instanceService.terminate(pid, "再撤", 2210L, 1L))
                    .isInstanceOf(RuntimeException.class);
            return null;
        });
    }

    /** 非发起人 terminate 拒绝，且不影响实例。 */
    @Test
    @DisplayName("FW06-2 非发起人撤回被拒")
    void nonStarterTerminateRejected() {
        String pid = start("wp008f-tm2");
        try {
            assertThatThrownBy(() -> withTenantRuntime(1L, () -> {
                instanceService.terminate(pid, "他人撤回", 2211L, 1L);
                return null;
            })).isInstanceOf(BusinessException.class);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count()).isEqualTo(1);
        } finally {
            withTenantRuntime(1L, () -> {
                instanceService.terminate(pid, "清理", 2210L, 1L);
                return null;
            });
        }
    }

    /** 并发 complete 同一任务：只允许一个成功，另一个被拒（无第二完成事实）。 */
    @Test
    @DisplayName("FW06-3 并发complete单结果")
    void concurrentCompleteSingleResult() throws Exception {
        String pid = start("wp008f-cc");
        Task t = withTenantRuntime(1L, () -> taskService.createTaskQuery().processInstanceId(pid).taskAssignee("2211").singleResult());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    withTenantRuntime(1L, () -> {
                        ApprovalRequest ar = new ApprovalRequest();
                        ar.setApproved(true);
                        ar.setComment("并发");
                        approvalService.complete(t.getId(), ar, 2211L, 1L);
                        return null;
                    });
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                    // 输者异常即可
                }
            });
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
            for (var e : Thread.getAllStackTraces().entrySet()) {
                if (e.getKey().getName().toLowerCase().contains("pool") || e.getKey().getName().contains("main")) {
                    System.out.println("FW06-3 THREAD " + e.getKey().getName() + " " + java.util.Arrays.toString(e.getValue()));
                }
            }
            throw new AssertionError("并发 complete 线程未在 60s 内结束，已输出栈");
        }
        assertThat(ok.get()).as("并发重复完成只允许一个成功").isEqualTo(1);
        withTenantRuntime(1L, () -> {
            if (runtimeService.createProcessInstanceQuery().processInstanceId(pid).count() > 0) {
                instanceService.terminate(pid, "清理", 2210L, 1L);
            }
            return null;
        });
    }

    /** 串行重复 complete 第二次 409/业务异常。 */
    @Test
    @DisplayName("FW06-4 串行重复完成被拒")
    void duplicateCompleteRejected() {
        String pid = start("wp008f-dc");
        Task t = withTenantRuntime(1L, () -> taskService.createTaskQuery().processInstanceId(pid).taskAssignee("2211").singleResult());
        approve(t.getId(), 2211L);
        assertThatThrownBy(() -> approve(t.getId(), 2211L)).isInstanceOf(BusinessException.class);
        withTenantRuntime(1L, () -> {
            if (runtimeService.createProcessInstanceQuery().processInstanceId(pid).count() > 0) {
                instanceService.terminate(pid, "清理", 2210L, 1L);
            }
            return null;
        });
    }

    private void approve(String taskId, Long userId) {
        withTenantRuntime(1L, () -> {
            ApprovalRequest ar = new ApprovalRequest();
            ar.setApproved(true);
            ar.setComment("ok");
            approvalService.complete(taskId, ar, userId, 1L);
            return null;
        });
    }

    private String start(String prefix) {
        String key = uniqKey(prefix);
        return withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-" + key);
            sp.setTitle("fw06");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
    }
}
