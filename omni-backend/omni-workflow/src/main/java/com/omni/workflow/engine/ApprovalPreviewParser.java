package com.omni.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.dto.internal.InternalApprovalPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 BPMN XML 转换为不泄露原始表达式的安全审批图。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class ApprovalPreviewParser {

    private static final Set<String> SUPPORTED_NODES =
            Set.of("startEvent", "endEvent", "userTask", "exclusiveGateway",
                    "parallelGateway", "inclusiveGateway", "serviceTask");

    private final ObjectMapper objectMapper;

    /**
     * 安全解析 BPMN XML。
     *
     * @param bpmnXml BPMN XML
     * @return 安全审批图数据
     */
    public PreviewGraph parse(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML 不能为空");
        }
        try {
            Document document = XmlSecurityUtils.createSafeDocumentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Map<String, Element> nodeElements = readNodeElements(document);
            List<InternalApprovalPreviewResponse.Node> nodes = nodeElements.values().stream()
                    .map(this::toNode)
                    .toList();
            Set<String> defaultFlows = readDefaultFlows(nodeElements.values());
            List<InternalApprovalPreviewResponse.Edge> edges = readEdges(document, nodeElements, defaultFlows);
            boolean hasBranches = hasBranches(nodes, edges);
            List<String> linearSummary = hasBranches ? null : linearSummary(nodes, edges);
            return new PreviewGraph(nodes, edges, hasBranches, linearSummary);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("BPMN XML 安全预览解析失败", exception);
        }
    }

    private Map<String, Element> readNodeElements(Document document) {
        Map<String, Element> elements = new LinkedHashMap<>();
        NodeList all = document.getElementsByTagName("*");
        for (int index = 0; index < all.getLength(); index++) {
            Element element = (Element) all.item(index);
            String localName = localName(element);
            String id = element.getAttribute("id");
            if (SUPPORTED_NODES.contains(localName) && !id.isBlank()) {
                elements.put(id, element);
            }
        }
        return elements;
    }

    private Set<String> readDefaultFlows(Iterable<Element> elements) {
        Set<String> defaults = new HashSet<>();
        for (Element element : elements) {
            String defaultFlow = element.getAttribute("default");
            if (!defaultFlow.isBlank()) {
                defaults.add(defaultFlow);
            }
        }
        return defaults;
    }

    private List<InternalApprovalPreviewResponse.Edge> readEdges(
            Document document,
            Map<String, Element> nodes,
            Set<String> defaultFlows) {
        List<InternalApprovalPreviewResponse.Edge> edges = new ArrayList<>();
        NodeList all = document.getElementsByTagName("*");
        for (int index = 0; index < all.getLength(); index++) {
            Element element = (Element) all.item(index);
            if (!"sequenceFlow".equals(localName(element))) {
                continue;
            }
            String source = element.getAttribute("sourceRef");
            String target = element.getAttribute("targetRef");
            if (!nodes.containsKey(source) || !nodes.containsKey(target)) {
                continue;
            }
            String id = element.getAttribute("id");
            boolean conditional = hasDescendant(element, "conditionExpression");
            edges.add(InternalApprovalPreviewResponse.Edge.builder()
                    .id(id)
                    .name(blankToNull(element.getAttribute("name")))
                    .source(source)
                    .target(target)
                    .defaultBranch(defaultFlows.contains(id))
                    .conditionSummary(conditional ? "已配置条件（内容已隐藏）" : null)
                    .build());
        }
        return List.copyOf(edges);
    }

    private InternalApprovalPreviewResponse.Node toNode(Element element) {
        String localName = localName(element);
        String name = blankToNull(element.getAttribute("name"));
        String roleCode = null;
        String approvalMode = null;
        String description;
        if ("userTask".equals(localName)) {
            JsonNode assignment = readAssignment(element);
            roleCode = text(assignment, "roleCode");
            approvalMode = text(assignment, "approvalMode");
            if (approvalMode == null) {
                approvalMode = "ALL";
            }
            description = roleCode == null
                    ? "按流程配置审批"
                    : "由角色 " + roleCode + " 按 " + approvalMode + " 模式审批";
        } else if ("serviceTask".equals(localName)) {
            description = "系统自动处理步骤";
        } else if (localName.endsWith("Gateway")) {
            description = "根据请购数据选择后续路径";
        } else if ("startEvent".equals(localName)) {
            description = "审批开始";
        } else {
            description = "审批结束";
        }
        return InternalApprovalPreviewResponse.Node.builder()
                .id(element.getAttribute("id"))
                .name(name == null ? defaultNodeName(localName) : name)
                .type(nodeType(localName))
                .roleCode(roleCode)
                .approvalMode(approvalMode)
                .description(description)
                .build();
    }

    private JsonNode readAssignment(Element element) {
        NodeList descendants = element.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            Element child = (Element) descendants.item(index);
            if ("assignment".equals(localName(child))) {
                String json = child.getTextContent();
                if (json != null && !json.isBlank()) {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception exception) {
                        throw new IllegalArgumentException("审批节点 assignment 配置无法解析", exception);
                    }
                }
            }
        }
        return null;
    }

    private boolean hasBranches(List<InternalApprovalPreviewResponse.Node> nodes,
                                List<InternalApprovalPreviewResponse.Edge> edges) {
        Map<String, Integer> outgoing = new HashMap<>();
        edges.forEach(edge -> outgoing.merge(edge.getSource(), 1, Integer::sum));
        return nodes.stream().anyMatch(node -> outgoing.getOrDefault(node.getId(), 0) > 1);
    }

    private List<String> linearSummary(List<InternalApprovalPreviewResponse.Node> nodes,
                                       List<InternalApprovalPreviewResponse.Edge> edges) {
        if (nodes.isEmpty() || edges.size() != nodes.size() - 1) {
            return null;
        }
        Map<String, InternalApprovalPreviewResponse.Node> byId = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        nodes.forEach(node -> byId.put(node.getId(), node));
        edges.forEach(edge -> {
            outgoing.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>()).add(edge.getTarget());
            incoming.merge(edge.getTarget(), 1, Integer::sum);
        });
        List<InternalApprovalPreviewResponse.Node> starts = nodes.stream()
                .filter(node -> incoming.getOrDefault(node.getId(), 0) == 0)
                .toList();
        if (starts.size() != 1) {
            return null;
        }
        List<String> summary = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = starts.getFirst().getId();
        while (current != null && visited.add(current)) {
            InternalApprovalPreviewResponse.Node node = byId.get(current);
            if (node != null && ("APPROVAL".equals(node.getType()) || "SERVICE".equals(node.getType()))) {
                summary.add(node.getName());
            }
            List<String> next = outgoing.getOrDefault(current, List.of());
            if (next.size() > 1) {
                return null;
            }
            current = next.isEmpty() ? null : next.getFirst();
        }
        return visited.size() == nodes.size() ? List.copyOf(summary) : null;
    }

    private boolean hasDescendant(Element element, String expectedLocalName) {
        NodeList descendants = element.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            if (expectedLocalName.equals(localName((Element) descendants.item(index)))) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.get(fieldName) == null || node.get(fieldName).isNull()) {
            return null;
        }
        return blankToNull(node.get(fieldName).asText());
    }

    private String nodeType(String localName) {
        return switch (localName) {
            case "startEvent" -> "START";
            case "endEvent" -> "END";
            case "userTask" -> "APPROVAL";
            case "serviceTask" -> "SERVICE";
            default -> "GATEWAY";
        };
    }

    private String defaultNodeName(String localName) {
        return switch (localName) {
            case "startEvent" -> "开始";
            case "endEvent" -> "结束";
            case "userTask" -> "审批节点";
            case "serviceTask" -> "系统处理";
            default -> "条件判断";
        };
    }

    private String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 安全解析后的审批图值对象。
     *
     * @param nodes 节点
     * @param edges 有向边
     * @param hasBranches 是否包含分支
     * @param linearSummary 单路径摘要
     */
    public record PreviewGraph(
            List<InternalApprovalPreviewResponse.Node> nodes,
            List<InternalApprovalPreviewResponse.Edge> edges,
            boolean hasBranches,
            List<String> linearSummary) {
    }
}
