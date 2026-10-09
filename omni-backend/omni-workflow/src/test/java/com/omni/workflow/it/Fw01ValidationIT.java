package com.omni.workflow.it;

import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.PublishResult;
import com.omni.workflow.dto.ValidateResult;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.service.WorkflowModelService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FW01：正式 schema/Flowable 上的模型唯一性、BPMN 校验失败与无残留部署。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FW01 校验与部署残留")
class Fw01ValidationIT extends AbstractFwIT {

    @Autowired
    private WorkflowModelService modelService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 同租户重复 modelKey 拒绝。 */
    @Test
    @DisplayName("FW01-1 同tenant重复modelKey拒绝")
    void duplicateModelKeyRejected() throws Exception {
        String key = uniqKey("wp008f-dup");
        withTenant(1L, () -> {
            WfProcessModel first = modelService.createModel(createReq(key), 1L, "wp008f");
            assertThat(first.getId()).isNotNull();
            assertThatThrownBy(() -> modelService.createModel(createReq(key), 1L, "wp008f"))
                    .isInstanceOf(BusinessException.class);
            return null;
        });
    }

    /** process id 与 modelKey 不匹配：校验报错且发布失败，无新部署。 */
    @Test
    @DisplayName("FW01-2 process id不匹配拒绝且无部署残留")
    void processIdMismatch() {
        String key = uniqKey("wp008f-mism");
        String xml = bpmn("some-other-id", "审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL"));
        long depBefore = deploymentCount();
        PublishResultHolder h = new PublishResultHolder();
        withTenantRuntime(1L, () -> {
            WfProcessModel m = modelService.createModel(createReq(key), 1L, "wp008f");
            saveDraftRaw(m.getId(), xml);
            ValidateResult vr = modelService.validateModel(m.getId());
            h.errors = vr.getErrors();
            assertThat(vr.isValid()).isFalse();
            assertThat(String.join(";", vr.getErrors())).containsIgnoringCase("process");
            assertThatThrownBy(() -> modelService.publishModel(m.getId(), "wp008f"))
                    .isInstanceOf(RuntimeException.class);
            return null;
        });
        assertThat(h.errors).isNotEmpty();
        assertThat(deploymentCount()).isEqualTo(depBefore);
    }

    /** 缺 omni:assignment / 错误流引用 / XXE 均校验失败且零部署。 */
    @Test
    @DisplayName("FW01-3 缺assignment/坏流引用/XXE拒绝")
    void invalidXmlVariants() {
        String withEmpty = bpmn("PLACEHOLDER", "审批", "");
        // 真实缺失变体：整个 extensionElements/omni:assignment 块不存在
        String noAssign = withEmpty.replaceAll("(?s)\\s*<extensionElements>.*?</extensionElements>", "");
        String badFlow = noAssign.replace("targetRef=\"approve\"", "targetRef=\"ghost-node\"");
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE foo [ <!ENTITY x SYSTEM \"file:///etc/passwd\"> ]>"
                + noAssign;
        String[] labels = {"no-assignment", "bad-flow-ref", "xxe"};
        String[] variants = {noAssign, badFlow, xxe};
        for (int i = 0; i < variants.length; i++) {
            final int idx = i;
            String key = uniqKey("wp008f-bad");
            withTenantRuntime(1L, () -> {
                long depBefore = deploymentCount();
                WfProcessModel m = modelService.createModel(createReq(key), 1L, "wp008f");
                String xml = variants[idx].replace("PLACEHOLDER", key);
                saveDraftRaw(m.getId(), xml);
                assertThatThrownBy(() -> modelService.publishModel(m.getId(), "wp008f"))
                        .as("变体应被拒绝: %s", labels[idx])
                        .isInstanceOf(RuntimeException.class);
                assertThat(deploymentCount()).isEqualTo(depBefore);
                return null;
            });
        }
        // 登记项（非阻断）：空 omni:assignment 元素当前仅校存在性不校内容，属 P1 校验深度缺口
    }

    /** 有效 XML 发布成功并生成部署/定义。 */
    @Test
    @DisplayName("FW01-4 有效XML发布成功")
    void validXmlPublishes() {
        String key = uniqKey("wp008f-ok");
        withTenantRuntime(1L, () -> {
            WfProcessModel m = modelService.createModel(createReq(key), 1L, "wp008f");
            saveDraftRaw(m.getId(), bpmn(key, "WP008F审批", assign("WP008_APPROVER", "SAME_UNIT", "ERROR", "ALL")));
            PublishResult pr = modelService.publishModel(m.getId(), "wp008f");
            assertThat(pr.getDeploymentId()).isNotBlank();
            assertThat(pr.getProcessDefinitionId()).isNotBlank();
            assertThat(pr.getBusinessVersion()).isEqualTo(1);
            assertThat(repositoryService.createProcessDefinitionQuery()
                    .processDefinitionTenantId("1").processDefinitionKey(key).count()).isEqualTo(1);
            return null;
        });
    }

    private long deploymentCount() {
        AtomicLong n = new AtomicLong();
        jdbc.query("SELECT COUNT(*) c FROM omni_workflow.ACT_RE_DEPLOYMENT", rs -> { n.set(rs.getLong("c")); });
        return n.get();
    }

    private void saveDraftRaw(Long modelId, String xml) {
        com.omni.workflow.dto.SaveDraftRequest sd = new com.omni.workflow.dto.SaveDraftRequest();
        sd.setBpmnXml(xml);
        sd.setDesignerJson("{\"nodes\":[]}");
        modelService.saveDraft(modelId, sd, "wp008f");
    }

    private com.omni.workflow.dto.CreateModelRequest createReq(String key) {
        com.omni.workflow.dto.CreateModelRequest cr = new com.omni.workflow.dto.CreateModelRequest();
        cr.setModelKey(key);
        cr.setModelName("WP008F " + key);
        cr.setCategory("wp008f_it");
        cr.setDesignerJson("{\"nodes\":[]}");
        return cr;
    }

    /** 局部容器（lambda 内传递校验错误）。 */
    private static final class PublishResultHolder {
        private java.util.List<String> errors;
    }
}
