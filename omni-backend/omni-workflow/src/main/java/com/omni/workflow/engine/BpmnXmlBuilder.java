package com.omni.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

/**
 * BPMN 2.0 XML 生成器。
 * <p>
 * 从设计器 JSON 生成符合 Flowable 引擎规范的 BPMN 2.0 XML。
 * 支持 UserTask（含 omni:assignment 扩展）、ServiceTask（抄送节点）、
 * ExclusiveGateway、SequenceFlow（含条件表达式）、bpmndi 布局。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmnXmlBuilder {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMN_DI_NS = "http://www.omg.org/specifications/BPMN_DI";
    private static final String OMNI_NS = "http://omni.com/workflow";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    private final ObjectMapper objectMapper;

    /**
     * 从设计器 JSON 字符串生成 BPMN 2.0 XML。
     *
     * @param modelKey      模型标识（作为 BPMN process id）
     * @param modelName     模型名称
     * @param designerJson  设计器 JSON
     * @return BPMN 2.0 XML 字符串
     * @throws Exception 生成失败时抛出
     */
    public String build(String modelKey, String modelName, String designerJson) throws Exception {
        JsonNode root = objectMapper.readTree(designerJson);
        JsonNode nodes = root.get("nodes");
        JsonNode edges = root.get("edges");

        // 创建 DOM 文档（使用安全的工厂防止 XXE 攻击）
        DocumentBuilderFactory factory = XmlSecurityUtils.createSafeDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // 根元素 definitions
        Element definitions = doc.createElementNS(BPMN_NS, "bpmn:definitions");
        definitions.setAttribute("xmlns:bpmn", BPMN_NS);
        definitions.setAttribute("xmlns:bpmndi", BPMN_DI_NS);
        definitions.setAttribute("xmlns:omni", OMNI_NS);
        definitions.setAttribute("xmlns:flowable", FLOWABLE_NS);
        definitions.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        definitions.setAttribute("targetNamespace", OMNI_NS);
        definitions.setAttribute("id", "definitions_" + modelKey);
        doc.appendChild(definitions);

        // process 元素
        Element process = doc.createElementNS(BPMN_NS, "bpmn:process");
        process.setAttribute("id", modelKey);
        process.setAttribute("name", modelName);
        process.setAttribute("isExecutable", "true");
        definitions.appendChild(process);

        // 遍历节点生成 BPMN 元素
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                String type = getTextValue(node, "type");
                String id = getTextValue(node, "id");
                String name = getTextValue(node, "name");

                switch (type) {
                    case "StartEvent" -> appendStartEvent(doc, process, id, name);
                    case "EndEvent" -> appendEndEvent(doc, process, id, name);
                    case "UserTask" -> appendUserTask(doc, process, id, name, node);
                    case "ServiceTask" -> appendServiceTask(doc, process, id, name, node);
                    case "ExclusiveGateway" -> appendExclusiveGateway(doc, process, id, name, node);
                    default -> log.warn("未知节点类型: type={}, id={}", type, id);
                }
            }
        }

        // 遍历边生成 SequenceFlow
        if (edges != null && edges.isArray()) {
            for (JsonNode edge : edges) {
                String id = getTextValue(edge, "id");
                String sourceRef = getTextValue(edge, "sourceRef");
                String targetRef = getTextValue(edge, "targetRef");
                String name = getTextValue(edge, "name");
                JsonNode properties = edge.get("properties");

                appendSequenceFlow(doc, process, id, name, sourceRef, targetRef, properties);
            }
        }

        // 生成 bpmndi 布局（简化版）
        appendBpmnDiagram(doc, definitions, modelKey, nodes, edges);

        // 转换为 XML 字符串
        return documentToString(doc);
    }

    // ======================== 节点生成方法 ========================

    private void appendStartEvent(Document doc, Element process, String id, String name) {
        Element el = doc.createElementNS(BPMN_NS, "bpmn:startEvent");
        el.setAttribute("id", id);
        if (name != null) el.setAttribute("name", name);
        process.appendChild(el);
    }

    private void appendEndEvent(Document doc, Element process, String id, String name) {
        Element el = doc.createElementNS(BPMN_NS, "bpmn:endEvent");
        el.setAttribute("id", id);
        if (name != null) el.setAttribute("name", name);
        process.appendChild(el);
    }

    private void appendUserTask(Document doc, Element process, String id, String name, JsonNode node) {
        Element el = doc.createElementNS(BPMN_NS, "bpmn:userTask");
        el.setAttribute("id", id);
        if (name != null) el.setAttribute("name", name);

        JsonNode properties = node.get("properties");
        JsonNode assignment = properties != null ? properties.get("assignment") : null;

        if (assignment != null) {
            // extensionElements: omni:assignment + taskListener
            Element extElements = doc.createElementNS(BPMN_NS, "bpmn:extensionElements");

            // omni:assignment 配置
            Element assignmentEl = doc.createElementNS(OMNI_NS, "omni:assignment");
            assignmentEl.setTextContent(assignment.toString());
            extElements.appendChild(assignmentEl);

            // flowable:taskListener 注册 scopedRoleAssignmentListener
            Element listener = doc.createElementNS(FLOWABLE_NS, "flowable:taskListener");
            listener.setAttribute("event", "create");
            listener.setAttribute("delegateExpression", "${scopedRoleAssignmentListener}");
            extElements.appendChild(listener);

            el.appendChild(extElements);
        }

        process.appendChild(el);
    }

    private void appendServiceTask(Document doc, Element process, String id, String name, JsonNode node) {
        Element el = doc.createElementNS(BPMN_NS, "bpmn:serviceTask");
        el.setAttribute("id", id);
        if (name != null) el.setAttribute("name", name);

        JsonNode properties = node.get("properties");
        String serviceType = properties != null && properties.has("serviceType")
                ? properties.get("serviceType").asText() : "CC";

        if ("CC".equals(serviceType)) {
            el.setAttribute("flowable:delegateExpression", "${ccNotifyDelegate}");

            JsonNode ccConfig = properties != null ? properties.get("ccConfig") : null;
            if (ccConfig != null) {
                Element extElements = doc.createElementNS(BPMN_NS, "bpmn:extensionElements");
                Element ccEl = doc.createElementNS(OMNI_NS, "omni:cc");
                ccEl.setTextContent(ccConfig.toString());
                extElements.appendChild(ccEl);
                el.appendChild(extElements);
            }
        }

        process.appendChild(el);
    }

    private void appendExclusiveGateway(Document doc, Element process, String id, String name, JsonNode node) {
        Element el = doc.createElementNS(BPMN_NS, "bpmn:exclusiveGateway");
        el.setAttribute("id", id);
        if (name != null) el.setAttribute("name", name);

        JsonNode properties = node.get("properties");
        if (properties != null && properties.has("defaultFlow")) {
            el.setAttribute("default", properties.get("defaultFlow").asText());
        }

        process.appendChild(el);
    }

    private void appendSequenceFlow(Document doc, Element process, String id, String name,
                                     String sourceRef, String targetRef, JsonNode properties) {
        Element el = doc.createElementNS(BPMN_NS, "bpmn:sequenceFlow");
        el.setAttribute("id", id);
        if (name != null) el.setAttribute("name", name);
        el.setAttribute("sourceRef", sourceRef);
        el.setAttribute("targetRef", targetRef);

        if (properties != null && properties.has("conditionExpression")) {
            String condition = properties.get("conditionExpression").asText();
            if (!condition.isBlank()) {
                Element condEl = doc.createElementNS(BPMN_NS, "bpmn:conditionExpression");
                condEl.setAttribute("xsi:type", "bpmn:tFormalExpression");
                condEl.setTextContent(condition);
                el.appendChild(condEl);
            }
        }

        process.appendChild(el);
    }

    // ======================== 布局生成 ========================

    private void appendBpmnDiagram(Document doc, Element definitions, String processId,
                                    JsonNode nodes, JsonNode edges) {
        Element diagram = doc.createElementNS(BPMN_DI_NS, "bpmndi:BPMNDiagram");
        diagram.setAttribute("id", "diagram_" + processId);

        Element plane = doc.createElementNS(BPMN_DI_NS, "bpmndi:BPMNPlane");
        plane.setAttribute("id", "plane_" + processId);
        plane.setAttribute("bpmnElement", processId);
        diagram.appendChild(plane);

        // 为每个节点生成 shape
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                String id = getTextValue(node, "id");
                JsonNode position = node.get("position");
                double x = position != null && position.has("x") ? position.get("x").asDouble() : 100;
                double y = position != null && position.has("y") ? position.get("y").asDouble() : 100;

                String type = getTextValue(node, "type");
                double width = "ExclusiveGateway".equals(type) ? 50 : 100;
                double height = "ExclusiveGateway".equals(type) ? 50 : 80;
                if ("StartEvent".equals(type) || "EndEvent".equals(type)) {
                    width = 36;
                    height = 36;
                }

                Element shape = doc.createElementNS(BPMN_DI_NS, "bpmndi:BPMNShape");
                shape.setAttribute("id", "shape_" + id);
                shape.setAttribute("bpmnElement", id);

                Element bounds = doc.createElementNS(BPMN_DI_NS, "dc:Bounds");
                bounds.setAttribute("x", String.valueOf(x));
                bounds.setAttribute("y", String.valueOf(y));
                bounds.setAttribute("width", String.valueOf(width));
                bounds.setAttribute("height", String.valueOf(height));
                shape.appendChild(bounds);

                plane.appendChild(shape);
            }
        }

        // 为每条边生成 edge
        if (edges != null && edges.isArray()) {
            for (JsonNode edge : edges) {
                String id = getTextValue(edge, "id");
                Element edgeEl = doc.createElementNS(BPMN_DI_NS, "bpmndi:BPMNEdge");
                edgeEl.setAttribute("id", "edge_" + id);
                edgeEl.setAttribute("bpmnElement", id);

                JsonNode waypoints = edge.get("waypoints");
                if (waypoints != null && waypoints.isArray()) {
                    for (JsonNode wp : waypoints) {
                        Element waypoint = doc.createElementNS(BPMN_DI_NS, "di:waypoint");
                        waypoint.setAttribute("x", String.valueOf(wp.get("x").asDouble()));
                        waypoint.setAttribute("y", String.valueOf(wp.get("y").asDouble()));
                        edgeEl.appendChild(waypoint);
                    }
                }

                plane.appendChild(edgeEl);
            }
        }

        definitions.appendChild(diagram);
    }

    // ======================== 辅助方法 ========================

    private String getTextValue(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = XmlSecurityUtils.createSafeTransformerFactory();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
