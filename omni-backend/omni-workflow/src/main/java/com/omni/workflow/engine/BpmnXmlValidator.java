package com.omni.workflow.engine;

import com.omni.workflow.dto.ValidateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * BPMN 2.0 XML 校验器。
 * <p>
 * 校验规则：
 * <ol>
 *   <li>XML 格式合法</li>
 *   <li>包含且仅包含一个可执行 process</li>
 *   <li>process id 与 modelKey 一致</li>
 *   <li>至少有一个 StartEvent 和一个 EndEvent</li>
 *   <li>UserTask 必须有 omni:assignment 扩展</li>
 *   <li>抄送 ServiceTask 必须有 omni:cc 扩展</li>
 *   <li>ExclusiveGateway 必须设置 default 分支</li>
 *   <li>default 分支不允许带 conditionExpression</li>
 *   <li>SequenceFlow 的 sourceRef/targetRef 引用有效</li>
 * </ol>
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
public class BpmnXmlValidator {

    /**
     * BPMN 2.0 标准命名空间 URI。
     * <p>
     * 对应 bpmn-js 导出 XML 中 {@code xmlns:bpmn} 的值。
     * 注意：这不是 targetNamespace，而是 BPMN 元素的 XML 命名空间。
     * </p>
     */
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    /**
     * 校验 BPMN XML。
     *
     * @param bpmnXml  BPMN XML 字符串
     * @param modelKey 期望的 process id
     * @return 校验结果
     */
    public ValidateResult validate(String bpmnXml, String modelKey) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (bpmnXml == null || bpmnXml.isBlank()) {
            errors.add("BPMN XML 为空");
            return ValidateResult.builder()
                    .valid(false).errors(errors).warnings(warnings).build();
        }

        // 1. XML 格式合法性
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 防止 XXE 攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            errors.add("XML 解析失败: " + e.getMessage());
            return ValidateResult.builder()
                    .valid(false).errors(errors).warnings(warnings).build();
        }

        // 2. 检查可执行 process
        NodeList processes = doc.getElementsByTagNameNS(BPMN_NS, "process");
        int executableCount = 0;
        Element processElement = null;
        for (int i = 0; i < processes.getLength(); i++) {
            Element proc = (Element) processes.item(i);
            if ("true".equals(proc.getAttribute("isExecutable"))) {
                executableCount++;
                processElement = proc;
            }
        }

        if (executableCount == 0) {
            errors.add("未找到可执行 process（isExecutable=true）");
            return ValidateResult.builder()
                    .valid(false).errors(errors).warnings(warnings).build();
        }
        if (executableCount > 1) {
            errors.add("存在多个可执行 process，仅支持单流程模型");
            return ValidateResult.builder()
                    .valid(false).errors(errors).warnings(warnings).build();
        }

        // 3. process id 与 modelKey 一致
        String processId = processElement.getAttribute("id");
        if (!modelKey.equals(processId)) {
            errors.add("process id（" + processId + "）与模型标识（" + modelKey + "）不一致");
        }

        // 收集所有元素 ID 用于引用校验
        Set<String> allIds = new HashSet<>();
        collectAllIds(processElement, allIds);

        // 4. 检查 StartEvent 和 EndEvent
        NodeList startEvents = processElement.getElementsByTagNameNS(BPMN_NS, "startEvent");
        NodeList endEvents = processElement.getElementsByTagNameNS(BPMN_NS, "endEvent");
        if (startEvents.getLength() == 0) {
            errors.add("缺少开始事件（StartEvent）");
        }
        if (endEvents.getLength() == 0) {
            errors.add("缺少结束事件（EndEvent）");
        }

        // 5. 检查 UserTask 必须有 omni:assignment
        NodeList userTasks = processElement.getElementsByTagNameNS(BPMN_NS, "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            Element task = (Element) userTasks.item(i);
            if (!hasExtensionElement(task, "omni:assignment")) {
                String taskId = task.getAttribute("id");
                String taskName = task.getAttribute("name");
                errors.add("审批节点 [" + taskId + "]（" + taskName + "）缺少 omni:assignment 配置");
            }
        }

        // 6. 检查抄送 ServiceTask 必须有 omni:cc
        NodeList serviceTasks = processElement.getElementsByTagNameNS(BPMN_NS, "serviceTask");
        for (int i = 0; i < serviceTasks.getLength(); i++) {
            Element task = (Element) serviceTasks.item(i);
            String delegateExpr = task.getAttributeNS("http://flowable.org/bpmn", "delegateExpression");
            if (delegateExpr.contains("ccNotifyDelegate") && !hasExtensionElement(task, "omni:cc")) {
                String taskId = task.getAttribute("id");
                String taskName = task.getAttribute("name");
                errors.add("抄送节点 [" + taskId + "]（" + taskName + "）缺少 omni:cc 配置");
            }
        }

        // 7. 检查 ExclusiveGateway 必须设置 default
        Set<String> defaultFlowIds = new HashSet<>();
        NodeList gateways = processElement.getElementsByTagNameNS(BPMN_NS, "exclusiveGateway");
        for (int i = 0; i < gateways.getLength(); i++) {
            Element gw = (Element) gateways.item(i);
            String defaultFlow = gw.getAttribute("default");
            if (defaultFlow == null || defaultFlow.isBlank()) {
                String gwId = gw.getAttribute("id");
                String gwName = gw.getAttribute("name");
                errors.add("排他网关 [" + gwId + "]（" + gwName + "）未设置默认分支（default）");
            } else {
                defaultFlowIds.add(defaultFlow);
            }
        }

        // 7.5 检查 default 分支不允许带 conditionExpression（Flowable 规则）
        NodeList sequenceFlows = processElement.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element flow = (Element) sequenceFlows.item(i);
            String flowId = flow.getAttribute("id");
            if (defaultFlowIds.contains(flowId)) {
                NodeList conditions = flow.getElementsByTagNameNS(BPMN_NS, "conditionExpression");
                if (conditions.getLength() > 0) {
                    errors.add("默认分支 [" + flowId + "] 不允许设置条件表达式（conditionExpression），"
                            + "默认分支的含义是「其他条件都不满足时走这条路」");
                }
            }
        }

        // 8. 检查 SequenceFlow 引用有效
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element flow = (Element) sequenceFlows.item(i);
            String flowId = flow.getAttribute("id");
            String sourceRef = flow.getAttribute("sourceRef");
            String targetRef = flow.getAttribute("targetRef");

            if (sourceRef != null && !sourceRef.isBlank() && !allIds.contains(sourceRef)) {
                errors.add("连线 [" + flowId + "] 的 sourceRef 引用无效: " + sourceRef);
            }
            if (targetRef != null && !targetRef.isBlank() && !allIds.contains(targetRef)) {
                errors.add("连线 [" + flowId + "] 的 targetRef 引用无效: " + targetRef);
            }
        }

        // 警告检查
        if (startEvents.getLength() > 1) {
            warnings.add("存在多个开始事件，建议只保留一个");
        }

        return ValidateResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * 检查元素是否包含指定名称的扩展子元素。
     */
    private boolean hasExtensionElement(Element parent, String elementName) {
        NodeList extElements = parent.getElementsByTagNameNS(BPMN_NS, "extensionElements");
        if (extElements.getLength() == 0) return false;

        Element extParent = (Element) extElements.item(0);
        NodeList children = extParent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                String localName = child.getLocalName();
                String nodeName = child.getNodeName();
                if (elementName.equals(localName)
                        || elementName.equals(nodeName)
                        || nodeName.endsWith(":" + elementName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 收集 process 内所有元素的 id 属性。
     */
    private void collectAllIds(Element process, Set<String> ids) {
        NodeList children = process.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String id = el.getAttribute("id");
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
    }
}
