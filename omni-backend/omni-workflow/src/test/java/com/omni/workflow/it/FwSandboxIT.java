package com.omni.workflow.it;

import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowModelService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 诊断沙盒（非 AC 用例）：定位 ALL 会签首个 approve 抛 FlowableOptimisticLocking 的确切命令。
 *
 * @author Omni-Stack Team
 */
@DisplayName("SANDBOX 乐观锁定位")
class FwSandboxIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private ProcessInstanceService instanceService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private com.omni.common.workflow.approval.ApprovalService approvalService;
    @Autowired
    private com.omni.workflow.service.WorkflowApprovalService workflowApprovalService;

    /** A：纯 taskService.complete 无变量。 */
    @Test
    @DisplayName("A pure complete")
    void pureComplete() {
        String pid = startInstance();
        Task t = firstTask(pid);
        Throwable err = null;
        try {
            withTenantRuntime(1L, () -> {
                taskService.complete(t.getId());
                return null;
            });
        } catch (Throwable e) {
            err = e;
        }
        System.out.println("SANDBOX-A pure-complete error=" + (err == null ? "NONE" : err));
        cleanupIfRunning(pid);
    }

    /** B：仅 setVariableLocal 后 complete（同事务两次命令）。 */
    @Test
    @DisplayName("B varlocal then complete")
    void varLocalThenComplete() {
        String pid = startInstance();
        Task t = firstTask(pid);
        Throwable err = null;
        try {
            withTenantRuntime(1L, () -> {
                taskService.createTaskQuery().taskId(t.getId()).singleResult();
                taskService.setVariableLocal(t.getId(), "approved", true);
                taskService.complete(t.getId());
                return null;
            });
        } catch (Throwable e) {
            err = e;
        }
        System.out.println("SANDBOX-B varlocal error=" + (err == null ? "NONE" : err));
        cleanupIfRunning(pid);
    }

    /** C：runtimeService.setVariable(procInstId)+child 计数器+complete（ApprovalServiceImpl 序列）。 */
    @Test
    @DisplayName("C approval service sequence")
    void approvalSequence() {
        String pid = startInstance();
        Task t = firstTask(pid);
        Throwable err = null;
        try {
            withTenantRuntime(1L, () -> {
                runtimeService.setVariable(pid, "approved", true);
                runtimeService.setVariable(t.getExecutionId(), "approvedCount",
                        ((Number) runtimeService.getVariable(t.getExecutionId(), "approvedCount")).intValue() + 1);
                taskService.complete(t.getId());
                return null;
            });
        } catch (Throwable e) {
            err = e;
        }
        System.out.println("SANDBOX-C sequence error=" + (err == null ? "NONE" : err));
        cleanupIfRunning(pid);
    }

    /** D：common ApprovalService 直接 complete（无租户包装）。 */
    @Test
    @DisplayName("D common approval service")
    void commonApprovalService() {
        String pid = startInstance();
        Task t = firstTask(pid);
        Throwable err = null;
        try {
            withTenantRuntime(1L, () -> {
                approvalService.complete(t.getId(), true, "d", null);
                return null;
            });
        } catch (Throwable e) {
            err = e;
        }
        System.out.println("SANDBOX-D common error=" + (err == null ? "NONE" : err));
        cleanupIfRunning(pid);
    }

    /** E：WorkflowApprovalService 完整入口（requireCurrentAssigneeTask+publishIfCross+syncAfterCommit）。 */
    @Test
    @DisplayName("E workflow approval facade")
    void workflowApprovalFacade() {
        String pid = startInstance();
        Task t = firstTask(pid);
        Throwable err = null;
        try {
            withTenantRuntime(1L, () -> {
                var ar = new com.omni.workflow.dto.ApprovalRequest();
                ar.setApproved(true);
                ar.setComment("e");
                workflowApprovalService.complete(t.getId(), ar, Long.valueOf(t.getAssignee()), 1L);
                return null;
            });
        } catch (Throwable e) {
            err = e;
        }
        System.out.println("SANDBOX-E facade error=" + (err == null ? "NONE" : err));
        cleanupIfRunning(pid);

        // E2：同一入口但 userId 与 assignee 不匹配
        String pid2 = startInstance();
        Task t2 = firstTask(pid2);
        Long wrong = "2211".equals(t2.getAssignee()) ? 2212L : 2211L;
        Throwable err2 = null;
        try {
            withTenantRuntime(1L, () -> {
                var ar = new com.omni.workflow.dto.ApprovalRequest();
                ar.setApproved(true);
                ar.setComment("e2");
                workflowApprovalService.complete(t2.getId(), ar, wrong, 1L);
                return null;
            });
        } catch (Throwable e) {
            err2 = e;
        }
        System.out.println("SANDBOX-E2 mismatch error=" + (err2 == null ? "NONE" : err2));
        cleanupIfRunning(pid2);
    }

    /** G：复刻 FW05-1 原文（硬编 2211，取 tasks.get(0)）。 */
    @Test
    @DisplayName("G replicate fw05-1")
    void replicateFw05() {
        String pid = startInstance();
        List<Task> tasks = withTenantRuntime(1L, () -> taskService.createTaskQuery().processInstanceId(pid).list());
        System.out.println("SANDBOX-G task0=" + tasks.get(0).getAssignee() + " task1=" + tasks.get(1).getAssignee());
        Throwable err = null;
        try {
            withTenantRuntime(1L, () -> {
                var ar = new com.omni.workflow.dto.ApprovalRequest();
                ar.setApproved(true);
                ar.setComment("ap1同意");
                workflowApprovalService.complete(tasks.get(0).getId(), ar, 2211L, 1L);
                return null;
            });
        } catch (Throwable e) {
            err = e;
        }
        System.out.println("SANDBOX-G replicate error=" + (err == null ? "NONE" : err));
        cleanupIfRunning(pid);
    }

    /** H：两次 facade approve 连续（复刻 FW05-1 全流程：approve1→查询→approve2）。 */
    @Test
    @DisplayName("H two approves")
    void twoApproves() {
        String pid = startInstance();
        try {
            withTenantRuntime(1L, () -> {
                List<Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
                Task t1 = tasks.stream().filter(t -> "2211".equals(t.getAssignee())).findFirst().orElseThrow();
                var ar = new com.omni.workflow.dto.ApprovalRequest();
                ar.setApproved(true);
                ar.setComment("h1");
                workflowApprovalService.complete(t1.getId(), ar, 2211L, 1L);
                return null;
            });
            withTenantRuntime(1L, () -> {
                List<Task> rest = taskService.createTaskQuery().processInstanceId(pid).list();
                Task t2 = rest.stream().filter(t -> "2212".equals(t.getAssignee())).findFirst().orElseThrow();
                var ar = new com.omni.workflow.dto.ApprovalRequest();
                ar.setApproved(true);
                ar.setComment("h2");
                workflowApprovalService.complete(t2.getId(), ar, 2212L, 1L);
                return null;
            });
            System.out.println("SANDBOX-H two-approves error=NONE");
        } catch (Throwable e) {
            System.out.println("SANDBOX-H two-approves error=" + e);
        } finally {
            cleanupIfRunning(pid);
        }
    }

    private Task firstTask(String pid) {
        List<Task> ts = withTenantRuntime(1L, () -> taskService.createTaskQuery().processInstanceId(pid).list());
        assertThat(ts).hasSize(2);
        return ts.get(0);
    }

    private String startInstance() {
        String key = uniqKey("wp008f-sbx");
        return withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-SBX-" + key);
            sp.setTitle("sbx");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
    }

    private void cleanupIfRunning(String pid) {
        try {
            withTenantRuntime(1L, () -> {
                if (runtimeService.createProcessInstanceQuery().processInstanceId(pid).count() > 0) {
                    instanceService.terminate(pid, "sbx", 2210L, 1L);
                }
                return null;
            });
        } catch (Exception ignored) {
            // ok
        }
    }
}
