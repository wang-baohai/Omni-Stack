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
 * FW03：v1 发布启动→独立 v2 发布；v1 XML/hash 不变，旧实例绑定 v1，新实例绑定 v2，归档版新启拒绝。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW03 版本不可变与实例绑定")
class Fw03VersionImmutabilityIT extends AbstractFwIT {

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

    /** v1/v2 双版本链与绑定隔离。 */
    @Test
    @DisplayName("FW03-1 版本链与实例绑定")
    void versionChainAndBinding() throws Exception {
        String key = uniqKey("wp008f-ver");
        // v1
        WfProcessModel model = withTenantRuntime(1L, () ->
                newDraftModel(modelService, 1L, "wp008f", key, "wp008f_it",
                        bpmn(key, "WP008F审批-1期", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")), null));
        PublishResult v1 = withTenantRuntime(1L, () -> modelService.publishModel(model.getId(), "wp008f"));
        String sha1 = versionSha(v1.getVersionId());
        String xml1 = versionXml(v1.getVersionId());

        // I1 @ v1（f1_req 发起，候选 2211/2212）
        String i1 = withTenantRuntime(1L, () -> {
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(v1.getVersionId());
            sp.setBusinessKey("WP008FI1-" + key);
            sp.setTitle("WP008F I1");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
        String i1Def = runtimeService.createProcessInstanceQuery().processInstanceId(i1).singleResult().getProcessDefinitionId();

        // v2 独立草稿再发布（节点名可比对）
        withTenantRuntime(1L, () -> {
            com.omni.workflow.dto.SaveDraftRequest sd = new com.omni.workflow.dto.SaveDraftRequest();
            sd.setBpmnXml(bpmn(key, "WP008F审批-2期", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")));
            sd.setDesignerJson("{\"nodes\":[]}");
            modelService.saveDraft(model.getId(), sd, "wp008f");
            return null;
        });
        PublishResult v2 = withTenantRuntime(1L, () -> modelService.publishModel(model.getId(), "wp008f"));
        assertThat(v2.getBusinessVersion()).isEqualTo(2);

        // v1 XML/hash 不变；v1 状态 ARCHIVED
        assertThat(versionSha(v1.getVersionId())).isEqualTo(sha1);
        assertThat(versionXml(v1.getVersionId())).isEqualTo(xml1);
        assertThat(versionStatus(v1.getVersionId())).isEqualTo("ARCHIVED");

        // 旧实例仍绑定 v1 定义并可继续审批
        List<Task> i1Tasks = taskService.createTaskQuery().processInstanceId(i1).list();
        assertThat(i1Tasks).hasSize(2);
        assertThat(i1Tasks.get(0).getTaskDefinitionKey()).isEqualTo("approve");
        String i1DefAfter = extProcessDefId(i1);
        assertThat(i1DefAfter).isEqualTo(i1Def);

        // 新实例绑定 v2 定义
        String i2 = withTenantRuntime(1L, () -> {
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(v2.getVersionId());
            sp.setBusinessKey("WP008FI2-" + key);
            sp.setTitle("WP008F I2");
            return instanceService.start(sp, 2210L, "f1_req", 1L);
        });
        assertThat(extProcessDefId(i2)).isEqualTo(v2.getProcessDefinitionId());
        assertThat(v2.getProcessDefinitionId()).isNotEqualTo(i1Def);

        // 归档版新启拒绝（契约）
        withTenantRuntime(1L, () -> {
            StartProcessRequest sp = new StartProcessRequest();
            sp.setModelVersionId(v1.getVersionId());
            sp.setBusinessKey("WP008FI-X-" + key);
            sp.setTitle("archived start");
            assertThatThrownBy(() -> instanceService.start(sp, 2210L, "f1_req", 1L))
                    .isInstanceOf(BusinessException.class);
            return null;
        });

        // 清理本轮 I1/I2 实例（F 环境允许真实终止）
        for (String pid : List.of(i1, i2)) {
            try {
                withTenantRuntime(1L, () -> {
                    instanceService.terminate(pid, "wp008f-it-cleanup", 2210L, 1L);
                    return null;
                });
            } catch (Exception ignored) {
                // 允许已结束
            }
        }
    }

    private String versionSha(Long versionId) {
        return jdbc.queryForObject("SELECT xml_sha256 FROM omni_workflow.wf_process_model_version WHERE id=?",
                String.class, versionId);
    }

    private String versionXml(Long versionId) {
        return jdbc.queryForObject("SELECT bpmn_xml FROM omni_workflow.wf_process_model_version WHERE id=?",
                String.class, versionId);
    }

    private String versionStatus(Long versionId) {
        return jdbc.queryForObject("SELECT status FROM omni_workflow.wf_process_model_version WHERE id=?",
                String.class, versionId);
    }

    private String extProcessDefId(String processInstanceId) {
        return jdbc.queryForObject(
                "SELECT process_definition_id FROM omni_workflow.wf_process_instance_ext WHERE process_instance_id=?",
                String.class, processInstanceId);
    }
}
