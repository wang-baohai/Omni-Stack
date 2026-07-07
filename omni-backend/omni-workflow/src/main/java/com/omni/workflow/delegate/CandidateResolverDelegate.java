package com.omni.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选人解析服务任务代理。
 * <p>
 * 在多实例 UserTask 之前的 ServiceTask 中调用，负责：
 * <ol>
 *   <li>从原始 BPMN XML 读取下一个 UserTask 的 {@code omni:assignment} 扩展元素</li>
 *   <li>根据角色编码 + 组织锚点 + 作用域模式解析候选人列表</li>
 *   <li>将 {@code candidateUserIds} 写入流程变量，供多实例 collection 使用</li>
 * </ol>
 * </p>
 * <p>
 * 必须在多实例 UserTask 之前执行，因为 Flowable 在创建子执行时就需要解析 collection 变量。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("candidateResolverDelegate")
@RequiredArgsConstructor
public class CandidateResolverDelegate implements JavaDelegate {

    private static final long serialVersionUID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            // 1. 确定下一个 UserTask 的 activityId（ServiceTask 的 outgoing 连线指向的目标）
            String targetActivityId = resolveTargetActivityId(execution);
            if (targetActivityId == null) {
                log.error("无法解析目标 UserTask: serviceTaskId={}", execution.getCurrentActivityId());
                return;
            }

            // 2. 从原始 BPMN XML 读取 omni:assignment 配置
            String assignmentJson = readAssignmentFromBpmnXml(execution, targetActivityId);
            if (assignmentJson == null || assignmentJson.isBlank()) {
                log.warn("目标 UserTask 未配置 omni:assignment: targetActivityId={}", targetActivityId);
                execution.setVariable("candidateUserIds", List.of("1"));
                initApprovalCounters(execution, "ALL", 1);
                return;
            }

            // 3. 解析配置
            JsonNode config = objectMapper.readTree(assignmentJson);
            String roleCode = getTextValue(config, "roleCode");
            String anchorType = getTextValue(config, "anchorType");
            String scopeMode = getTextValue(config, "scopeMode");
            JsonNode anchorParams = config.get("anchorParams");
            String fallbackStrategy = getTextValue(config, "fallbackStrategy");
            String approvalMode = getTextValue(config, "approvalMode");
            if (approvalMode == null) approvalMode = "ALL";

            if (roleCode == null || roleCode.isBlank()) {
                log.warn("omni:assignment 缺少 roleCode: targetActivityId={}", targetActivityId);
                execution.setVariable("candidateUserIds", List.of("1"));
                initApprovalCounters(execution, "ALL", 1);
                return;
            }

            Long tenantId = resolveTenantId(execution);
            Long startUserId = resolveStartUserId(execution);

            // 4. 解析候选人
            List<Long> candidateUserIds;
            Long anchorUnitId;
            if ("ABSOLUTE_UNIT".equals(anchorType)) {
                anchorUnitId = null;
                List<Long> unitIds = extractUnitIds(anchorParams);
                candidateUserIds = resolveCandidatesForAbsoluteUnits(roleCode, unitIds, tenantId);
            } else {
                anchorUnitId = resolveAnchorUnitId(anchorType, anchorParams, startUserId, tenantId);
                if (anchorUnitId == null) {
                    handleFallback(execution, fallbackStrategy, "无法解析锚点组织单元", tenantId);
                    return;
                }
                if ("UNIT_AND_BELOW".equals(scopeMode)) {
                    candidateUserIds = resolveCandidatesInUnitAndBelow(roleCode, anchorUnitId, tenantId);
                } else if ("CHILDREN_ONLY".equals(scopeMode)) {
                    candidateUserIds = resolveCandidatesInChildrenOnly(roleCode, anchorUnitId, tenantId);
                } else {
                    candidateUserIds = resolveCandidatesInSameUnit(roleCode, anchorUnitId, tenantId);
                }
            }

            if (candidateUserIds.isEmpty()) {
                handleFallback(execution, fallbackStrategy,
                        "未找到候选人: roleCode=" + roleCode + ", unitId=" + anchorUnitId, tenantId);
                return;
            }

            // 5. 写入流程变量（必须在多实例 UserTask 解析 collection 之前完成）
            execution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));
            initApprovalCounters(execution, approvalMode, candidateUserIds.size());

            log.info("候选人解析完成: targetActivityId={}, roleCode={}, unitId={}, candidates={}",
                    targetActivityId, roleCode, anchorUnitId, candidateUserIds);

        } catch (Exception e) {
            log.error("CandidateResolverDelegate 执行异常: serviceTaskId={}",
                    execution.getCurrentActivityId(), e);
        }
    }

    /**
     * 解析 ServiceTask 的 outgoing 连线，找到目标 UserTask 的 activityId。
     */
    private String resolveTargetActivityId(DelegateExecution execution) {
        try {
            java.io.InputStream is = repositoryService.getProcessModel(execution.getProcessDefinitionId());
            if (is == null) return null;
            byte[] modelBytes = is.readAllBytes();
            if (modelBytes == null) return null;

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(modelBytes));
            String serviceTaskId = execution.getCurrentActivityId();

            // 查找以当前 ServiceTask 为 sourceRef 的 sequenceFlow，获取 targetRef
            NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
            for (int i = 0; i < flows.getLength(); i++) {
                Element flow = (Element) flows.item(i);
                if (serviceTaskId.equals(flow.getAttribute("sourceRef"))) {
                    return flow.getAttribute("targetRef");
                }
            }
        } catch (Exception e) {
            log.warn("解析目标 activityId 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从原始 BPMN XML 中读取指定节点的 omni:assignment 扩展元素。
     */
    private String readAssignmentFromBpmnXml(DelegateExecution execution, String activityId) {
        try {
            java.io.InputStream is = repositoryService.getProcessModel(execution.getProcessDefinitionId());
            if (is == null) return null;
            byte[] modelBytes = is.readAllBytes();
            if (modelBytes == null) return null;

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(modelBytes));

            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                if (activityId.equals(el.getAttribute("id"))) {
                    NodeList children = el.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        org.w3c.dom.Node child = children.item(j);
                        if ("extensionElements".equals(child.getLocalName())) {
                            NodeList extChildren = child.getChildNodes();
                            for (int k = 0; k < extChildren.getLength(); k++) {
                                org.w3c.dom.Node extNode = extChildren.item(k);
                                if ("assignment".equals(extNode.getLocalName())
                                        || "omni:assignment".equals(extNode.getNodeName())) {
                                    String text = extNode.getTextContent();
                                    if (text != null && !text.isBlank()) return text.trim();
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("从 BPMN XML 读取扩展元素失败: activityId={}, error={}", activityId, e.getMessage());
        }
        return null;
    }

    /**
     * 初始化审批计数器（仅在变量尚未初始化时设置，防止重复调用重置）。
     *
     * @param execution     执行委托
     * @param approvalMode  "ALL" 全员通过 / "ANY" 任一人通过
     * @param candidateCount 候选人总数
     */
    private void initApprovalCounters(DelegateExecution execution, String approvalMode, int candidateCount) {
        if (execution.getVariable("approvedCount") == null) {
            execution.setVariable("approvedCount", 0);
        }
        if (execution.getVariable("rejectedCount") == null) {
            execution.setVariable("rejectedCount", 0);
        }
        if (execution.getVariable("requiredApprovals") == null) {
            int requiredApprovals = "ANY".equals(approvalMode) ? 1 : candidateCount;
            execution.setVariable("requiredApprovals", requiredApprovals);
        }
    }

    // ======================== 流程变量解析 ========================

    private Long resolveStartUserId(DelegateExecution execution) {
        Object initiatorObj = execution.getVariable("initiator");
        if (initiatorObj != null) {
            try {
                return Long.valueOf(initiatorObj.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long resolveTenantId(DelegateExecution execution) {
        try {
            String tenantId = execution.getTenantId();
            return tenantId != null ? Long.valueOf(tenantId) : 1L;
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    // ======================== Fallback 策略 ========================

    private void handleFallback(DelegateExecution execution, String fallbackStrategy,
                                String reason, Long tenantId) {
        String strategy = fallbackStrategy != null ? fallbackStrategy : "ERROR";
        switch (strategy) {
            case "ASSIGN_ADMIN" -> {
                List<Long> adminIds = resolveCandidatesInSameUnit("SUPER_ADMIN", 1L, tenantId);
                List<String> ids = adminIds.isEmpty()
                        ? List.of("1")
                        : adminIds.stream().map(String::valueOf).collect(Collectors.toList());
                execution.setVariable("candidateUserIds", ids);
                initApprovalCounters(execution, "ALL", ids.size());
                log.warn("Fallback ASSIGN_ADMIN: {}", ids);
            }
            default -> {
                log.error("候选人解析失败且 fallback={}: {}", strategy, reason);
                execution.setVariable("candidateUserIds", List.of("1"));
                initApprovalCounters(execution, "ALL", 1);
            }
        }
    }

    // ======================== 锚点解析 ========================

    private Long resolveAnchorUnitId(String anchorType, JsonNode anchorParams,
                                     Long startUserId, Long tenantId) {
        if (anchorType == null || startUserId == null) {
            return null;
        }

        return switch (anchorType) {
            case "START_USER_PRIMARY_UNIT" -> getPrimaryUnitId(startUserId, tenantId);
            case "PARENT_BY_TYPE" -> {
                Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                if (primaryUnit == null) yield null;
                String targetType = anchorParams != null && anchorParams.has("targetType")
                        ? anchorParams.get("targetType").asText() : null;
                yield findParentByType(primaryUnit, targetType, tenantId);
            }
            case "CHILD_BY_CODE" -> {
                Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                if (primaryUnit == null) yield null;
                String childCode = anchorParams != null && anchorParams.has("childCode")
                        ? anchorParams.get("childCode").asText() : null;
                yield findChildByCode(primaryUnit, childCode, tenantId);
            }
            case "SIBLING_BY_CODE" -> {
                Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                if (primaryUnit == null) yield null;
                String siblingCode = anchorParams != null && anchorParams.has("siblingCode")
                        ? anchorParams.get("siblingCode").asText() : null;
                yield findSiblingByCode(primaryUnit, siblingCode, tenantId);
            }
            case "PARENT_CHILDREN" -> {
                Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                if (primaryUnit == null) yield null;
                String targetType = anchorParams != null && anchorParams.has("targetType")
                        ? anchorParams.get("targetType").asText() : null;
                yield findParentByType(primaryUnit, targetType, tenantId);
            }
            case "DEPT_BY_CODE" -> {
                String deptCode = anchorParams != null && anchorParams.has("deptCode")
                        ? anchorParams.get("deptCode").asText() : null;
                yield findDeptByCode(deptCode, tenantId);
            }
            case "ABSOLUTE_UNIT" -> {
                if (anchorParams != null && anchorParams.has("unitId")) {
                    yield anchorParams.get("unitId").asLong();
                }
                yield null;
            }
            case "PARENT" -> {
                Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                if (primaryUnit == null) yield null;
                yield findParentId(primaryUnit, tenantId);
            }
            case "CHILD_UNIT" -> {
                if (anchorParams != null && anchorParams.has("unitId")) {
                    yield anchorParams.get("unitId").asLong();
                }
                yield null;
            }
            case "SIBLING_UNIT" -> {
                if (anchorParams != null && anchorParams.has("unitId")) {
                    yield anchorParams.get("unitId").asLong();
                }
                yield null;
            }
            default -> null;
        };
    }

    private Long getPrimaryUnitId(Long userId, Long tenantId) {
        try {
            List<Long> results = jdbcTemplate.queryForList("""
                    SELECT primary_unit_id FROM omni_auth.sys_user
                    WHERE id = ? AND tenant_id = ? AND status = 1
                    """, Long.class, userId, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.warn("查询用户主组织失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    private Long findParentId(Long unitId, Long tenantId) {
        try {
            List<Long> results = jdbcTemplate.queryForList("""
                    SELECT parent_id FROM omni_auth.sys_org_unit
                    WHERE id = ? AND tenant_id = ?
                    """, Long.class, unitId, tenantId);
            return results.isEmpty() || results.get(0) == null ? null : results.get(0);
        } catch (Exception e) {
            log.warn("查询父组织失败: unitId={}, error={}", unitId, e.getMessage());
            return null;
        }
    }

    private Long findParentByType(Long unitId, String targetType, Long tenantId) {
        if (targetType == null) return unitId;
        try {
            Long currentId = unitId;
            int maxDepth = 20;
            while (currentId != null && maxDepth-- > 0) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT parent_id, type FROM omni_auth.sys_org_unit
                        WHERE id = ? AND tenant_id = ?
                        """, currentId, tenantId);
                if (rows.isEmpty()) return null;
                Map<String, Object> row = rows.get(0);
                String type = (String) row.get("type");
                if (targetType.equals(type)) return currentId;
                Object parentId = row.get("parent_id");
                currentId = parentId != null ? Long.valueOf(parentId.toString()) : null;
            }
            return null;
        } catch (Exception e) {
            log.warn("查找父组织失败: {}", e.getMessage());
            return null;
        }
    }

    private Long findChildByCode(Long parentUnitId, String childCode, Long tenantId) {
        if (childCode == null) return null;
        try {
            List<Long> results = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE parent_id = ? AND unit_code = ? AND tenant_id = ? AND status = 1
                    """, Long.class, parentUnitId, childCode, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.warn("查找子组织失败: {}", e.getMessage());
            return null;
        }
    }

    private Long findSiblingByCode(Long unitId, String siblingCode, Long tenantId) {
        if (siblingCode == null) return null;
        try {
            List<Long> parentIds = jdbcTemplate.queryForList("""
                    SELECT parent_id FROM omni_auth.sys_org_unit
                    WHERE id = ? AND tenant_id = ?
                    """, Long.class, unitId, tenantId);
            if (parentIds.isEmpty() || parentIds.get(0) == null) return null;
            Long parentId = parentIds.get(0);
            List<Long> results = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE parent_id = ? AND unit_code = ? AND tenant_id = ? AND status = 1
                    """, Long.class, parentId, siblingCode, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.warn("查找兄弟组织失败: {}", e.getMessage());
            return null;
        }
    }

    private Long findDeptByCode(String deptCode, Long tenantId) {
        if (deptCode == null) return null;
        try {
            List<Long> results = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE unit_code = ? AND tenant_id = ? AND status = 1
                    LIMIT 1
                    """, Long.class, deptCode, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.warn("按编码查找部门失败: deptCode={}, error={}", deptCode, e.getMessage());
            return null;
        }
    }

    // ======================== ABSOLUTE_UNIT 多选专用 ========================

    private List<Long> extractUnitIds(JsonNode anchorParams) {
        if (anchorParams == null) return Collections.emptyList();
        if (anchorParams.has("unitIds") && anchorParams.get("unitIds").isArray()
                && !anchorParams.get("unitIds").isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (JsonNode node : anchorParams.get("unitIds")) {
                ids.add(node.asLong());
            }
            return ids;
        }
        if (anchorParams.has("unitId") && !anchorParams.get("unitId").isNull()) {
            return List.of(anchorParams.get("unitId").asLong());
        }
        return Collections.emptyList();
    }

    private List<Long> resolveCandidatesForAbsoluteUnits(String roleCode, List<Long> unitIds, Long tenantId) {
        if (unitIds == null || unitIds.isEmpty()) {
            log.warn("ABSOLUTE_UNIT: 未配置组织单元");
            return Collections.emptyList();
        }
        try {
            String placeholders = unitIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT urs.user_id
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1 AND u.status = 1
                    """.formatted(placeholders);
            Object[] params = new Object[2 + unitIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < unitIds.size(); i++) {
                params[2 + i] = unitIds.get(i);
            }
            return jdbcTemplate.queryForList(sql, Long.class, params);
        } catch (Exception e) {
            log.warn("ABSOLUTE_UNIT 候选人解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== 候选人查询 ========================

    private List<Long> resolveCandidatesInSameUnit(String roleCode, Long unitId, Long tenantId) {
        try {
            return jdbcTemplate.queryForList("""
                    SELECT DISTINCT urs.user_id
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id = ? AND urs.status = 1 AND u.status = 1
                    """, Long.class, roleCode, tenantId, unitId);
        } catch (Exception e) {
            log.warn("解析同单元候选人失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Long> resolveCandidatesInUnitAndBelow(String roleCode, Long unitId, Long tenantId) {
        try {
            List<String> paths = jdbcTemplate.queryForList("""
                    SELECT path FROM omni_auth.sys_org_unit WHERE id = ? AND tenant_id = ?
                    """, String.class, unitId, tenantId);
            if (paths.isEmpty() || paths.get(0) == null) return Collections.emptyList();
            String path = paths.get(0);
            List<Long> descendantIds = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE tenant_id = ? AND (id = ? OR path LIKE ?)
                    """, Long.class, tenantId, unitId, path + "%");
            if (descendantIds.isEmpty()) return Collections.emptyList();
            String placeholders = descendantIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT urs.user_id
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1 AND u.status = 1
                    """.formatted(placeholders);
            Object[] params = new Object[2 + descendantIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < descendantIds.size(); i++) {
                params[2 + i] = descendantIds.get(i);
            }
            return jdbcTemplate.queryForList(sql, Long.class, params);
        } catch (Exception e) {
            log.warn("解析单元及下属候选人失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Long> resolveCandidatesInChildrenOnly(String roleCode, Long unitId, Long tenantId) {
        try {
            List<Long> childIds = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE parent_id = ? AND tenant_id = ? AND status = 1
                    """, Long.class, unitId, tenantId);
            if (childIds.isEmpty()) return Collections.emptyList();
            String placeholders = childIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT urs.user_id
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1 AND u.status = 1
                    """.formatted(placeholders);
            Object[] params = new Object[2 + childIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < childIds.size(); i++) {
                params[2 + i] = childIds.get(i);
            }
            return jdbcTemplate.queryForList(sql, Long.class, params);
        } catch (Exception e) {
            log.warn("解析直属子单元候选人失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
