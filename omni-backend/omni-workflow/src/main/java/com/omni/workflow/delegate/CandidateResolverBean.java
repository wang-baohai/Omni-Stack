package com.omni.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
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
 * 候选人解析 Bean，供 BPMN 中 {@code flowable:collection} UEL 表达式调用。
 * <p>
 * 用法：{@code flowable:collection="${candidateResolver.resolve(execution)}"}
 * </p>
 * <p>
 * 在 Flowable 解析多实例集合时调用，从原始 BPMN XML 读取当前节点的 {@code omni:assignment} 配置，
 * 根据角色编码 + 组织锚点 + 作用域模式解析候选人列表。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("candidateResolver")
@RequiredArgsConstructor
public class CandidateResolverBean {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;

    /**
     * 解析当前活动的候选人列表。
     * <p>由 Flowable UEL 表达式在多实例集合解析时调用。</p>
     *
     * @param execution 当前执行实例
     * @return 候选人用户 ID 字符串列表
     */
    public List<String> resolve(DelegateExecution execution) {
        try {
            String activityId = execution.getCurrentActivityId();
            Long tenantId = resolveTenantId(execution);
            Long startUserId = resolveStartUserId(execution);

            // 委托给公共方法
            List<Long> candidateUserIds = resolveCandidates(
                    execution.getProcessDefinitionId(), activityId, startUserId, tenantId);

            if (candidateUserIds.isEmpty()) {
                // 读取 omni:assignment 配置（含 fallbackStrategy 和 approvalMode）
                String assignmentJson = readAssignmentFromBpmnXml(execution, activityId);
                String fallbackStrategy = "ERROR";
                String approvalMode = "ALL";
                if (assignmentJson != null && !assignmentJson.isBlank()) {
                    JsonNode config = objectMapper.readTree(assignmentJson);
                    fallbackStrategy = getTextValue(config, "fallbackStrategy");
                    String mode = getTextValue(config, "approvalMode");
                    if (mode != null) approvalMode = mode;
                }
                List<String> result = handleFallback(fallbackStrategy, "未找到候选人", tenantId);
                applyApprovalVariables(execution, approvalMode, result.size());
                return result;
            }

            // 读取 approvalMode 并设置流程变量
            String approvalMode = resolveApprovalMode(execution, activityId);
            applyApprovalVariables(execution, approvalMode, candidateUserIds.size());

            log.info("候选人解析完成: activityId={}, approvalMode={}, candidates={}",
                    activityId, approvalMode, candidateUserIds);
            return candidateUserIds.stream().map(String::valueOf).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CandidateResolverBean 执行异常: activityId={}",
                    execution.getCurrentActivityId(), e);
            applyApprovalVariables(execution, "ALL", 1);
            return List.of("1");
        }
    }

    /**
     * 根据流程定义和活动 ID 预解析候选人（不依赖运行时的 DelegateExecution）。
     * <p>供 getProgress() 等离线场景使用。</p>
     *
     * @param processDefinitionId 流程定义 ID
     * @param activityId          BPMN 活动元素 ID
     * @param startUserId         流程发起人用户 ID
     * @param tenantId            租户 ID
     * @return 候选人用户 ID 列表，解析失败返回空列表
     */
    public List<Long> resolveCandidates(String processDefinitionId, String activityId,
                                        Long startUserId, Long tenantId) {
        try {
            String assignmentJson = readAssignmentFromBpmnXmlById(processDefinitionId, activityId);
            if (assignmentJson == null || assignmentJson.isBlank()) {
                log.debug("未找到 omni:assignment 配置: activityId={}", activityId);
                return Collections.emptyList();
            }

            JsonNode config = objectMapper.readTree(assignmentJson);
            String roleCode = getTextValue(config, "roleCode");
            String anchorType = getTextValue(config, "anchorType");
            String scopeMode = getTextValue(config, "scopeMode");
            JsonNode anchorParams = config.get("anchorParams");

            if (roleCode == null || roleCode.isBlank()) {
                log.debug("omni:assignment 缺少 roleCode: activityId={}", activityId);
                return Collections.emptyList();
            }

            if ("ABSOLUTE_UNIT".equals(anchorType)) {
                List<Long> unitIds = extractUnitIds(anchorParams);
                List<Long> candidates = resolveCandidatesForAbsoluteUnits(roleCode, unitIds, tenantId);
                if (candidates.isEmpty()) {
                    log.warn("ABSOLUTE_UNIT 预解析候选人为空: activityId={}, roleCode={}, unitIds={}, tenantId={}",
                            activityId, roleCode, unitIds, tenantId);
                }
                return candidates;
            }

            Long anchorUnitId = resolveAnchorUnitId(anchorType, anchorParams, startUserId, tenantId);
            if (anchorUnitId == null) {
                log.debug("无法解析锚点组织单元: activityId={}, anchorType={}", activityId, anchorType);
                return Collections.emptyList();
            }

            if ("UNIT_AND_BELOW".equals(scopeMode)) {
                return resolveCandidatesInUnitAndBelow(roleCode, anchorUnitId, tenantId);
            } else if ("CHILDREN_ONLY".equals(scopeMode)) {
                return resolveCandidatesInChildrenOnly(roleCode, anchorUnitId, tenantId);
            } else {
                return resolveCandidatesInSameUnit(roleCode, anchorUnitId, tenantId);
            }
        } catch (Exception e) {
            log.warn("预解析候选人失败: activityId={}, error={}", activityId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 BPMN XML 中提取所有 userTask 节点的 ID 和名称。
     * <p>供 getProgress() 枚举所有可能的审批节点。</p>
     *
     * @param processDefinitionId 流程定义 ID
     * @return activityId → activityName 映射
     */
    public Map<String, String> extractUserTaskNodes(String processDefinitionId) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            java.io.InputStream is = repositoryService.getProcessModel(processDefinitionId);
            if (is == null) return result;
            byte[] modelBytes = is.readAllBytes();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(modelBytes));

            NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                Element el = (Element) userTasks.item(i);
                String id = el.getAttribute("id");
                String name = el.getAttribute("name");
                if (id != null && !id.isBlank()) {
                    result.put(id, (name != null && !name.isBlank()) ? name : id);
                }
            }
        } catch (Exception e) {
            log.warn("提取 userTask 节点失败: {}", e.getMessage());
        }
        return result;
    }

    // ======================== BPMN XML 读取 ========================

    private String readAssignmentFromBpmnXml(DelegateExecution execution, String activityId) {
        return readAssignmentFromBpmnXmlById(execution.getProcessDefinitionId(), activityId);
    }

    /**
     * 根据流程定义 ID 从 BPMN XML 读取指定活动的 omni:assignment 配置。
     */
    private String readAssignmentFromBpmnXmlById(String processDefinitionId, String activityId) {
        try {
            java.io.InputStream is = repositoryService.getProcessModel(processDefinitionId);
            if (is == null) return null;
            byte[] modelBytes = is.readAllBytes();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
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
     * 从 BPMN XML 读取当前节点的 approvalMode 配置。
     *
     * @return "ALL"（全员通过）或 "ANY"（任一人通过），默认 "ALL"
     */
    private String resolveApprovalMode(DelegateExecution execution, String activityId) {
        String assignmentJson = readAssignmentFromBpmnXml(execution, activityId);
        if (assignmentJson != null && !assignmentJson.isBlank()) {
            try {
                JsonNode config = objectMapper.readTree(assignmentJson);
                String mode = getTextValue(config, "approvalMode");
                if (mode != null) return mode;
            } catch (Exception e) {
                log.warn("解析 approvalMode 失败: activityId={}", activityId);
            }
        }
        return "ALL";
    }

    /**
     * 根据审批模式设置 requiredApprovals、approvedCount、rejectedCount 流程变量。
     * <p>
     * 注意：Flowable 可能对同一个多实例节点多次调用 resolve()（如任务创建和待办同步），
     * 因此仅在变量不存在时才初始化，避免重置已有的审批计数。
     * </p>
     *
     * @param approvalMode "ALL" 全员通过 / "ANY" 任一人通过
     * @param candidateCount 候选人总数
     */
    private void applyApprovalVariables(DelegateExecution execution, String approvalMode, int candidateCount) {
        // 仅在变量尚未初始化时设置，防止重复调用 resolve() 重置计数器
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

    // ======================== 变量解析 ========================

    private Long resolveStartUserId(DelegateExecution execution) {
        Object initiatorObj = execution.getVariable("initiator");
        if (initiatorObj != null) {
            try { return Long.valueOf(initiatorObj.toString()); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Long resolveTenantId(DelegateExecution execution) {
        try {
            String tenantId = execution.getTenantId();
            return tenantId != null ? Long.valueOf(tenantId) : 1L;
        } catch (NumberFormatException e) { return 1L; }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) return null;
        return node.get(fieldName).asText();
    }

    private List<String> handleFallback(String fallbackStrategy, String reason, Long tenantId) {
        String strategy = fallbackStrategy != null ? fallbackStrategy : "ERROR";
        if ("ASSIGN_ADMIN".equals(strategy)) {
            List<Long> adminIds = resolveCandidatesInSameUnit("SUPER_ADMIN", 1L, tenantId);
            List<String> ids = adminIds.isEmpty()
                    ? List.of("1")
                    : adminIds.stream().map(String::valueOf).collect(Collectors.toList());
            log.warn("Fallback ASSIGN_ADMIN: {}", ids);
            return ids;
        }
        log.error("候选人解析失败且 fallback={}: {}", strategy, reason);
        return List.of("1");
    }

    // ======================== 锚点解析 ========================

    private Long resolveAnchorUnitId(String anchorType, JsonNode anchorParams,
                                     Long startUserId, Long tenantId) {
        if (anchorType == null || startUserId == null) return null;
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
            case "PARENT" -> {
                Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                if (primaryUnit == null) yield null;
                yield findParentId(primaryUnit, tenantId);
            }
            case "DEPT_BY_CODE" -> {
                String deptCode = anchorParams != null && anchorParams.has("deptCode")
                        ? anchorParams.get("deptCode").asText() : null;
                yield findDeptByCode(deptCode, tenantId);
            }
            default -> null;
        };
    }

    // ======================== 组织查询 ========================

    private Long getPrimaryUnitId(Long userId, Long tenantId) {
        try {
            List<Long> results = jdbcTemplate.queryForList(
                    "SELECT primary_unit_id FROM omni_auth.sys_user WHERE id = ? AND tenant_id = ? AND status = 1",
                    Long.class, userId, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) { log.warn("查询用户主组织失败: userId={}", userId); return null; }
    }

    private Long findParentId(Long unitId, Long tenantId) {
        try {
            List<Long> results = jdbcTemplate.queryForList(
                    "SELECT parent_id FROM omni_auth.sys_org_unit WHERE id = ? AND tenant_id = ?",
                    Long.class, unitId, tenantId);
            return results.isEmpty() || results.get(0) == null ? null : results.get(0);
        } catch (Exception e) { log.warn("查询父组织失败: unitId={}", unitId); return null; }
    }

    private Long findParentByType(Long unitId, String targetType, Long tenantId) {
        if (targetType == null) return unitId;
        try {
            Long currentId = unitId;
            int maxDepth = 20;
            while (currentId != null && maxDepth-- > 0) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT parent_id, type FROM omni_auth.sys_org_unit WHERE id = ? AND tenant_id = ?",
                        currentId, tenantId);
                if (rows.isEmpty()) return null;
                String type = (String) rows.get(0).get("type");
                if (targetType.equals(type)) return currentId;
                Object parentId = rows.get(0).get("parent_id");
                currentId = parentId != null ? Long.valueOf(parentId.toString()) : null;
            }
            return null;
        } catch (Exception e) { log.warn("查找父组织失败: {}", e.getMessage()); return null; }
    }

    private Long findChildByCode(Long parentUnitId, String childCode, Long tenantId) {
        if (childCode == null) return null;
        try {
            List<Long> results = jdbcTemplate.queryForList(
                    "SELECT id FROM omni_auth.sys_org_unit WHERE parent_id = ? AND unit_code = ? AND tenant_id = ? AND status = 1",
                    Long.class, parentUnitId, childCode, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) { log.warn("查找子组织失败: {}", e.getMessage()); return null; }
    }

    private Long findDeptByCode(String deptCode, Long tenantId) {
        if (deptCode == null) return null;
        try {
            List<Long> results = jdbcTemplate.queryForList(
                    "SELECT id FROM omni_auth.sys_org_unit WHERE unit_code = ? AND tenant_id = ? AND status = 1 LIMIT 1",
                    Long.class, deptCode, tenantId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) { log.warn("按编码查找部门失败: deptCode={}", deptCode); return null; }
    }

    // ======================== 候选人查询 ========================

    private List<Long> extractUnitIds(JsonNode anchorParams) {
        if (anchorParams == null) return Collections.emptyList();
        if (anchorParams.has("unitIds") && anchorParams.get("unitIds").isArray()
                && !anchorParams.get("unitIds").isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (JsonNode node : anchorParams.get("unitIds")) ids.add(node.asLong());
            return ids;
        }
        if (anchorParams.has("unitId") && !anchorParams.get("unitId").isNull()) {
            return List.of(anchorParams.get("unitId").asLong());
        }
        return Collections.emptyList();
    }

    private List<Long> resolveCandidatesForAbsoluteUnits(String roleCode, List<Long> unitIds, Long tenantId) {
        if (unitIds == null || unitIds.isEmpty()) return Collections.emptyList();
        try {
            String placeholders = unitIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT DISTINCT urs.user_id FROM omni_auth.sys_user_role_scope urs "
                    + "JOIN omni_auth.sys_role r ON urs.role_id = r.id "
                    + "JOIN omni_auth.sys_user u ON urs.user_id = u.id "
                    + "WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1 "
                    + "AND urs.unit_id IN (" + placeholders + ") AND urs.status = 1 AND u.status = 1";
            Object[] params = new Object[2 + unitIds.size()];
            params[0] = roleCode; params[1] = tenantId;
            for (int i = 0; i < unitIds.size(); i++) params[2 + i] = unitIds.get(i);
            List<Long> result = jdbcTemplate.queryForList(sql, Long.class, params);
            if (result.isEmpty()) {
                log.warn("ABSOLUTE_UNIT 候选人查询结果为空: roleCode={}, unitIds={}, tenantId={}", roleCode, unitIds, tenantId);
            }
            return result;
        } catch (Exception e) { log.warn("ABSOLUTE_UNIT 候选人解析失败: {}", e.getMessage()); return Collections.emptyList(); }
    }

    private List<Long> resolveCandidatesInSameUnit(String roleCode, Long unitId, Long tenantId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT urs.user_id FROM omni_auth.sys_user_role_scope urs "
                    + "JOIN omni_auth.sys_role r ON urs.role_id = r.id "
                    + "JOIN omni_auth.sys_user u ON urs.user_id = u.id "
                    + "WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1 "
                    + "AND urs.unit_id = ? AND urs.status = 1 AND u.status = 1",
                    Long.class, roleCode, tenantId, unitId);
        } catch (Exception e) { log.warn("解析同单元候选人失败: {}", e.getMessage()); return Collections.emptyList(); }
    }

    private List<Long> resolveCandidatesInUnitAndBelow(String roleCode, Long unitId, Long tenantId) {
        try {
            List<String> paths = jdbcTemplate.queryForList(
                    "SELECT path FROM omni_auth.sys_org_unit WHERE id = ? AND tenant_id = ?",
                    String.class, unitId, tenantId);
            if (paths.isEmpty() || paths.get(0) == null) return Collections.emptyList();
            String path = paths.get(0);
            List<Long> descendantIds = jdbcTemplate.queryForList(
                    "SELECT id FROM omni_auth.sys_org_unit WHERE tenant_id = ? AND (id = ? OR path LIKE ?)",
                    Long.class, tenantId, unitId, path + "%");
            if (descendantIds.isEmpty()) return Collections.emptyList();
            String placeholders = descendantIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT DISTINCT urs.user_id FROM omni_auth.sys_user_role_scope urs "
                    + "JOIN omni_auth.sys_role r ON urs.role_id = r.id "
                    + "JOIN omni_auth.sys_user u ON urs.user_id = u.id "
                    + "WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1 "
                    + "AND urs.unit_id IN (" + placeholders + ") AND urs.status = 1 AND u.status = 1";
            Object[] params = new Object[2 + descendantIds.size()];
            params[0] = roleCode; params[1] = tenantId;
            for (int i = 0; i < descendantIds.size(); i++) params[2 + i] = descendantIds.get(i);
            return jdbcTemplate.queryForList(sql, Long.class, params);
        } catch (Exception e) { log.warn("解析单元及下属候选人失败: {}", e.getMessage()); return Collections.emptyList(); }
    }

    private List<Long> resolveCandidatesInChildrenOnly(String roleCode, Long unitId, Long tenantId) {
        try {
            List<Long> childIds = jdbcTemplate.queryForList(
                    "SELECT id FROM omni_auth.sys_org_unit WHERE parent_id = ? AND tenant_id = ? AND status = 1",
                    Long.class, unitId, tenantId);
            if (childIds.isEmpty()) return Collections.emptyList();
            String placeholders = childIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT DISTINCT urs.user_id FROM omni_auth.sys_user_role_scope urs "
                    + "JOIN omni_auth.sys_role r ON urs.role_id = r.id "
                    + "JOIN omni_auth.sys_user u ON urs.user_id = u.id "
                    + "WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1 "
                    + "AND urs.unit_id IN (" + placeholders + ") AND urs.status = 1 AND u.status = 1";
            Object[] params = new Object[2 + childIds.size()];
            params[0] = roleCode; params[1] = tenantId;
            for (int i = 0; i < childIds.size(); i++) params[2 + i] = childIds.get(i);
            return jdbcTemplate.queryForList(sql, Long.class, params);
        } catch (Exception e) { log.warn("解析直属子单元候选人失败: {}", e.getMessage()); return Collections.emptyList(); }
    }
}
