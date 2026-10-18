package com.omni.workflow.it;

import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.ApprovalRequest;
import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.ProcessInstanceService;
import com.omni.workflow.service.WorkflowApprovalService;
import com.omni.workflow.service.WorkflowModelService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FW05：真实 Flowable 多实例引擎行为——ALL 全员通过、ANY 首过余 MI_END、任一驳回；历史以 deleteReason 判定。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW05 ALL/ANY/MI_END/驳回")
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Fw05MultiInstanceIT extends AbstractFwIT {

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
    @Autowired
    private HistoryService historyService;

    /** ALL：第一人通过仍 pending，第二人完成实例；两任务均正常完成。 */
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("FW05-1 ALL双人顺序通过")
    void allCountersign() {
        String pid = start("wp008f-mi-all", "ALL");
        try {
            List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
            assertThat(tasks).hasSize(2);
            approve(taskOf(tasks, "2211"), true, "ap1同意", 2211L);
            assertThat(taskService.createTaskQuery().processInstanceId(pid).count()).as("第一通过后仍有待办")
                    .isEqualTo(1);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).singleResult()).isNotNull();
            List<org.flowable.task.api.Task> rest = taskService.createTaskQuery().processInstanceId(pid).list();
            assertThat(rest).hasSize(1);
            approve(rest.get(0).getId(), true, "ap2同意", 2212L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count()).isZero();
            Map<String, String> reasons = historyReasons(pid);
            assertThat(reasons.values()).allMatch(r -> r == null); // 无跳过：两人真实通过
        } finally {
            cleanup(pid);
        }
    }

    /** ANY：首人通过后其余任务 deleteReason=MI_END；审批记录区分自动通过与真人批准。 */
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("FW05-2 ANY首过余MI_END")
    void anyCountersign() {
        String pid = start("wp008f-mi-any", "ANY");
        try {
            List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
            assertThat(tasks).hasSize(2);
            String winner = tasks.stream().filter(t -> "2211".equals(t.getAssignee())).findFirst().orElseThrow().getId();
            approve(winner, true, "首过", 2211L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count()).isZero();
            Map<String, String> reasons = historyReasons(pid);
            assertThat(reasons.values()).contains("MI_END");
            var records = instanceService.getApprovalRecords(pid, 1L);
            assertThat(records).anySatisfy(r -> assertThat(String.valueOf(r)).contains("auto-approved"));
            assertThat(records).anySatisfy(r -> assertThat(String.valueOf(r)).contains("result=approved"));
        } finally {
            cleanup(pid);
        }
    }

    /** 任一驳回：rejectedCount>0 触发完成条件，实例结束；驳回者无 MI 跳过原因。 */
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("FW05-3 任一驳回整体结束")
    void rejectionEnds() {
        String pid = start("wp008f-mi-rej", "ALL");
        try {
            List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
            approve(taskOf(tasks, "2211"), false, "驳回：不符合", 2211L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count())
                    .as("驳回后完成条件触发，实例结束").isZero();
            var records = instanceService.getApprovalRecords(pid, 1L);
            assertThat(records).isNotEmpty();
        } finally {
            cleanup(pid);
        }
    }

    /** 非 assignee 完成被拒（引擎层任务归属校验）。 */
    @Test
    @DisplayName("FW05-4 非指派完成被拒")
    void foreignApproveRejected() {
        String pid = start("wp008f-mi-for", "ALL");
        try {
            List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
            String ap2Task = tasks.stream().filter(t -> "2212".equals(t.getAssignee())).findFirst().orElseThrow().getId();
            assertThatThrownBy(() -> approve(ap2Task, true, "冒审", 2211L))
                    .isInstanceOf(BusinessException.class);
        } finally {
            cleanup(pid);
        }
    }

    /**
     * FW05-5 加签语义回归（WP-015 FR04 修正错误 oracle）。
     * <p>ALL 双人会签 → 第一人通过后加签第三人 → 已有票数不得被重置，
     * 且有效全员阈值必须变为 3：第二票后实例仍运行、第三人仍有真实待办，
     * 第三票后才结束，三人均为真实同意（无 MI_END 跳过加签人）。</p>
     */
    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("FW05-5 ALL 加签第三人必须计入阈值")
    void addSignerRaisesAllThreshold() {
        String pid = start("wp008f-mi-ads", "ALL");
        try {
            // 初始 2 个 MI 任务
            List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().processInstanceId(pid).list();
            assertThat(tasks).hasSize(2);

            // 第一人通过 → approvedCount=1
            approve(taskOf(tasks, "2211"), true, "ap1同意", 2211L);
            assertThat(miVar(pid, "approvedCount")).as("第一通过后 approvedCount=1").isEqualTo(1);
            assertThat(miVar(pid, "requiredApprovals")).as("初始 ALL 阈值=2").isEqualTo(2);
            assertThat(runtimeService.getVariables(pid).get("approvedCount"))
                    .as("计数器不得沉到流程实例作用域").isNull();

            // 加签第三人（2213）
            String remainingTask = taskService.createTaskQuery().processInstanceId(pid).list().get(0).getId();
            withTenantRuntime(1L, () -> {
                approvalService.addSigner(remainingTask, "2213", 2212L, 1L);
                return null;
            });

            // 关键断言：加签后 approvedCount 仍为 1（未被重置为 0）
            assertThat(miVar(pid, "approvedCount")).as("addSigner 后 approvedCount 不得被重置").isEqualTo(1);
            // 正确 ALL 语义：加签后有效全员阈值变 3（已同意 1 + 仍在待办 2）
            assertThat(miVar(pid, "requiredApprovals")).as("加签第三人后 ALL 阈值必须变 3").isEqualTo(3);

            // 现在应有 2 个待办任务（2212 原始 + 2213 加签）
            List<org.flowable.task.api.Task> afterAdd = taskService.createTaskQuery().processInstanceId(pid).list();
            assertThat(afterAdd).as("加签后应有 2 个待办").hasSize(2);

            // 第二人通过 → approvedCount=2 < 阈值 3，实例仍运行且第三人待办仍在
            approve(taskOf(afterAdd, "2212"), true, "ap2同意", 2212L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count())
                    .as("第二票后实例必须仍运行（2 < 3）").isEqualTo(1);
            assertThat(taskService.createTaskQuery().processInstanceId(pid).taskAssignee("2213").count())
                    .as("加签人 2213 必须仍有真实待办").isEqualTo(1);
            assertThat(miVar(pid, "approvedCount")).as("第二票后 approvedCount=2").isEqualTo(2);

            // 第三人通过 → 3 >= 3，实例结束
            String thirdTask = taskService.createTaskQuery().processInstanceId(pid)
                    .taskAssignee("2213").singleResult().getId();
            approve(thirdTask, true, "ap3同意", 2213L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count())
                    .as("第三票后实例结束").isZero();

            // 三人均为真实同意，无一人被 MI_END 跳过
            Map<String, String> finalReasons = historyReasons(pid);
            assertThat(finalReasons.keySet())
                    .as("三人各一条历史任务")
                    .containsExactlyInAnyOrder("2211/审批", "2212/审批", "2213/审批");
            assertThat(finalReasons.values())
                    .as("三票全部真实通过，deleteReason 均为 null")
                    .filteredOn(r -> r == null).hasSize(3);
            assertThat(finalReasons.values())
                    .as("不得出现 MI_END 跳过加签人")
                    .filteredOn("MI_END"::equals).isEmpty();
        } finally {
            cleanup(pid);
        }
    }

    /**
     * FW05-6 顺序两个 MI 节点：第二个节点必须重新初始化计数器，不继承第一个节点。
     * <p>旧实现把 {@code _counters_init} 写到流程实例作用域，导致第二节点跳过初始化，
     * 阀值/票数跳节点泄漏。本用例直接报旧行为。</p>
     */
    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("FW05-6 顺序两个 MI 节点不串票数与阈值")
    void sequentialMiNodesDoNotLeakCounters() {
        String key = uniqKey("wp008f-mi-seq");
        String pid = startFromXml(key, twoNodeBpmn(key));
        try {
            assertThat(taskService.createTaskQuery().processInstanceId(pid).taskDefinitionKey("approve").count())
                    .as("节点1 应有 2 个待办").isEqualTo(2);
            assertThat(miVar(pid, "requiredApprovals")).as("节点1 阈值=2").isEqualTo(2);
            assertThat(miVar(pid, "approvedCount")).as("节点1 初始票数=0").isEqualTo(0);

            // 节点1 两人全部通过
            for (org.flowable.task.api.Task t : taskService.createTaskQuery()
                    .processInstanceId(pid).taskDefinitionKey("approve").list()) {
                approve(t.getId(), true, "n1同意", Long.valueOf(t.getAssignee()));
            }

            // 进入节点2：必须是全新的计数器
            assertThat(taskService.createTaskQuery().processInstanceId(pid).taskDefinitionKey("approve2").count())
                    .as("节点2 应有 2 个待办").isEqualTo(2);
            assertThat(miVar(pid, "approvedCount")).as("节点2 票数必须从 0 重新开始，不继承节点1 的 2").isEqualTo(0);
            assertThat(miVar(pid, "requiredApprovals")).as("节点2 阈值重新初始为 2").isEqualTo(2);
            assertThat(miVar(pid, "rejectedCount")).as("节点2 驳回数从 0 开始").isEqualTo(0);

            // 节点2 一人驳回：驳汇快捷结束，实例推进到驳回终点
            String n2 = taskService.createTaskQuery().processInstanceId(pid)
                    .taskDefinitionKey("approve2").list().get(0).getId();
            approve(n2, false, "n2驳回", 2211L);
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count())
                    .as("任一驳回即结束会签").isZero();
            assertThat(historyReasons(pid).values())
                    .as("节点2 未被驳回的审批人被 MI_END 跳过")
                    .filteredOn("MI_END"::equals).isNotEmpty();
        } finally {
            cleanup(pid);
        }
    }

    /**
     * FW05-7 相同 activityId 再次进入（循环回到同一个 MI 节点）。
     * <p>activityId 相同但 MI 根执行不同：第二次进入必须重新初始化，
     * 不能仅用 activityId 做幂等键，也不能因上一轮的标志而跳过。</p>
     */
    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("FW05-7 相同 activityId 重入必须重新初始化")
    void sameActivityReentryReinitializesCounters() {
        String key = uniqKey("wp008f-mi-loop");
        String pid = startFromXml(key, loopBackBpmn(key));
        try {
            // 第一遍：两人全部通过 → 进入标记节点 → 回到同一 activityId
            for (org.flowable.task.api.Task t : taskService.createTaskQuery()
                    .processInstanceId(pid).taskDefinitionKey("approve").list()) {
                approve(t.getId(), true, "round1", Long.valueOf(t.getAssignee()));
            }
            assertThat(miVar(pid, "approvedCount")).as("第二遍重新进入后票数归 0").isEqualTo(0);
            assertThat(miVar(pid, "requiredApprovals")).as("第二遍阈值重新初始为 2").isEqualTo(2);
            assertThat(taskService.createTaskQuery().processInstanceId(pid).taskDefinitionKey("approve").count())
                    .as("第二遍仍有 2 个待办").isEqualTo(2);

            // 第二遍：只需一人通过即可（ANY 不改变，本例仍为 ALL）→ 两人都通过后结束
            for (org.flowable.task.api.Task t : taskService.createTaskQuery()
                    .processInstanceId(pid).taskDefinitionKey("approve").list()) {
                approve(t.getId(), true, "round2", Long.valueOf(t.getAssignee()));
            }
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pid).count())
                    .as("第二遍完成后实例结束").isZero();
            assertThat(historyService.createHistoricTaskInstanceQuery().processInstanceId(pid).count())
                    .as("两遍共 4 个历史任务").isEqualTo(4);
        } finally {
            cleanup(pid);
        }
    }

    /**
     * 读取多实例根执行上的本地变量（沿该实例内所有执行查找）。
     * <p>用于验证计数器确实住在 MI 根作用域而不是流程实例作用域。</p>
     */
    private Object miVar(String processInstanceId, String name) {
        for (org.flowable.engine.runtime.Execution e : runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId).list()) {
            Object v = runtimeService.getVariableLocal(e.getId(), name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 以自定义 BPMN 启动一个实例（用于多节点/循环拓扑）。 */
    private String startFromXml(String key, String xml) {
        return withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it", xml, null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-" + key);
            sp.setTitle("mi-topology");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
    }

    /** 顺序两个 ALL 会签节点的 BPMN。 */
    private static String twoNodeBpmn(String processKey) {
        String json = assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:omni="http://omni.com/workflow"
                  id="Definitions_%1$s" targetNamespace="wp008f">
                  <process id="%1$s" name="WP008F %1$s" isExecutable="true">
                    <startEvent id="start" name="start" flowable:initiator="initiator"/>
                    <userTask id="approve" name="审批1" flowable:assignee="${userId}">
                      <extensionElements><omni:assignment>%2$s</omni:assignment></extensionElements>
                      <multiInstanceLoopCharacteristics isSequential="false"
                        flowable:collection="${candidateResolver.resolve(execution)}"
                        flowable:elementVariable="userId">
                        <completionCondition xsi:type="tFormalExpression">${rejectedCount &gt; 0 || approvedCount &gt;= requiredApprovals}</completionCondition>
                      </multiInstanceLoopCharacteristics>
                    </userTask>
                    <userTask id="approve2" name="审批2" flowable:assignee="${userId}">
                      <extensionElements><omni:assignment>%2$s</omni:assignment></extensionElements>
                      <multiInstanceLoopCharacteristics isSequential="false"
                        flowable:collection="${candidateResolver.resolve(execution)}"
                        flowable:elementVariable="userId">
                        <completionCondition xsi:type="tFormalExpression">${rejectedCount &gt; 0 || approvedCount &gt;= requiredApprovals}</completionCondition>
                      </multiInstanceLoopCharacteristics>
                    </userTask>
                    <endEvent id="end" name="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="f2" sourceRef="approve" targetRef="approve2"/>
                    <sequenceFlow id="f3" sourceRef="approve2" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(processKey, json);
    }

    /** 循环回到同一 MI 节点的 BPMN。 */
    private static String loopBackBpmn(String processKey) {
        String json = assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:omni="http://omni.com/workflow"
                  id="Definitions_%1$s" targetNamespace="wp008f">
                  <process id="%1$s" name="WP008F %1$s" isExecutable="true">
                    <startEvent id="start" name="start" flowable:initiator="initiator"/>
                    <serviceTask id="seed" name="seed"
                      flowable:expression="${execution.setVariable('loopDone', false)}"/>
                    <userTask id="approve" name="审批" flowable:assignee="${userId}">
                      <extensionElements><omni:assignment>%2$s</omni:assignment></extensionElements>
                      <multiInstanceLoopCharacteristics isSequential="false"
                        flowable:collection="${candidateResolver.resolve(execution)}"
                        flowable:elementVariable="userId">
                        <completionCondition xsi:type="tFormalExpression">${rejectedCount &gt; 0 || approvedCount &gt;= requiredApprovals}</completionCondition>
                      </multiInstanceLoopCharacteristics>
                    </userTask>
                    <exclusiveGateway id="g1" name="g1" default="f4"/>
                    <serviceTask id="mark" name="mark"
                      flowable:expression="${execution.setVariable('loopDone', true)}"/>
                    <endEvent id="end" name="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="seed"/>
                    <sequenceFlow id="f6" sourceRef="seed" targetRef="approve"/>
                    <sequenceFlow id="f2" sourceRef="approve" targetRef="g1"/>
                    <sequenceFlow id="f3" sourceRef="g1" targetRef="mark">
                      <conditionExpression xsi:type="tFormalExpression">${loopDone == false}</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="f4" sourceRef="g1" targetRef="end"/>
                    <sequenceFlow id="f5" sourceRef="mark" targetRef="approve"/>
                  </process>
                </definitions>
                """.formatted(processKey, json);
    }

    private static String taskOf(List<org.flowable.task.api.Task> tasks, String assignee) {
        return tasks.stream().filter(t -> assignee.equals(t.getAssignee())).findFirst().orElseThrow().getId();
    }

    private Map<String, String> historyReasons(String pid) {
        Map<String, String> m = new HashMap<>();
        for (HistoricTaskInstance ht : historyService.createHistoricTaskInstanceQuery().processInstanceId(pid).list()) {
            m.put(ht.getAssignee() + "/" + ht.getName(), ht.getDeleteReason());
        }
        return m;
    }

    private String start(String prefix, String approvalMode) {
        String key = uniqKey(prefix);
        return withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", approvalMode)), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-" + key);
            sp.setTitle("mi-" + approvalMode);
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
    }

    private void approve(String taskId, boolean ok, String comment, Long userId) {
        withTenantRuntime(1L, () -> {
            ApprovalRequest ar = new ApprovalRequest();
            ar.setApproved(ok);
            ar.setComment(comment);
            approvalService.complete(taskId, ar, userId, 1L);
            return null;
        });
    }

    private void cleanup(String pid) {
        try {
            withTenantRuntime(1L, () -> {
                if (runtimeService.createProcessInstanceQuery().processInstanceId(pid).count() > 0) {
                    instanceService.terminate(pid, "it-cleanup", 2210L, 1L);
                }
                return null;
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
