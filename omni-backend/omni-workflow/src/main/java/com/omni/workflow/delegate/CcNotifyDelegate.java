package com.omni.workflow.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.workflow.entity.WfCcRecord;
import com.omni.workflow.mapper.WfCcRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 抄送通知代理（ServiceTask 实现）。
 * <p>
 * 在 BPMN 中以 {@code delegateExpression="${ccNotifyDelegate}"} 引用。
 * 执行时从 {@code omni:cc} 扩展元素读取收件人配置，
 * 写入 {@code wf_cc_record} 表，不调用待办通知接口（抄送不进入待办）。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("ccNotifyDelegate")
@RequiredArgsConstructor
public class CcNotifyDelegate implements JavaDelegate {

    private final WfCcRecordMapper ccRecordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public void execute(DelegateExecution execution) {
        try {
            // 1. 从扩展元素读取 omni:cc 配置
            String ccConfigJson = readExtensionElement(execution, "omni:cc");
            if (ccConfigJson == null || ccConfigJson.isBlank()) {
                log.debug("ServiceTask 未配置 omni:cc，跳过抄送: activityId={}", execution.getCurrentActivityId());
                return;
            }

            JsonNode config = objectMapper.readTree(ccConfigJson);
            List<Long> recipientUserIds = resolveRecipients(config, execution);

            if (recipientUserIds.isEmpty()) {
                log.info("抄送无收件人: activityId={}, processInstanceId={}",
                        execution.getCurrentActivityId(), execution.getProcessInstanceId());
                return;
            }

            // 2. 获取流程标题
            String title = (String) execution.getVariable("title");
            if (title == null) {
                title = "流程抄送";
            }

            // 3. 获取租户 ID
            Long tenantId;
            try {
                tenantId = execution.getTenantId() != null
                        ? Long.valueOf(execution.getTenantId()) : 1L;
            } catch (NumberFormatException e) {
                tenantId = 1L;
            }

            // 4. 写入抄送记录
            for (Long userId : recipientUserIds) {
                WfCcRecord record = new WfCcRecord();
                record.setTenantId(tenantId);
                record.setProcessInstanceId(execution.getProcessInstanceId());
                record.setSourceActivityId(execution.getCurrentActivityId());
                record.setUserId(userId);
                record.setTitle(title);
                record.setReadStatus(0); // 未读
                record.setCreateTime(LocalDateTime.now());
                ccRecordMapper.insert(record);
            }

            log.info("抄送通知已写入: processInstanceId={}, activityId={}, recipients={}",
                    execution.getProcessInstanceId(), execution.getCurrentActivityId(), recipientUserIds);

        } catch (Exception e) {
            log.error("CcNotifyDelegate 执行异常: processInstanceId={}, activityId={}",
                    execution.getProcessInstanceId(), execution.getCurrentActivityId(), e);
        }
    }

    /**
     * 从扩展元素中读取指定名称的文本内容。
     */
    private String readExtensionElement(DelegateExecution execution, String elementName) {
        var flowElement = execution.getCurrentFlowElement();
        if (flowElement == null) {
            return null;
        }
        var extensionElements = flowElement.getExtensionElements();
        if (extensionElements == null || extensionElements.isEmpty()) {
            return null;
        }

        for (var entry : extensionElements.entrySet()) {
            for (var ext : entry.getValue()) {
                if (elementName.equals(ext.getName())
                        || (ext.getName() != null && ext.getName().endsWith(":" + elementName))) {
                    // 尝试从 elementText 获取
                    String text = ext.getElementText();
                    if (text != null && !text.isBlank()) return text;
                    // 尝试从 body 属性获取
                    text = ext.getAttributeValue(null, "body");
                    if (text != null && !text.isBlank()) return text;
                    // 尝试从子元素 body 获取
                    var bodyChildren = ext.getChildElements().get("body");
                    if (bodyChildren != null && !bodyChildren.isEmpty()) {
                        return bodyChildren.get(0).getElementText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 解析抄送收件人列表。
     * <p>
     * 支持三种模式：
     * <ul>
     *   <li>userIds — 直接指定用户 ID 列表</li>
     *   <li>ABSOLUTE_UNIT — 指定组织（支持多选）中查角色</li>
     *   <li>roleCode + unitId — 按角色+组织查询</li>
     * </ul>
     * </p>
     */
    private List<Long> resolveRecipients(JsonNode config, DelegateExecution execution) {
        List<Long> userIds = new ArrayList<>();

        // 模式 1: 直接指定用户 ID
        if (config.has("userIds") && config.get("userIds").isArray()) {
            for (JsonNode idNode : config.get("userIds")) {
                userIds.add(idNode.asLong());
            }
            return userIds;
        }

        // 模式 2: 角色 + 组织查询
        String roleCode = config.has("roleCode") ? config.get("roleCode").asText() : null;
        if (roleCode == null || roleCode.isBlank()) {
            return Collections.emptyList();
        }

        Long tenantId;
        try {
            tenantId = execution.getTenantId() != null
                    ? Long.valueOf(execution.getTenantId()) : 1L;
        } catch (NumberFormatException e) {
            tenantId = 1L;
        }

        // ABSOLUTE_UNIT 走独立的多组织候选人解析路径（支持多选）
        String anchorType = config.has("anchorType") ? config.get("anchorType").asText() : null;
        if ("ABSOLUTE_UNIT".equals(anchorType)) {
            return resolveCcAbsoluteUnits(config, execution, roleCode, tenantId);
        }

        // 解析目标组织单元
        Long unitId = resolveCcUnitId(config, execution, tenantId);
        if (unitId == null) {
            return Collections.emptyList();
        }

        String scopeMode = config.has("scopeMode") ? config.get("scopeMode").asText() : "SAME_UNIT";

        if ("UNIT_AND_BELOW".equals(scopeMode)) {
            // 查询子树
            try {
                List<String> paths = jdbcTemplate.queryForList(
                        "SELECT path FROM omni_auth.sys_org_unit WHERE id = ? AND tenant_id = ?",
                        String.class, unitId, tenantId);
                if (!paths.isEmpty() && paths.get(0) != null) {
                    String path = paths.get(0);
                    List<Long> descendantIds = jdbcTemplate.queryForList("""
                            SELECT id FROM omni_auth.sys_org_unit
                            WHERE tenant_id = ? AND (id = ? OR path LIKE ?)
                            """, Long.class, tenantId, unitId, path + "%");

                    if (!descendantIds.isEmpty()) {
                        String placeholders = String.join(",",
                                Collections.nCopies(descendantIds.size(), "?"));
                        String sql = """
                                SELECT DISTINCT urs.user_id
                                FROM omni_auth.sys_user_role_scope urs
                                JOIN omni_auth.sys_role r ON urs.role_id = r.id
                                WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                                  AND urs.unit_id IN (%s) AND urs.status = 1
                                """.formatted(placeholders);

                        Object[] params = new Object[2 + descendantIds.size()];
                        params[0] = roleCode;
                        params[1] = tenantId;
                        for (int i = 0; i < descendantIds.size(); i++) {
                            params[2 + i] = descendantIds.get(i);
                        }
                        return jdbcTemplate.queryForList(sql, Long.class, params);
                    }
                }
            } catch (Exception e) {
                log.warn("查询 UNIT_AND_BELOW 抄送人失败: {}", e.getMessage());
            }
        } else if ("CHILDREN_ONLY".equals(scopeMode)) {
            // 查询直属子组织
            try {
                List<Long> childIds = jdbcTemplate.queryForList("""
                        SELECT id FROM omni_auth.sys_org_unit
                        WHERE parent_id = ? AND tenant_id = ? AND status = 1
                        """, Long.class, unitId, tenantId);
                if (!childIds.isEmpty()) {
                    String placeholders = String.join(",",
                            Collections.nCopies(childIds.size(), "?"));
                    String sql = """
                            SELECT DISTINCT urs.user_id
                            FROM omni_auth.sys_user_role_scope urs
                            JOIN omni_auth.sys_role r ON urs.role_id = r.id
                            WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                              AND urs.unit_id IN (%s) AND urs.status = 1
                            """.formatted(placeholders);

                    Object[] params = new Object[2 + childIds.size()];
                    params[0] = roleCode;
                    params[1] = tenantId;
                    for (int i = 0; i < childIds.size(); i++) {
                        params[2 + i] = childIds.get(i);
                    }
                    return jdbcTemplate.queryForList(sql, Long.class, params);
                }
            } catch (Exception e) {
                log.warn("查询 CHILDREN_ONLY 抄送人失败: {}", e.getMessage());
            }
        } else {
            // SAME_UNIT
            try {
                return jdbcTemplate.queryForList("""
                        SELECT DISTINCT urs.user_id
                        FROM omni_auth.sys_user_role_scope urs
                        JOIN omni_auth.sys_role r ON urs.role_id = r.id
                        WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                          AND urs.unit_id = ? AND urs.status = 1
                        """, Long.class, roleCode, tenantId, unitId);
            } catch (Exception e) {
                log.warn("查询 SAME_UNIT 抄送人失败: {}", e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    /**
     * ABSOLUTE_UNIT 专用抄送收件人解析：在指定的一个或多个组织中查询角色。
     * <p>优先读取 {@code unitIds} 数组（新格式），回退读取 {@code unitId} 单值（旧格式兼容）。</p>
     */
    private List<Long> resolveCcAbsoluteUnits(JsonNode config, DelegateExecution execution,
                                               String roleCode, Long tenantId) {
        try {
            JsonNode anchorParams = config.has("anchorParams") ? config.get("anchorParams") : null;
            List<Long> unitIds = new ArrayList<>();

            if (anchorParams != null) {
                // 新格式：unitIds 数组
                if (anchorParams.has("unitIds") && anchorParams.get("unitIds").isArray()
                        && !anchorParams.get("unitIds").isEmpty()) {
                    for (JsonNode node : anchorParams.get("unitIds")) {
                        unitIds.add(node.asLong());
                    }
                }
                // 旧格式兼容：unitId 单值
                else if (anchorParams.has("unitId") && !anchorParams.get("unitId").isNull()) {
                    unitIds.add(anchorParams.get("unitId").asLong());
                }
            }

            if (unitIds.isEmpty()) return Collections.emptyList();

            String placeholders = unitIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT urs.user_id
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1
                    """.formatted(placeholders);

            Object[] params = new Object[2 + unitIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < unitIds.size(); i++) {
                params[2 + i] = unitIds.get(i);
            }
            return jdbcTemplate.queryForList(sql, Long.class, params);
        } catch (Exception e) {
            log.warn("ABSOLUTE_UNIT 抄送收件人解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析抄送目标组织单元 ID。
     * <p>
     * 优先读取 anchorType + anchorParams（新格式），回退兼容旧 unitId。
     * 注意：ABSOLUTE_UNIT 由 resolveCcAbsoluteUnits 单独处理，不经过此方法。
     * </p>
     */
    private Long resolveCcUnitId(JsonNode config, DelegateExecution execution, Long tenantId) {
        // 获取发起人用户 ID
        String initiatorVar = config.has("initiatorVariable")
                ? config.get("initiatorVariable").asText() : "initiator";
        Object initiatorIdObj = execution.getVariable(initiatorVar);
        Long startUserId = null;
        if (initiatorIdObj != null) {
            try {
                startUserId = Long.valueOf(initiatorIdObj.toString());
            } catch (NumberFormatException ignored) {
                // 忽略
            }
        }

        // 新格式：anchorType + anchorParams
        String anchorType = config.has("anchorType") ? config.get("anchorType").asText() : null;
        JsonNode anchorParams = config.has("anchorParams") ? config.get("anchorParams") : null;

        if (anchorType != null) {
            return switch (anchorType) {
                case "START_USER_PRIMARY_UNIT" -> {
                    if (startUserId == null) yield null;
                    yield getPrimaryUnitId(startUserId, tenantId);
                }
                case "PARENT" -> {
                    if (startUserId == null) yield null;
                    Long primaryUnit = getPrimaryUnitId(startUserId, tenantId);
                    if (primaryUnit == null) yield null;
                    yield findParentId(primaryUnit, tenantId);
                }
                case "CHILD_UNIT", "SIBLING_UNIT", "ABSOLUTE_UNIT" -> {
                    if (anchorParams != null && anchorParams.has("unitId")) {
                        yield anchorParams.get("unitId").asLong();
                    }
                    yield null;
                }
                default -> null;
            };
        }

        // 旧格式兼容：直接指定 unitId
        if (config.has("unitId")) {
            return config.get("unitId").asLong();
        }

        // 回退：使用发起人主组织
        if (startUserId == null) return null;
        return getPrimaryUnitId(startUserId, tenantId);
    }

    /**
     * 获取用户的主组织单元 ID。
     */
    private Long getPrimaryUnitId(Long userId, Long tenantId) {
        try {
            List<Long> unitIds = jdbcTemplate.queryForList("""
                    SELECT unit_id FROM omni_auth.sys_user
                    WHERE id = ? AND tenant_id = ? AND status = 1
                    """, Long.class, userId, tenantId);
            return unitIds.isEmpty() ? null : unitIds.get(0);
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
}
