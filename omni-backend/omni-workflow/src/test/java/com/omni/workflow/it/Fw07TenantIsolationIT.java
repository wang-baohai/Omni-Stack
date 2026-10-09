package com.omni.workflow.it;

import com.omni.common.core.result.BusinessException;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FW07：两真实 tenant 的模型/定义/任务/进度读写隔离 + simulateUser 冒名防护。
 * <p>冒名用例先以“失败触发”形式确立原合同（普通启动不得借 simulateUserId 覆盖为他人），
 * 修复前该用例应红，修复后转绿。</p>
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW07 跨租户与冒名")
class Fw07TenantIsolationIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private ProcessInstanceService instanceService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private JdbcTemplate jdbc;

    /** tenant1 列表不可见 tenant2 模型，反之亦然。 */
    @Test
    @DisplayName("FW07-1 模型读写跨tenant隔离")
    void modelIsolation() {
        String key = uniqKey("wp008f-iso");
        Long t2Model = withTenantRuntime(2002L, () -> {
            WfProcessModel m = newDraftModel(modelService, 2002L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            return m.getId();
        });
        withTenantRuntime(1L, () -> {
            assertThat(modelService.listModels(1L, key, null, 1, 10).getTotal()).isZero();
            assertThatThrownBy(() -> modelService.getModel(t2Model)).isInstanceOf(RuntimeException.class);
            return null;
        });
        // tenant2 可见自己的
        withTenantRuntime(2002L, () -> {
            assertThat(modelService.listModels(2002L, key, null, 1, 10).getTotal()).isEqualTo(1);
            return null;
        });
    }

    /** 跨租户使用他人模型版本启动被拒（403）。 */
    @Test
    @DisplayName("FW07-2 异tenant模型版本启动403")
    void crossTenantStartForbidden() {
        String key = uniqKey("wp008f-x403");
        PublishResult pr = withTenantRuntime(2002L, () -> {
            WfProcessModel m = newDraftModel(modelService, 2002L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            return modelService.publishModel(m.getId(), "wp008f");
        });
        withTenantRuntime(1L, () -> {
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-X403-" + key);
            sp.setTitle("cross");
            assertThatThrownBy(() -> instanceService.start(sp, 2210L, "f1_req", 1L))
                    .isInstanceOf(BusinessException.class);
            return null;
        });
    }

    /** tenant2 实例的任务不出现在 tenant1 待办查询，进度/记录按参与人隔离。 */
    @Test
    @DisplayName("FW07-3 任务与历史跨tenant不串")
    void taskHistoryIsolation() {
        String key = uniqKey("wp008f-th");
        String pid = withTenantRuntime(2002L, () -> {
            WfProcessModel m = newDraftModel(modelService, 2002L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-TH-" + key);
            sp.setTitle("th");
            return instanceService.start(sp, 2201L, "f2_req", 2002L);
        });
        try {
            // ACT_* 带 tenant：t1 服务上下文查不到该实例的任务（Flowable 按 tenantId 查询）
            assertThat(taskService.createTaskQuery().processInstanceId(pid).taskTenantId("1").count()).isZero();
            assertThat(taskService.createTaskQuery().processInstanceId(pid).taskTenantId("2002").count()).isEqualTo(2);
            // wf_process_instance_ext 行归属 tenant 2002
            Integer extTenant = jdbc.queryForObject(
                    "SELECT tenant_id FROM omni_workflow.wf_process_instance_ext WHERE process_instance_id=?",
                    Integer.class, pid);
            assertThat(extTenant).isEqualTo(2002);
        } finally {
            withTenantRuntime(2002L, () -> {
                instanceService.terminate(pid, "清理", 2201L, 2002L);
                return null;
            });
        }
    }

    /** 冒名防护：普通启动携带他人 simulateUserId 必须被拒/忽略，start_user 仍为可信 actor。 */
    @Test
    @DisplayName("FW07-4 simulateUser冒名拒绝")
    void simulateImpersonationRejected() {
        String key = uniqKey("wp008f-sim");
        Long versionId = withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            return modelService.publishModel(m.getId(), "wp008f").getVersionId();
        });
        withTenantRuntime(1L, () -> {
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(versionId);
            sp.setBusinessKey("WP008F-SIM-" + key);
            sp.setTitle("impersonate");
            sp.setSimulateUserId(2211L);       // 冒充 ap1
            sp.setSimulateUserName("f1_ap1");
            String pid;
            try {
                pid = instanceService.start(sp, 2210L, "f1_req", 1L);
            } catch (BusinessException expected) {
                return null; // 修复后：直接拒绝
            }
            try {
                Long startUser = jdbc.queryForObject(
                        "SELECT start_user_id FROM omni_workflow.wf_process_instance_ext WHERE process_instance_id=?",
                        Long.class, pid);
                assertThat(startUser).as("普通启动不得被 simulateUserId 覆盖为 2211").isEqualTo(2210L);
            } finally {
                try {
                    instanceService.terminate(pid, "清理", 2210L, 1L);
                } catch (Exception ignored) {
                    // ok
                }
            }
            return null;
        });
    }

    /** 可信 actor 不被 variables 覆盖：request.variables 放 startUser/initiator 也不改变记录。 */
    @Test
    @DisplayName("FW07-5 variables不能篡改发起人")
    void variablesCannotOverrideActor() {
        String key = uniqKey("wp008f-var");
        Long versionId = withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            return modelService.publishModel(m.getId(), "wp008f").getVersionId();
        });
        withTenantRuntime(1L, () -> {
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(versionId);
            sp.setBusinessKey("WP008F-VAR-" + key);
            sp.setTitle("var-actor");
            java.util.Map<String, Object> vars = new java.util.HashMap<>();
            vars.put("startUserId", 999999);
            vars.put("initiator", "hacker");
            sp.setVariables(vars);
            String pid = instanceService.start(sp, 2210L, "f1_req", 1L);
            try {
                Long startUser = jdbc.queryForObject(
                        "SELECT start_user_id FROM omni_workflow.wf_process_instance_ext WHERE process_instance_id=?",
                        Long.class, pid);
                assertThat(startUser).isEqualTo(2210L);
                List<Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
                assertThat(tasks).extracting(Task::getAssignee).containsExactlyInAnyOrder("2211", "2212");
            } finally {
                try {
                    instanceService.terminate(pid, "清理", 2210L, 1L);
                } catch (Exception ignored) {
                    // ok
                }
            }
            return null;
        });
    }
}
