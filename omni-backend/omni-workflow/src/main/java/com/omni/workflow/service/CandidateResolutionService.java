package com.omni.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.engine.XmlSecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选人解析公共服务，封装锚点解析、组织查询、候选人查询和 fallback 逻辑。
 * <p>
 * 本类提取自 {@code CandidateResolverDelegate}、{@code CandidateResolverBean} 和
 * {@code ScopedRoleAssignmentListener} 中高度重复的候选人解析代码，提供统一的解析入口。
 * 三个调用入口仅负责各自的 Flowable 生命周期集成。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateResolutionService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;

    // ======================== 核心解析方法 ========================

    /**
     * 从 BPMN XML 读取指定活动的 omni:assignment JSON 配置。
     *
     * @param processDefinitionId 流程定义 ID
     * @param activityId          BPMN 活动元素 ID
     * @return assignment JSON 字符串，不存在则返回 null
     */
    public String readAssignmentFromBpmnXml(String processDefinitionId, String activityId) {
        try {
            InputStream is = repositoryService.getProcessModel(processDefinitionId);
            if (is == null) return null;
            byte[] modelBytes = is.readAllBytes();

            DocumentBuilderFactory dbf = XmlSecurityUtils.createSafeDocumentBuilderFactory();
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
     * 解析候选人列表（核心方法）。
     * <p>从 BPMN XML 读取 assignment 配置，根据角色编码 + 组织锚点 + 作用域模式解析候选人。</p>
     *
     * @param processDefinitionId 流程定义 ID
     * @param activityId          BPMN 活动元素 ID
     * @param startUserId         流程发起人用户 ID
     * @param tenantId            租户 ID
     * @return 候选人用户 ID 列表，解析失败或无候选人返回空列表
     */
    public List<Long> resolveCandidates(String processDefinitionId, String activityId,
                                        Long startUserId, Long tenantId) {
        try {
            String assignmentJson = readAssignmentFromBpmnXml(processDefinitionId, activityId);
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

            return doResolveCandidates(roleCode, anchorType, scopeMode, anchorParams, startUserId, tenantId);
        } catch (Exception e) {
            log.warn("解析候选人失败: activityId={}, error={}", activityId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从已解析的 assignment 配置中执行候选人解析（不读取 BPMN XML）。
     *
     * @param roleCode     角色编码
     * @param anchorType   锚点类型
     * @param scopeMode    作用域模式
     * @param anchorParams 锚点参数 JSON
     * @param startUserId  流程发起人 ID
     * @param tenantId     租户 ID
     * @return 候选人用户 ID 列表
     */
    public List<Long> doResolveCandidates(String roleCode, String anchorType, String scopeMode,
                                          JsonNode anchorParams, Long startUserId, Long tenantId) {
        if ("ABSOLUTE_UNIT".equals(anchorType)) {
            List<Long> unitIds = extractUnitIds(anchorParams);
            List<Long> candidates = resolveCandidatesForAbsoluteUnits(roleCode, unitIds, tenantId);
            if (candidates.isEmpty()) {
                log.warn("ABSOLUTE_UNIT 候选人为空: roleCode={}, unitIds={}, tenantId={}",
                        roleCode, unitIds, tenantId);
            }
            return candidates;
        }

        Long anchorUnitId = resolveAnchorUnitId(anchorType, anchorParams, startUserId, tenantId);
        if (anchorUnitId == null) {
            log.debug("无法解析锚点组织单元: anchorType={}", anchorType);
            return Collections.emptyList();
        }

        return resolveScoped(roleCode, scopeMode, anchorUnitId, tenantId);
    }

    /**
     * 按 scopeMode 在指定锚点单位解析候选人（SAME_UNIT/UNIT_AND_BELOW/CHILDREN_ONLY 的唯一分发入口）。
     */
    private List<Long> resolveScoped(String roleCode, String scopeMode, Long anchorUnitId, Long tenantId) {
        if ("UNIT_AND_BELOW".equals(scopeMode)) {
            return resolveCandidatesInUnitAndBelow(roleCode, anchorUnitId, tenantId);
        } else if ("CHILDREN_ONLY".equals(scopeMode)) {
            return resolveCandidatesInChildrenOnly(roleCode, anchorUnitId, tenantId);
        } else {
            return resolveCandidatesInSameUnit(roleCode, anchorUnitId, tenantId);
        }
    }

    /**
     * ESCALATE_PARENT 候选策略（D1 批准的语义）：无候选人时，从锚点组织的父级开始逐级向上
     * 沿组织树重解同一 roleCode + 同一 scopeMode 的候选人，命中即返回。
     * <p>上溯层级：到根单位（parent_id 为空）为止；角色/组织过滤：维持现有 scopeMode 与安全 JOIN
     * （仅取启用用户）；多锚点：由调用侧保证 anchor 解析为单一单位（ABSOLUTE_UNIT 多 unitIds 场景
     * 取首个解析锚点后上溯）。上溯至根仍无候选→返回空，由调用方按 ERROR 语义失败关闭（不 fail-open、
     * 不自动审批）。依赖故障（组织/权限表查询异常）在底层 helper 已吐空→最终表现为根未命中而抛错，
     * 不会虚报成功。</p>
     *
     * @param roleCode     角色编码
     * @param anchorType   锚点类型
     * @param scopeMode    作用域模式
     * @param anchorParams 锚点参数
     * @param startUserId  发起人 ID
     * @param tenantId     租户 ID
     * @return 命中的上级候选集；上溯至根仍空则空
     */
    public List<Long> escalateToParentForCandidates(String roleCode, String anchorType, String scopeMode,
                                                    JsonNode anchorParams, Long startUserId, Long tenantId) {
        Long anchorUnitId = resolveAnchorUnitId(anchorType, anchorParams, startUserId, tenantId);
        if (anchorUnitId == null) {
            return Collections.emptyList();
        }
        Long current = findParentId(anchorUnitId, tenantId);
        int maxDepth = 20;
        while (current != null && maxDepth-- > 0) {
            List<Long> found = resolveScoped(roleCode, scopeMode, current, tenantId);
            if (!found.isEmpty()) {
                log.warn("ESCALATE_PARENT 命中上级单位候选: roleCode={}, ancestorUnitId={}, candidates={}",
                        roleCode, current, found);
                return found;
            }
            current = findParentId(current, tenantId);
        }
        return Collections.emptyList();
    }

    /**
     * 处理 fallback 策略，返回候选人 ID 字符串列表。
     * <p>
     * L5 修复：不再硬编码 user_id=1，当 ASSIGN_ADMIN 无法找到管理员时抛出异常，
     * 让流程中断而非错误分配给不存在的用户。
     * </p>
     *
     * @param fallbackStrategy fallback 策略名称（ASSIGN_ADMIN / ERROR）
     * @param reason           触发 fallback 的原因
     * @param tenantId         租户 ID
     * @return 候选人用户 ID 字符串列表
     * @throws IllegalStateException 当 fallback 为 ERROR 或无法找到管理员时
     */
    public List<String> handleFallback(String fallbackStrategy, String reason, Long tenantId) {
        String strategy = fallbackStrategy != null ? fallbackStrategy : "ERROR";
        if ("ASSIGN_ADMIN".equals(strategy)) {
            List<Long> adminIds = resolveCandidatesInSameUnit("SUPER_ADMIN", 1L, tenantId);
            if (adminIds.isEmpty()) {
                throw new IllegalStateException(
                        "Fallback ASSIGN_ADMIN 失败: 未找到 SUPER_ADMIN 角色用户, tenantId=" + tenantId);
            }
            List<String> ids = adminIds.stream().map(String::valueOf).collect(Collectors.toList());
            log.warn("Fallback ASSIGN_ADMIN: {}", ids);
            return ids;
        }
        throw new IllegalStateException("候选人解析失败且 fallback=" + strategy + ": " + reason);
    }

    /**
     * 初始化审批计数器（每个多实例审批活动实例进入时写入一次）。
     * <p>
     * WP-015 FR04 修正（Codex RF01）：作用域必须绑定到**本 MI 活动的根执行**，
     * 而不是沿执行树向上落到流程实例作用域。旧实现注释声称使用
     * {@code setVariableLocal}，实际却调用 {@code setVariable}，导致
     * {@code _counters_init} 沉淀在流程实例级：顺序审批的下一个 MI 节点会读到
     * 遗留标志而跳过初始化，使票数/阈值跳节点泄漏。
     * </p>
     * <p>
     * 当前实现：
     * <ol>
     *   <li>从传入执行沿 {@code getParent()} 上溯，定位第一个
     *       {@code isMultiInstanceRoot()} 为真的执行作为 MI 根；定位失败（非多实例节点）
     *       时退化为在传入执行本地写入；</li>
     *   <li>全部计数器与 {@code _counters_init}、{@code approvalMode} 均用
     *       {@code setVariableLocal} 写在该 MI 根上，完成条件在同一作用域求值；</li>
     *   <li>幂等标志只在 MI 根本地读（{@code getVariableLocal}）：同一活动实例内
     *       重入（如 addSigner 触发集合重解析）不重置已有票数；不同 MI 活动实例
     *       拥有各自根执行，互不可见，因此正常初始化。</li>
     * </ol>
     * 加签导致的有效全员阈值变化不由本方法负责，而是由
     * {@code ApprovalServiceImpl} 在加签/减签后按“已同意 + 仍在待办”重算。
     * </p>
     *
     * @param execution      执行委托（MI 根、子执行或其它活动执行均可）
     * @param approvalMode   "ALL" 全员通过 / "ANY" 任一人通过
     * @param candidateCount 候选人总数
     */
    public void initApprovalCounters(DelegateExecution execution, String approvalMode, int candidateCount) {
        DelegateExecution miRoot = findMultiInstanceRoot(execution);
        Object flag = miRoot.getVariableLocal("_counters_init");
        if (Boolean.TRUE.equals(flag)) {
            log.debug("审批计数器已在本 MI 活动实例初始化，跳过重复写入（不重置己有票数）: activityId={}",
                    execution.getCurrentActivityId());
            return;
        }
        int requiredApprovals = "ANY".equals(approvalMode) ? 1 : candidateCount;
        miRoot.setVariableLocal("approvedCount", 0);
        miRoot.setVariableLocal("rejectedCount", 0);
        miRoot.setVariableLocal("requiredApprovals", requiredApprovals);
        miRoot.setVariableLocal("approvalMode", approvalMode);
        miRoot.setVariableLocal("_counters_init", Boolean.TRUE);
        log.debug("审批计数器初始化完成: miRootExecution={}, activityId={}, mode={}, requiredApprovals={}",
                miRoot.getId(), execution.getCurrentActivityId(), approvalMode, requiredApprovals);
    }

    /**
     * 沿执行树上溯定位多实例根执行。
     * <p>
     * 最多上溯 32 层防止异常拓扑死循环；若未找到 MI 根（非多实例节点），
     * 返回传入执行本身，保持原有行为可用。
     * </p>
     *
     * @param execution 当前执行
     * @return MI 根执行；找不到时退化为传入执行
     */
    private DelegateExecution findMultiInstanceRoot(DelegateExecution execution) {
        DelegateExecution current = execution;
        int depth = 0;
        while (current != null && depth < 32) {
            if (current.isMultiInstanceRoot()) {
                return current;
            }
            DelegateExecution parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
            depth++;
        }
        return execution;
    }

    /**
     * 从 BPMN XML 中提取所有 userTask 节点的 ID 和名称。
     *
     * @param processDefinitionId 流程定义 ID
     * @return activityId → activityName 映射
     */
    public Map<String, String> extractUserTaskNodes(String processDefinitionId) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            InputStream is = repositoryService.getProcessModel(processDefinitionId);
            if (is == null) return result;
            byte[] modelBytes = is.readAllBytes();

            DocumentBuilderFactory dbf = XmlSecurityUtils.createSafeDocumentBuilderFactory();
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

    /**
     * 从 ServiceTask 的 outgoing 连线找到目标 activityId。
     *
     * @param processDefinitionId 流程定义 ID
     * @param sourceActivityId    源活动 ID（ServiceTask）
     * @return 目标活动 ID，未找到返回 null
     */
    public String resolveTargetActivityId(String processDefinitionId, String sourceActivityId) {
        try {
            InputStream is = repositoryService.getProcessModel(processDefinitionId);
            if (is == null) return null;
            byte[] modelBytes = is.readAllBytes();

            DocumentBuilderFactory factory = XmlSecurityUtils.createSafeDocumentBuilderFactory();
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(modelBytes));

            NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
            for (int i = 0; i < flows.getLength(); i++) {
                Element flow = (Element) flows.item(i);
                if (sourceActivityId.equals(flow.getAttribute("sourceRef"))) {
                    return flow.getAttribute("targetRef");
                }
            }
        } catch (Exception e) {
            log.warn("解析目标 activityId 失败: {}", e.getMessage());
        }
        return null;
    }

    // ======================== 流程变量解析 ========================

    public Long resolveStartUserId(DelegateExecution execution) {
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

    public Long resolveTenantId(DelegateExecution execution) {
        try {
            String tenantId = execution.getTenantId();
            return tenantId != null ? Long.valueOf(tenantId) : 1L;
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    public String getTextValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText();
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

    // ======================== 组织查询 ========================

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

    // ======================== 候选人查询 ========================

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
