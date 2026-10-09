package com.omni.workflow.it;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FW10：完成元数据 + Outbox 同事务原子性、重复完成事件闩、故障回滚一致性。
 * <p>故障注入为测试事务边界（外层 TransactionTemplate 强制回滚），服务与 DB 均真实（F_REAL）。
 * sys_mq_message 归属各服务自己的库（workflow 服务写入 omni_workflow.sys_mq_message）。</p>
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW10 完成事件Outbox事务")
class Fw10OutboxAtomicityIT extends AbstractFwIT {

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
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate txTemplate;

    /** 正常完成：ext.completion_event_id 与 sys_mq_message(PENDING) 同事务产生。 */
    @Test
    @DisplayName("FW10-1 完成事件与Outbox成对产生")
    void completionCreatesOutboxAtomically() {
        String pid = startCrossService("wp008f-ob1");
        approveAll(pid);
        String eventId = completionEventId(pid);
        assertThat(eventId).isNotBlank();
        assertThat(outboxByEvent(eventId)).isEqualTo(1);
        Integer status = jdbc.queryForObject(
                "SELECT status FROM omni_workflow.sys_mq_message WHERE payload LIKE ?",
                Integer.class, "%" + eventId + "%");
        assertThat(status).as("PENDING=0").isZero();
    }

    /** 重复完成调用路径不再产生第二条消息（DB 门闩 uk_wf_completion_event）。 */
    @Test
    @DisplayName("FW10-2 重复完成事件闩")
    void duplicateCompletionLatched() {
        String pid = startCrossService("wp008f-ob2");
        approveAll(pid);
        int before = outboxByEvent(completionEventId(pid));
        assertThat(before).isEqualTo(1);
        // 已完成实例再次 approve：409/业务异常，不产生新事件
        assertThat(withTenantRuntime(1L, () ->
                taskService.createTaskQuery().processInstanceId(pid).count())).isZero();
        // 门闩列唯一：同实例至多一条 completion_event_id + 一条 payload 含该事件的消息
        assertThat(outboxByEvent(completionEventId(pid))).isEqualTo(before);
    }

    /** 完成事务中途失败：ext.completion_event_id 与 sys_mq_message 共同回滚，任务保持可重试。 */
    @Test
    @DisplayName("FW10-3 中途故障共同回滚")
    void failureRollsBackBoth() {
        String pid = startCrossService("wp008f-ob3");
        List<Task> tasks = withTenantRuntime(1L, () ->
                taskService.createTaskQuery().processInstanceId(pid).list());
        assertThat(tasks).hasSize(2);
        approve(tasks.get(0).getId(), 2211L);
        Task last = withTenantRuntime(1L, () ->
                taskService.createTaskQuery().processInstanceId(pid).singleResult());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                withTenantRuntime(1L, () -> txTemplate.execute(s -> {
                    ApprovalRequest ar = new ApprovalRequest();
                    ar.setApproved(true);
                    ar.setComment("将在事务内回滚");
                    approvalService.complete(last.getId(), ar, 2212L, 1L);
                    throw new RuntimeException("WP008F injected failure after outbox write");
                })));

        // 共同回滚：无 Outbox、ext 未完成、最后任务仍在
        assertThat(completionEventId(pid)).isNull();
        assertThat(outboxByPid(pid)).isZero();
        assertThat(withTenantRuntime(1L, () -> taskService.createTaskQuery().processInstanceId(pid).count())).isEqualTo(1);

        // 合法重试成功完成，产生恰一条 Outbox
        Task retry = withTenantRuntime(1L, () -> taskService.createTaskQuery().processInstanceId(pid).singleResult());
        approve(retry.getId(), 2212L);
        assertThat(outboxByEvent(completionEventId(pid))).isEqualTo(1);
    }

    private String completionEventId(String pid) {
        List<String> list = jdbc.queryForList(
                "SELECT completion_event_id FROM omni_workflow.wf_process_instance_ext WHERE process_instance_id=?",
                String.class, pid);
        return list.isEmpty() ? null : list.get(0);
    }

    private int outboxByEvent(String eventId) {
        if (eventId == null) {
            return 0;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_workflow.sys_mq_message WHERE payload LIKE ?",
                Integer.class, "%" + eventId + "%");
        return n == null ? 0 : n;
    }

    private int outboxByPid(String pid) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_workflow.sys_mq_message WHERE payload LIKE ?",
                Long.class, "%" + pid + "%");
        return n == null ? 0 : n.intValue();
    }

    private String startCrossService(String prefix) {
        String key = uniqKey(prefix);
        return withTenantRuntime(1L, () -> {
            WfProcessModel m = newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                    bpmn(key, "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null);
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(pr.getVersionId());
            sp.setBusinessKey("WP008F-" + key);
            sp.setBusinessType("WP008FTEST");
            sp.setRequestId("wp008f-req-" + key);
            sp.setTitle("fw10");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
    }

    private void approveAll(String pid) {
        while (true) {
            List<Task> tasks = withTenantRuntime(1L, () ->
                    taskService.createTaskQuery().processInstanceId(pid).list());
            if (tasks.isEmpty()) {
                return;
            }
            Task t = tasks.get(0);
            Long uid = Long.valueOf(t.getAssignee());
            approve(t.getId(), uid);
            if (runtimeService.createProcessInstanceQuery().processInstanceId(pid).count() == 0) {
                return;
            }
        }
    }

    private void approve(String taskId, Long userId) {
        withTenantRuntime(1L, () -> {
            ApprovalRequest ar = new ApprovalRequest();
            ar.setApproved(true);
            ar.setComment("ok-" + userId);
            approvalService.complete(taskId, ar, userId, 1L);
            return null;
        });
    }
}
