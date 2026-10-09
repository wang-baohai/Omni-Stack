package com.omni.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 安全审批图解析测试。 */
class ApprovalPreviewParserTest {

    private final ApprovalPreviewParser parser = new ApprovalPreviewParser(new ObjectMapper());

    /** 单路径流程应返回审批摘要，但不得返回 assignment 原文。 */
    @Test
    void shouldReturnLinearBusinessSummaryWithoutRawAssignment() {
        ApprovalPreviewParser.PreviewGraph graph = parser.parse(linearBpmn());

        assertThat(graph.hasBranches()).isFalse();
        assertThat(graph.linearSummary()).containsExactly("部门负责人审批");
        assertThat(graph.nodes()).anySatisfy(node -> {
            assertThat(node.getRoleCode()).isEqualTo("DEPT_LEADER");
            assertThat(node.getApprovalMode()).isEqualTo("ANY");
            assertThat(node.getDescription()).doesNotContain("anchorParams");
        });
    }

    /** 条件网关应标记为分支且隐藏原始表达式。 */
    @Test
    void shouldRedactConditionalExpressionForBranchingFlow() {
        ApprovalPreviewParser.PreviewGraph graph = parser.parse(branchBpmn());

        assertThat(graph.hasBranches()).isTrue();
        assertThat(graph.linearSummary()).isNull();
        assertThat(graph.edges()).anySatisfy(edge -> {
            if (edge.getConditionSummary() != null) {
                assertThat(edge.getConditionSummary()).isEqualTo("已配置条件（内容已隐藏）");
                assertThat(edge.getConditionSummary()).doesNotContain("amount");
            }
        });
    }

    /** 包含 DOCTYPE 的 XML 必须被 XXE 安全配置拒绝。 */
    @Test
    void shouldRejectDoctype() {
        String malicious = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "
                + "\"file:///etc/passwd\">]><definitions><process id=\"p\">&xxe;</process></definitions>";

        assertThatThrownBy(() -> parser.parse(malicious))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String linearBpmn() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:omni="http://omni-stack.com/schema/bpmn">
                  <process id="purchase">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="部门负责人审批">
                      <extensionElements>
                        <omni:assignment>{"roleCode":"DEPT_LEADER","approvalMode":"ANY","anchorParams":{}}</omni:assignment>
                      </extensionElements>
                    </userTask>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }

    private String branchBpmn() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <process id="purchase">
                    <startEvent id="start"/>
                    <exclusiveGateway id="amount-gateway" default="default-flow"/>
                    <userTask id="manager" name="经理审批"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="to-gateway" sourceRef="start" targetRef="amount-gateway"/>
                    <sequenceFlow id="large-flow" sourceRef="amount-gateway" targetRef="manager">
                      <conditionExpression xsi:type="tFormalExpression">${amount &gt; 10000}</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="default-flow" sourceRef="amount-gateway" targetRef="end"/>
                    <sequenceFlow id="manager-end" sourceRef="manager" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }
}
