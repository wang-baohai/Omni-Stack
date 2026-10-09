package com.omni.workflow.it;

import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowModelService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FW04：role+anchor+scope 真实候选解析、scopeMode 分发、fallback（ERROR/ASSIGN_ADMIN/ESCALATE_PARENT）与禁用/无人边界。
 * <p>scope 断言必须走真实 {@link com.omni.workflow.service.CandidateResolutionService} 产品解析链，
 * 不得直查 fixture SQL。ESCALATE_PARENT 按 D1 批准的最小上溯语义验收（上溯命中/根空失败关闭）。</p>
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW04 候选解析")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Fw04CandidateIT extends AbstractFwIT {

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
    @Autowired
    private com.omni.workflow.service.CandidateResolutionService candidateResolutionService;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 为 ASSIGN_ADMIN 正例补 F 侧 SUPER_ADMIN scope（仅 F，允许）。 */
    @BeforeAll
    void seedAdminScope() {
        jdbc.update("INSERT IGNORE INTO omni_auth.sys_user_role_scope (tenant_id,user_id,role_id,unit_id,scope_mode,status)"
                + " SELECT 1,u.id,r.id,1,'SAME_UNIT',1 FROM omni_auth.sys_user u, omni_auth.sys_role r"
                + " WHERE u.tenant_id=1 AND u.username='admin' AND r.tenant_id=1 AND r.role_code='SUPER_ADMIN'");
    }

    /** SAME_UNIT：unit 2103 精确命中 ap1/ap2 两人（nocand 因 scope 在 101 被排除）。 */
    @Test
    @DisplayName("FW04-1 SAME_UNIT两候选")
    void sameUnitTwoCandidates() {
        String pid = startInstance("wp008f-c1", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL"));
        try {
            Set<String> assignees = taskService.createTaskQuery().processInstanceId(pid).list()
                    .stream().map(Task::getAssignee).collect(Collectors.toSet());
            assertThat(assignees).containsExactlyInAnyOrder("2211", "2212");
        } finally {
            terminateQuiet(pid);
        }
    }

    /** UNIT_AND_BELOW：走产品解析链 doResolveCandidates，命中 100 及其下属 DEPT_LEADER（非直查 SQL）。 */
    @Test
    @DisplayName("FW04-2 UNIT_AND_BELOW产品解析链含子单位")
    void unitAndBelow() {
        Long startUser = firstUserInUnit(100L);
        org.junit.jupiter.api.Assumptions.assumeTrue(startUser != null, "F 种子需存在 primary_unit=100 用户");
        List<Long> viaProduct = candidateResolutionService.doResolveCandidates(
                "DEPT_LEADER", "START_USER_PRIMARY_UNIT", "UNIT_AND_BELOW",
                objectMapper.createObjectNode(), startUser, 1L);
        assertThat(viaProduct).as("产品 UNIT_AND_BELOW 解析应命中 100 及下属 DEPT_LEADER(105/106)")
                .contains(105L, 106L);
    }

    /** CHILDREN_ONLY：走产品解析链，仅命中 100 的直接子单位 TEAM_LEADER，不含 100 本身。 */
    @Test
    @DisplayName("FW04-3 CHILDREN_ONLY产品解析链仅子单位")
    void childrenOnly() {
        Long startUser = firstUserInUnit(100L);
        org.junit.jupiter.api.Assumptions.assumeTrue(startUser != null, "F 种子需存在 primary_unit=100 用户");
        List<Long> viaProduct = candidateResolutionService.doResolveCandidates(
                "TEAM_LEADER", "START_USER_PRIMARY_UNIT", "CHILDREN_ONLY",
                objectMapper.createObjectNode(), startUser, 1L);
        assertThat(viaProduct).as("产品 CHILDREN_ONLY 解析仅 100 子单位 TEAM_LEADER").containsExactlyInAnyOrder(101L, 102L, 103L, 104L);
    }

    /** ESCALATE_PARENT：本级无候选时沿父链上溯命中上级 APPROVER；根仍空则服务层返回空（由入口按 ERROR 失败关闭）。 */
    @Test
    @DisplayName("FW04-8 ESCALATE_PARENT 上溯命中+根空失败关闭")
    void escalateParentFallback() {
        // 造一个 2103 的子单位 2199 与其内无 APPROVER 的发起用户 2299；上级 2103 有 APPROVER 2211/2212。
        Long parentUnit = 2103L;
        Long childUnit = 2199L;
        Long leafUser = 2299L;
        try {
            jdbc.update("INSERT INTO omni_auth.sys_org_unit (id,tenant_id,parent_id,name,type,unit_code,path,depth,sort,status) "
                    + "VALUES (?,1,?,'WP008F Escalate Child','DEPT','WP008F-ESC','/1/100/2103/2199',4,0,1)", childUnit, parentUnit);
            jdbc.update("INSERT INTO omni_auth.sys_user (id,tenant_id,username,password,nickname,primary_unit_id,status) "
                    + "VALUES (?,1,'wp008f_esc_leaf','x','Escalate Leaf',?,1)", leafUser, childUnit);
            // 正例：本级(2199)无 APPROVER，上溯到 2103 命中 2211/2212。
            List<Long> escalated = candidateResolutionService.escalateToParentForCandidates(
                    "WP008_APPROVER", "START_USER_PRIMARY_UNIT", "SAME_UNIT",
                    objectMapper.createObjectNode(), leafUser, 1L);
            assertThat(escalated).as("ESCALATE_PARENT 应上溯至 2103 命中 APPROVER").contains(2211L, 2212L);
            // 负例：根链全无 APPROVER 的旁支→上溯返回空（入口据此按 ERROR 抛错，不 fail-open）。
            Long otherUnit = 105L;
            Long otherUser = firstUserInUnit(otherUnit);
            org.junit.jupiter.api.Assumptions.assumeTrue(otherUser != null, "F 种子需存在 primary_unit=105 用户");
            List<Long> noneUp = candidateResolutionService.escalateToParentForCandidates(
                    "ROLE_NOT_EXIST_XYZ", "START_USER_PRIMARY_UNIT", "SAME_UNIT",
                    objectMapper.createObjectNode(), otherUser, 1L);
            assertThat(noneUp).as("不存在的角色上溯至根仍空→空(由入口失败关闭)").isEmpty();
        } finally {
            jdbc.update("DELETE FROM omni_auth.sys_user WHERE id=?", leafUser);
            jdbc.update("DELETE FROM omni_auth.sys_org_unit WHERE id=?", childUnit);
        }
    }

    /** 取指定主单位下一个启用用户 ID（F 种子）。 */
    private Long firstUserInUnit(Long unitId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM omni_auth.sys_user WHERE primary_unit_id=? AND tenant_id=1 AND status=1 ORDER BY id LIMIT 1",
                Long.class, unitId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** fallback=ERROR 且无人：启动失败且无活动实例残留（非静默通过）。 */
    @Test
    @DisplayName("FW04-4 ERROR无人候选失败无残留")
    void errorFallbackNoFailOpen() {
        long instBefore = runtimeService.createProcessInstanceQuery().count();
        String key = uniqKey("wp008f-c4");
        withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("ROLE_NOT_EXIST_XYZ", "SAME_UNIT", "ERROR", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-E-" + key);
            sp.setTitle("empty-candidates");
            assertThatThrownBy(() -> instanceService.start(sp, 2210L, "f1_req", 1L))
                    .isInstanceOf(RuntimeException.class);
            return null;
        });
        // 失败事务回滚：无新增活动实例、无 wf_process_instance_ext 行
        assertThat(runtimeService.createProcessInstanceQuery().count()).isEqualTo(instBefore);
    }

    /** fallback=ASSIGN_ADMIN：admin(unit1) 命中为唯一候选。发起人用 wangwu(103@102)，该单位无 WP008_APPROVER scope。 */
    @Test
    @DisplayName("FW04-5 ASSIGN_ADMIN回落到管理员")
    void assignAdminFallback() {
        String key = uniqKey("wp008f-c5");
        withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ASSIGN_ADMIN", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-AD-" + key);
            sp.setTitle("fallback-admin");
            String pid = instanceService.start(sp, 103L, "wangwu", 1L);
            try {
                Set<String> assignees = taskService.createTaskQuery().processInstanceId(pid).list()
                        .stream().map(Task::getAssignee).collect(Collectors.toSet());
                assertThat(assignees).containsExactly("1");
            } finally {
                terminateQuiet(pid);
            }
            return null;
        });
    }

    /** 禁用用户不得成为候选：临时禁用 f1_ap2 后只剩 1 人，用毕恢复。 */
    @Test
    @DisplayName("FW04-6 禁用用户排除")
    void disabledUserExcluded() {
        jdbc.update("UPDATE omni_auth.sys_user SET status=0 WHERE id=2212");
        String pid = null;
        try {
            pid = startInstance("wp008f-c6", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL"));
            Set<String> assignees = taskService.createTaskQuery().processInstanceId(pid).list()
                    .stream().map(Task::getAssignee).collect(Collectors.toSet());
            assertThat(assignees).containsExactly("2211");
        } finally {
            jdbc.update("UPDATE omni_auth.sys_user SET status=1 WHERE id=2212");
            if (pid != null) {
                terminateQuiet(pid);
            }
        }
    }

    /** 异租户锚点数据不串：tenant 2002 模型候选为 f2 用户。 */
    @Test
    @DisplayName("FW04-7 跨tenant候选隔离")
    void crossTenantCandidates() {
        String key = uniqKey("wp008f-c7");
        withTenantRuntime(2002L, () -> {
            WfProcessModel m = newDraftModel(modelService, 2002L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-T2-" + key);
            sp.setTitle("t2 candidates");
            String pid = instanceService.start(sp, 2201L, "f2_req", 2002L);
            try {
                Set<String> assignees = taskService.createTaskQuery().processInstanceId(pid).list()
                        .stream().map(Task::getAssignee).collect(Collectors.toSet());
                assertThat(assignees).containsExactlyInAnyOrder("2202", "2203");
            } finally {
                terminateQuiet(pid, 2201L, 2002L);
            }
            return null;
        });
    }

    private String startInstance(String keyPrefix, String assignment) {
        String key = uniqKey(keyPrefix);
        return withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assignment), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-" + key);
            sp.setTitle("it");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
    }

    private void terminateQuiet(String pid) {
        terminateQuiet(pid, 2210L, 1L);
    }

    private void terminateQuiet(String pid, Long userId, Long tenantId) {
        try {
            withTenantRuntime(tenantId, () -> {
                instanceService.terminate(pid, "it-cleanup", userId, tenantId);
                return null;
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
