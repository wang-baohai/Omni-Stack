package com.omni.workflow.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于"角色 + 组织作用域"的动态审批候选人解析监听器。
 * <p>
 * 作为 Flowable {@link TaskListener}，在 UserTask 的 {@code create} 事件触发时：
 * <ol>
 *   <li>从 BPMN 扩展元素 {@code omni:assignment} 读取 JSON 配置</li>
 *   <li>根据锚点类型解析目标组织单元（START_USER_PRIMARY_UNIT / PARENT / CHILD_UNIT / ABSOLUTE_UNIT）</li>
 *   <li>在目标组织范围内查询拥有指定角色的候选人</li>
 *   <li>将候选人设置到 Flowable Task 的 candidateUsers</li>
 *   <li>如无候选人，按 fallback 策略处理（ERROR / ASSIGN_ADMIN / ESCALATE_PARENT）</li>
 * </ol>
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("scopedRoleAssignmentListener")
@RequiredArgsConstructor
public class ScopedRoleAssignmentListener implements TaskListener, ExecutionListener {

    private static final long serialVersionUID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;

    /**
     * ExecutionListener 入口：在 UserTask 执行启动时触发（多实例解析之前）。
     * <p>负责解析候选人并写入流程变量 {@code candidateUserIds}，供 multiInstance collection 使用。</p>
     *
     * @param delegateExecution 执行委托
     */
    @Override
    public void notify(DelegateExecution delegateExecution) {
        try {
            // 1. 从 BPMN 模型读取当前节点的 omni:assignment 配置
            String assignmentJson = readExtensionElementFromExecution(delegateExecution, "omni:assignment");
            if (assignmentJson == null || assignmentJson.isBlank()) {
                log.debug("UserTask 未配置 omni:assignment，跳过动态解析: activityId={}", delegateExecution.getCurrentActivityId());
                return;
            }

            JsonNode config = objectMapper.readTree(assignmentJson);
            String roleCode = getTextValue(config, "roleCode");
            String anchorType = getTextValue(config, "anchorType");
            String scopeMode = getTextValue(config, "scopeMode");
            JsonNode anchorParams = config.get("anchorParams");
            String fallbackStrategy = getTextValue(config, "fallbackStrategy");

            if (roleCode == null || roleCode.isBlank()) {
                log.warn("omni:assignment 缺少 roleCode: activityId={}", delegateExecution.getCurrentActivityId());
                return;
            }

            Long tenantId = resolveTenantId(delegateExecution);
            Long startUserId = resolveStartUserId(delegateExecution);

            // 2. 解析候选人
            List<Long> candidateUserIds;
            Long anchorUnitId;
            if ("ABSOLUTE_UNIT".equals(anchorType)) {
                anchorUnitId = null;
                List<Long> unitIds = extractUnitIds(anchorParams);
                candidateUserIds = resolveCandidatesForAbsoluteUnits(roleCode, unitIds, tenantId);
            } else {
                anchorUnitId = resolveAnchorUnitId(anchorType, anchorParams, startUserId, tenantId);
                if (anchorUnitId == null) {
                    handleFallbackExecution(delegateExecution, fallbackStrategy, "无法解析锚点组织单元", tenantId);
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
                handleFallbackExecution(delegateExecution, fallbackStrategy,
                        "未找到候选人: roleCode=" + roleCode + ", unitId=" + anchorUnitId, tenantId);
                return;
            }

            // 3. 将候选人列表写入流程变量，供多实例 collection 使用（必须在多实例解析之前完成）
            delegateExecution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(id -> String.valueOf(id))
                            .collect(Collectors.toList()));

            // 初始化审批计数器（含 approvalMode 支持）
            String approvalMode = getTextValue(config, "approvalMode");
            initApprovalCounters(delegateExecution, approvalMode, candidateUserIds.size());

            log.info("ExecutionListener 候选人解析完成: activityId={}, roleCode={}, unitId={}, candidates={}",
                    delegateExecution.getCurrentActivityId(), roleCode, anchorUnitId, candidateUserIds);

        } catch (Exception e) {
            log.error("ScopedRoleAssignmentListener(ExecutionListener) 执行异常: activityId={}",
                    delegateExecution.getCurrentActivityId(), e);
        }
    }

    /**
     * TaskListener 入口（保留向后兼容，当前 BPMN 已改用 ExecutionListener）。
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        // 当前 BPMN 已改用 ExecutionListener(event=start)，此方法保留用于兼容旧版本
        log.debug("TaskListener 已废弃，请使用 ExecutionListener: taskId={}", delegateTask.getId());
    }

    /**
     * 从流程定义的原始 BPMN XML 中读取指定节点的扩展元素。
     * <p>
     * Flowable 的 {@code BpmnXMLConverter} 在解析时会丢弃无法识别的自定义命名空间元素
     * （如 {@code omni:assignment}），因此必须直接从原始 XML 中解析。
     * </p>
     *
     * @param execution   执行委托
     * @param elementName 扩展元素本地名称（如 "assignment"）
     * @return 元素文本内容，不存在则返回 null
     */
    private String readExtensionElementFromExecution(DelegateExecution execution, String elementName) {
        try {
            // 从 Flowable 获取原始 BPMN XML 字节流
            java.io.InputStream is = repositoryService.getProcessModel(execution.getProcessDefinitionId());
            if (is == null) return null;
            byte[] modelBytes = is.readAllBytes();
            if (modelBytes == null) return null;

            String activityId = execution.getCurrentActivityId();

            // 使用标准 DOM 解析原始 XML
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder docBuilder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = docBuilder.parse(new java.io.ByteArrayInputStream(modelBytes));

            // 查找目标节点（按 id 属性匹配）
            org.w3c.dom.NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) allElements.item(i);
                if (activityId.equals(el.getAttribute("id"))) {
                    // 找到目标节点，查找 extensionElements 下的自定义元素
                    org.w3c.dom.NodeList children = el.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        org.w3c.dom.Node child = children.item(j);
                        if ("extensionElements".equals(child.getLocalName())) {
                            org.w3c.dom.NodeList extChildren = child.getChildNodes();
                            for (int k = 0; k < extChildren.getLength(); k++) {
                                org.w3c.dom.Node extNode = extChildren.item(k);
                                String localName = extNode.getLocalName();
                                if (elementName.equals(localName)
                                        || ("omni:" + elementName).equals(extNode.getNodeName())) {
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
            log.warn("从原始 BPMN XML 读取扩展元素失败: activityId={}, error={}",
                    execution.getCurrentActivityId(), e.getMessage());
        }
        return null;
    }

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

    private void handleFallbackExecution(DelegateExecution execution, String fallbackStrategy,
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
                log.warn("Fallback ASSIGN_ADMIN(ExecutionListener): {}", ids);
            }
            default -> {
                log.error("候选人解析失败且 fallback={}: {}", strategy, reason);
                execution.setVariable("candidateUserIds", List.of("1"));
                initApprovalCounters(execution, "ALL", 1);
            }
        }
    }

    /**
     * 初始化审批计数器（仅在变量尚未初始化时设置，防止重复调用重置）。
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

    // ======================== 扩展元素读取 ========================

    /**
     * 从 Flowable 任务的 BPMN 模型中读取指定名称的扩展元素文本内容。
     */
    private String readExtensionElement(DelegateTask delegateTask, String elementName) {
        try {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(delegateTask.getProcessDefinitionId());
            if (bpmnModel == null) return null;
            FlowNode flowNode = (FlowNode) bpmnModel.getFlowElement(delegateTask.getTaskDefinitionKey());
            if (flowNode == null) return null;

            var extensionElements = flowNode.getExtensionElements();
            if (extensionElements == null || extensionElements.isEmpty()) {
                return null;
            }

            for (var entry : extensionElements.entrySet()) {
                for (var ext : entry.getValue()) {
                    if (elementName.equals(ext.getName())
                            || (ext.getName() != null && ext.getName().endsWith(":" + elementName))) {
                        String text = ext.getElementText();
                        if (text != null && !text.isBlank()) return text;
                        text = ext.getAttributeValue(null, "body");
                        if (text != null && !text.isBlank()) return text;
                        var bodyChildren = ext.getChildElements().get("body");
                        if (bodyChildren != null && !bodyChildren.isEmpty()) {
                            return bodyChildren.get(0).getElementText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取扩展元素失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 安全获取 JSON 节点的文本值。
     */
    private String getTextValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    // ======================== 锚点解析 ========================

    /**
     * 解析流程发起人用户 ID。
     */
    private Long resolveStartUserId(DelegateTask delegateTask) {
        Object initiatorObj = delegateTask.getVariable("initiator");
        if (initiatorObj != null) {
            try {
                return Long.valueOf(initiatorObj.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 从 DelegateTask 解析租户 ID。
     */
    private Long resolveTenantId(DelegateTask delegateTask) {
        try {
            String tenantId = delegateTask.getTenantId();
            return tenantId != null ? Long.valueOf(tenantId) : 1L;
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    /**
     * 根据锚点类型和参数解析目标组织单元 ID。
     */
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

    /**
     * 获取用户的主组织单元 ID。
     */
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

    /**
     * 获取指定组织单元的父级组织 ID。
     */
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

    /**
     * 沿组织树向上查找指定类型的父节点。
     */
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

    /**
     * 在子节点中查找指定编码的单元。
     */
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

    /**
     * 查找同父节点下指定编码的兄弟单元。
     */
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

    /**
     * 根据编码直接查找部门（全局查找，不依赖当前用户组织）。
     * 用于跨部门审批场景。
     */
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

    // ======================== ABSOLUTE_UNIT 多选专用解析 ========================

    /**
     * 从 anchorParams 提取组织单元 ID 列表。
     * <p>优先读取 {@code unitIds} 数组（新格式），回退读取 {@code unitId} 单值（旧格式兼容）。</p>
     *
     * @param anchorParams 锚点参数 JSON 节点
     * @return 组织单元 ID 列表，如无有效参数则返回空列表
     */
    private List<Long> extractUnitIds(JsonNode anchorParams) {
        if (anchorParams == null) return Collections.emptyList();

        // 新格式：unitIds 数组
        if (anchorParams.has("unitIds") && anchorParams.get("unitIds").isArray()
                && !anchorParams.get("unitIds").isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (JsonNode node : anchorParams.get("unitIds")) {
                ids.add(node.asLong());
            }
            return ids;
        }

        // 旧格式兼容：unitId 单值
        if (anchorParams.has("unitId") && !anchorParams.get("unitId").isNull()) {
            return List.of(anchorParams.get("unitId").asLong());
        }

        return Collections.emptyList();
    }

    /**
     * 在指定的多个组织单元中查询拥有指定角色的候选人。
     *
     * @param roleCode 角色编码
     * @param unitIds  组织单元 ID 列表
     * @param tenantId 租户 ID
     * @return 候选人用户 ID 列表
     */
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

    /**
     * 在同一组织单元内查询拥有指定角色的候选人 ID 列表。
     */
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

    /**
     * 在指定组织单元及其子单元中查询拥有指定角色的候选人 ID 列表。
     */
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

    /**
     * 在指定组织单元的直属子单元中查询拥有指定角色的候选人 ID 列表。
     * 不包含锚点单元自身，仅在其直接子组织中查找。
     */
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

    // ======================== Fallback 策略 ========================

    /**
     * 处理候选人解析失败时的兜底策略。
     */
    private void handleFallback(DelegateTask delegateTask, String fallbackStrategy,
                                 String reason, Long tenantId) {
        String strategy = fallbackStrategy != null ? fallbackStrategy : "ERROR";

        switch (strategy) {
            case "ASSIGN_ADMIN" -> {
                // 分配给租户管理员（角色编码 SUPER_ADMIN）
                List<Long> adminIds = resolveCandidatesInSameUnit("SUPER_ADMIN", 1L, tenantId);
                if (adminIds.isEmpty()) {
                    // 如果查不到管理员，尝试 user_id = 1
                    delegateTask.addCandidateUser("1");
                    log.warn("Fallback ASSIGN_ADMIN: 未找到 SUPER_ADMIN 角色用户，使用默认管理员: taskId={}",
                            delegateTask.getId());
                } else {
                    adminIds.forEach(id -> delegateTask.addCandidateUser(String.valueOf(id)));
                    log.warn("Fallback ASSIGN_ADMIN: 分配给管理员: taskId={}, admins={}",
                            delegateTask.getId(), adminIds);
                }
            }
            case "ESCALATE_PARENT" -> {
                // 向上查找一级父组织中有该角色的候选人
                log.warn("Fallback ESCALATE_PARENT: {}, taskId={}", reason, delegateTask.getId());
                // 简单实现：记录日志，实际场景中可能需要解析父级组织
            }
            default -> {
                // ERROR: 不做任何操作，让流程引擎继续（任务可能没有候选人）
                log.error("候选人解析失败且 fallback=ERROR: taskId={}, reason={}",
                        delegateTask.getId(), reason);
            }
        }
    }
}
