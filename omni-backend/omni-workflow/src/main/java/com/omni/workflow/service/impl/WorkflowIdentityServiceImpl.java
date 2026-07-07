package com.omni.workflow.service.impl;

import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.*;
import com.omni.workflow.service.WorkflowIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流身份查询服务实现。
 * <p>
 * 通过 JdbcTemplate 跨库查询 {@code omni_auth} 中的用户、角色、组织表，
 * 为流程设计器的属性面板提供身份选择数据。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowIdentityServiceImpl implements WorkflowIdentityService {

    private final JdbcTemplate jdbcTemplate;

    /** {@inheritDoc} */
    @Override
    public List<IdentityUserVO> listUsers(Long tenantId, String keyword) {
        try {
            String sql;
            Object[] params;
            if (keyword != null && !keyword.isBlank()) {
                sql = """
                        SELECT u.id AS user_id, u.username, u.nickname, u.primary_unit_id AS unit_id, ou.name AS unit_name
                        FROM omni_auth.sys_user u
                        LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
                        WHERE u.tenant_id = ? AND u.status = 1
                          AND (u.username LIKE ? OR u.nickname LIKE ?)
                        ORDER BY u.id ASC
                        LIMIT 100
                        """;
                String like = "%" + keyword + "%";
                params = new Object[]{tenantId, like, like};
            } else {
                sql = """
                        SELECT u.id AS user_id, u.username, u.nickname, u.primary_unit_id AS unit_id, ou.name AS unit_name
                        FROM omni_auth.sys_user u
                        LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
                        WHERE u.tenant_id = ? AND u.status = 1
                        ORDER BY u.id ASC
                        LIMIT 100
                        """;
                params = new Object[]{tenantId};
            }

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    IdentityUserVO.builder()
                            .userId(rs.getLong("user_id"))
                            .username(rs.getString("username"))
                            .nickname(rs.getString("nickname"))
                            .unitId(rs.getObject("unit_id") != null ? rs.getLong("unit_id") : null)
                            .unitName(rs.getString("unit_name"))
                            .build(), params);
        } catch (DataAccessException e) {
            log.warn("查询用户列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<IdentityRoleVO> listRoles(Long tenantId) {
        try {
            return jdbcTemplate.query("""
                    SELECT id, role_code, role_name, data_scope
                    FROM omni_auth.sys_role
                    WHERE tenant_id = ? AND status = 1
                    ORDER BY sort ASC, id ASC
                    """, (rs, rowNum) ->
                    IdentityRoleVO.builder()
                            .id(rs.getLong("id"))
                            .roleCode(rs.getString("role_code"))
                            .roleName(rs.getString("role_name"))
                            .dataScope(rs.getString("data_scope"))
                            .build(), tenantId);
        } catch (DataAccessException e) {
            log.warn("查询角色列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<OrgTreeNodeVO> getOrgTree(Long tenantId) {
        List<OrgTreeNodeVO> allNodes = getAllUnits(tenantId);

        // 构建树
        Map<Long, OrgTreeNodeVO> nodeMap = new LinkedHashMap<>();
        allNodes.forEach(n -> nodeMap.put(n.getId(), n));

        List<OrgTreeNodeVO> roots = new ArrayList<>();
        for (OrgTreeNodeVO node : allNodes) {
            if (node.getParentId() == null || node.getParentId() == 0) {
                roots.add(node);
            } else {
                OrgTreeNodeVO parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    /** {@inheritDoc} */
    @Override
    public List<OrgTreeNodeVO> getUnitOptions(Long tenantId) {
        return getOrgTree(tenantId);
    }

    /** {@inheritDoc} */
    @Override
    public ResolvePreviewResult resolvePreview(ResolvePreviewRequest request, Long tenantId) {
        // ABSOLUTE_UNIT 走独立的多组织候选人解析路径（支持多选）
        if ("ABSOLUTE_UNIT".equals(request.getAnchorType())) {
            List<Long> unitIds = extractUnitIds(request.getAnchorParams());
            List<ResolvePreviewResult.CandidateUser> candidates =
                    resolveCandidatesForAbsoluteUnits(request.getRoleCode(), unitIds, tenantId);
            return ResolvePreviewResult.builder()
                    .candidateCount(candidates.size())
                    .candidates(candidates)
                    .build();
        }

        // 解析锚点获取目标组织单元 ID
        Long anchorUnitId = resolveAnchorUnitId(request, tenantId);
        if (anchorUnitId == null) {
            return ResolvePreviewResult.builder()
                    .candidateCount(0)
                    .candidates(Collections.emptyList())
                    .build();
        }

        // 根据角色编码 + 组织范围查询候选人
        String scopeMode = request.getScopeMode() != null ? request.getScopeMode() : "SAME_UNIT";
        List<ResolvePreviewResult.CandidateUser> candidates;

        if ("UNIT_AND_BELOW".equals(scopeMode)) {
            candidates = resolveCandidatesInUnitAndBelow(request.getRoleCode(), anchorUnitId, tenantId);
        } else if ("CHILDREN_ONLY".equals(scopeMode)) {
            candidates = resolveCandidatesInChildrenOnly(request.getRoleCode(), anchorUnitId, tenantId);
        } else {
            candidates = resolveCandidatesInSameUnit(request.getRoleCode(), anchorUnitId, tenantId);
        }

        return ResolvePreviewResult.builder()
                .candidateCount(candidates.size())
                .candidates(candidates)
                .build();
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 查询所有组织单元（扁平列表）。
     */
    private List<OrgTreeNodeVO> getAllUnits(Long tenantId) {
        try {
            return jdbcTemplate.query("""
                    SELECT id, parent_id, name, type, unit_code, status
                    FROM omni_auth.sys_org_unit
                    WHERE tenant_id = ? AND status = 1
                    ORDER BY sort ASC, id ASC
                    """, (rs, rowNum) ->
                    OrgTreeNodeVO.builder()
                            .id(rs.getLong("id"))
                            .parentId(rs.getObject("parent_id") != null
                                    ? rs.getLong("parent_id") : null)
                            .name(rs.getString("name"))
                            .type(rs.getString("type"))
                            .unitCode(rs.getString("unit_code"))
                            .status(rs.getInt("status"))
                            .children(null)
                            .build(), tenantId);
        } catch (DataAccessException e) {
            log.warn("查询组织单元失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析锚点获取目标组织单元 ID。
     */
    private Long resolveAnchorUnitId(ResolvePreviewRequest request, Long tenantId) {
        if (request.getAnchorType() == null || request.getSimulateUserId() == null) {
            return null;
        }

        return switch (request.getAnchorType()) {
            case "START_USER_PRIMARY_UNIT" -> getPrimaryUnitId(request.getSimulateUserId(), tenantId);
            case "PARENT_BY_TYPE" -> {
                Long primaryUnit = getPrimaryUnitId(request.getSimulateUserId(), tenantId);
                if (primaryUnit == null) yield null;
                String targetType = request.getAnchorParams() != null
                        ? (String) request.getAnchorParams().get("targetType") : null;
                yield findParentByType(primaryUnit, targetType, tenantId);
            }
            case "CHILD_BY_CODE" -> {
                Long primaryUnit = getPrimaryUnitId(request.getSimulateUserId(), tenantId);
                if (primaryUnit == null) yield null;
                String childCode = request.getAnchorParams() != null
                        ? (String) request.getAnchorParams().get("childCode") : null;
                yield findChildByCode(primaryUnit, childCode, tenantId);
            }
            case "SIBLING_BY_CODE" -> {
                Long primaryUnit = getPrimaryUnitId(request.getSimulateUserId(), tenantId);
                if (primaryUnit == null) yield null;
                String siblingCode = request.getAnchorParams() != null
                        ? (String) request.getAnchorParams().get("siblingCode") : null;
                yield findSiblingByCode(primaryUnit, siblingCode, tenantId);
            }
            case "PARENT_CHILDREN" -> {
                Long primaryUnit = getPrimaryUnitId(request.getSimulateUserId(), tenantId);
                if (primaryUnit == null) yield null;
                String targetType = request.getAnchorParams() != null
                        ? (String) request.getAnchorParams().get("targetType") : null;
                yield findParentByType(primaryUnit, targetType, tenantId);
            }
            case "DEPT_BY_CODE" -> {
                String deptCode = request.getAnchorParams() != null
                        ? (String) request.getAnchorParams().get("deptCode") : null;
                yield findDeptByCode(deptCode, tenantId);
            }
            case "ABSOLUTE_UNIT" -> {
                if (request.getAnchorParams() != null) {
                    Object unitId = request.getAnchorParams().get("unitId");
                    yield unitId != null ? Long.valueOf(unitId.toString()) : null;
                }
                yield null;
            }
            case "PARENT" -> {
                Long primaryUnit = getPrimaryUnitId(request.getSimulateUserId(), tenantId);
                if (primaryUnit == null) yield null;
                yield findParentId(primaryUnit, tenantId);
            }
            case "CHILD_UNIT" -> {
                if (request.getAnchorParams() != null) {
                    Object unitId = request.getAnchorParams().get("unitId");
                    yield unitId != null ? Long.valueOf(unitId.toString()) : null;
                }
                yield null;
            }
            case "SIBLING_UNIT" -> {
                if (request.getAnchorParams() != null) {
                    Object unitId = request.getAnchorParams().get("unitId");
                    yield unitId != null ? Long.valueOf(unitId.toString()) : null;
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
        } catch (DataAccessException e) {
            log.warn("查询用户主组织失败: {}", e.getMessage());
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
        } catch (DataAccessException e) {
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
            int maxDepth = 20; // 防止无限循环
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
        } catch (DataAccessException e) {
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
        } catch (DataAccessException e) {
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
            // 先找到当前节点的 parent_id
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
        } catch (DataAccessException e) {
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
        } catch (DataAccessException e) {
            log.warn("按编码查找部门失败: deptCode={}, error={}", deptCode, e.getMessage());
            return null;
        }
    }

    /**
     * 在同一组织单元内查询拥有指定角色的候选人。
     */
    private List<ResolvePreviewResult.CandidateUser> resolveCandidatesInSameUnit(
            String roleCode, Long unitId, Long tenantId) {
        try {
            return jdbcTemplate.query("""
                    SELECT DISTINCT u.id AS user_id, u.username, u.nickname, ou.name AS unit_name
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id = ? AND urs.status = 1
                      AND u.status = 1
                    ORDER BY u.id ASC
                    """, (rs, rowNum) ->
                    ResolvePreviewResult.CandidateUser.builder()
                            .userId(rs.getLong("user_id"))
                            .username(rs.getString("username"))
                            .nickname(rs.getString("nickname"))
                            .unitName(rs.getString("unit_name"))
                            .build(), roleCode, tenantId, unitId);
        } catch (DataAccessException e) {
            log.warn("解析同单元候选人失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 在指定组织单元及其子单元中查询拥有指定角色的候选人。
     */
    private List<ResolvePreviewResult.CandidateUser> resolveCandidatesInUnitAndBelow(
            String roleCode, Long unitId, Long tenantId) {
        try {
            // 获取目标节点的物化路径
            List<String> paths = jdbcTemplate.queryForList("""
                    SELECT path FROM omni_auth.sys_org_unit WHERE id = ? AND tenant_id = ?
                    """, String.class, unitId, tenantId);
            if (paths.isEmpty() || paths.get(0) == null) {
                return Collections.emptyList();
            }

            // 查找目标节点及其所有子孙节点的 ID
            String path = paths.get(0);
            List<Long> descendantIds = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE tenant_id = ? AND (id = ? OR path LIKE ?)
                    """, Long.class, tenantId, unitId, path + "%");

            if (descendantIds.isEmpty()) {
                return Collections.emptyList();
            }

            String placeholders = descendantIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT u.id AS user_id, u.username, u.nickname, ou.name AS unit_name
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1
                      AND u.status = 1
                    ORDER BY u.id ASC
                    """.formatted(placeholders);

            Object[] params = new Object[2 + descendantIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < descendantIds.size(); i++) {
                params[2 + i] = descendantIds.get(i);
            }

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    ResolvePreviewResult.CandidateUser.builder()
                            .userId(rs.getLong("user_id"))
                            .username(rs.getString("username"))
                            .nickname(rs.getString("nickname"))
                            .unitName(rs.getString("unit_name"))
                            .build(), params);
        } catch (DataAccessException e) {
            log.warn("解析单元及下属候选人失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 在指定组织单元的直属子单元中查询拥有指定角色的候选人。
     * 不包含锚点单元自身，仅在其直接子组织中查找。
     */
    private List<ResolvePreviewResult.CandidateUser> resolveCandidatesInChildrenOnly(
            String roleCode, Long unitId, Long tenantId) {
        try {
            List<Long> childIds = jdbcTemplate.queryForList("""
                    SELECT id FROM omni_auth.sys_org_unit
                    WHERE parent_id = ? AND tenant_id = ? AND status = 1
                    """, Long.class, unitId, tenantId);
            if (childIds.isEmpty()) {
                return Collections.emptyList();
            }

            String placeholders = childIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT u.id AS user_id, u.username, u.nickname, ou.name AS unit_name
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1
                      AND u.status = 1
                    ORDER BY u.id ASC
                    """.formatted(placeholders);

            Object[] params = new Object[2 + childIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < childIds.size(); i++) {
                params[2 + i] = childIds.get(i);
            }

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    ResolvePreviewResult.CandidateUser.builder()
                            .userId(rs.getLong("user_id"))
                            .username(rs.getString("username"))
                            .nickname(rs.getString("nickname"))
                            .unitName(rs.getString("unit_name"))
                            .build(), params);
        } catch (DataAccessException e) {
            log.warn("解析直属子单元候选人失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== ABSOLUTE_UNIT 多选专用解析 ========================

    /**
     * 从 anchorParams Map 提取组织单元 ID 列表。
     * <p>优先读取 {@code unitIds} 数组（新格式），回退读取 {@code unitId} 单值（旧格式兼容）。</p>
     */
    @SuppressWarnings("unchecked")
    private List<Long> extractUnitIds(Map<String, Object> anchorParams) {
        if (anchorParams == null) return Collections.emptyList();

        // 新格式：unitIds 数组
        Object unitIdsObj = anchorParams.get("unitIds");
        if (unitIdsObj instanceof List<?> unitIdsList && !unitIdsList.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Object id : unitIdsList) {
                ids.add(Long.valueOf(id.toString()));
            }
            return ids;
        }

        // 旧格式兼容：unitId 单值
        Object unitIdObj = anchorParams.get("unitId");
        if (unitIdObj != null) {
            return List.of(Long.valueOf(unitIdObj.toString()));
        }

        return Collections.emptyList();
    }

    /**
     * 在指定的多个组织单元中查询拥有指定角色的候选人（预览用）。
     */
    private List<ResolvePreviewResult.CandidateUser> resolveCandidatesForAbsoluteUnits(
            String roleCode, List<Long> unitIds, Long tenantId) {
        if (unitIds == null || unitIds.isEmpty()) return Collections.emptyList();
        try {
            String placeholders = unitIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = """
                    SELECT DISTINCT u.id AS user_id, u.username, u.nickname, ou.name AS unit_name
                    FROM omni_auth.sys_user_role_scope urs
                    JOIN omni_auth.sys_role r ON urs.role_id = r.id
                    JOIN omni_auth.sys_user u ON urs.user_id = u.id
                    LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
                    WHERE r.role_code = ? AND r.tenant_id = ? AND r.status = 1
                      AND urs.unit_id IN (%s) AND urs.status = 1
                      AND u.status = 1
                    ORDER BY u.id ASC
                    """.formatted(placeholders);

            Object[] params = new Object[2 + unitIds.size()];
            params[0] = roleCode;
            params[1] = tenantId;
            for (int i = 0; i < unitIds.size(); i++) {
                params[2 + i] = unitIds.get(i);
            }

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    ResolvePreviewResult.CandidateUser.builder()
                            .userId(rs.getLong("user_id"))
                            .username(rs.getString("username"))
                            .nickname(rs.getString("nickname"))
                            .unitName(rs.getString("unit_name"))
                            .build(), params);
        } catch (DataAccessException e) {
            log.warn("ABSOLUTE_UNIT 预览候选人解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
