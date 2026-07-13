package com.omni.auth.service.impl;

import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.service.InternalDirectoryService;
import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 内部目录查询服务实现。
 * <p>所有查询都在 SQL 层显式携带租户条件，并限制批量和搜索结果规模。</p>
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class InternalDirectoryServiceImpl implements InternalDirectoryService {

    /** 单次批量查询允许的最大 ID 数量 */
    private static final int MAX_BATCH_SIZE = 100;

    /** 用户搜索允许的最大返回数量 */
    private static final int MAX_SEARCH_LIMIT = 100;

    /** 用户搜索关键字最大长度 */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /** 用户数据访问组件 */
    private final SysUserMapper sysUserMapper;

    /** 组织单元数据访问组件 */
    private final SysOrgUnitMapper sysOrgUnitMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public InternalUserDTO getUserById(Long id, Long tenantId) {
        validateId(id, "用户 ID");
        validateTenantId(tenantId);
        SysUser user = sysUserMapper.selectByIdAndTenantId(id, tenantId);
        return user == null ? null : toUserDTO(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<InternalUserDTO> getUsersByIds(List<Long> ids, Long tenantId) {
        validateTenantId(tenantId);
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectByIdsAndTenantId(normalizedIds, tenantId).stream()
                .map(this::toUserDTO)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public InternalOrgDTO getOrgById(Long id, Long tenantId) {
        validateId(id, "组织单元 ID");
        validateTenantId(tenantId);
        SysOrgUnit unit = sysOrgUnitMapper.selectByIdAndTenantId(id, tenantId);
        return unit == null ? null : toOrgDTO(unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<InternalOrgDTO> getOrgsByIds(List<Long> ids, Long tenantId) {
        validateTenantId(tenantId);
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return sysOrgUnitMapper.selectByIdsAndTenantId(normalizedIds, tenantId).stream()
                .map(this::toOrgDTO)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<InternalUserOptionDTO> searchEnabledUserOptions(Long tenantId, String keyword, int limit) {
        validateTenantId(tenantId);
        if (limit < 1 || limit > MAX_SEARCH_LIMIT) {
            throw new BusinessException(400, "limit 必须在 1 到 " + MAX_SEARCH_LIMIT + " 之间");
        }

        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(400, "搜索关键字长度不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }

        return sysUserMapper.searchEnabledUsers(tenantId, normalizedKeyword, limit).stream()
                .map(this::toUserOptionDTO)
                .toList();
    }

    /**
     * 规范化批量 ID 参数并执行规模限制。
     *
     * @param ids 原始 ID 列表
     * @return 去重且保持输入顺序的 ID 列表
     */
    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(400, "ID 列表包含无效值");
        }
        List<Long> normalizedIds = List.copyOf(new LinkedHashSet<>(ids));
        if (normalizedIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(400, "单次批量查询不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        return normalizedIds;
    }

    /**
     * 校验租户 ID。
     *
     * @param tenantId 租户 ID
     */
    private void validateTenantId(Long tenantId) {
        validateId(tenantId, "租户 ID");
    }

    /**
     * 校验正整数 ID。
     *
     * @param id        待校验 ID
     * @param fieldName 字段名称
     */
    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, fieldName + "必须为正整数");
        }
    }

    /**
     * 将用户实体转换为内部用户 DTO。
     *
     * @param user 用户实体
     * @return 内部用户 DTO
     */
    private InternalUserDTO toUserDTO(SysUser user) {
        InternalUserDTO dto = new InternalUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setTenantId(user.getTenantId());
        dto.setPrimaryUnitId(user.getPrimaryUnitId());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatar(user.getAvatar());
        dto.setStatus(user.getStatus());
        return dto;
    }

    /**
     * 将用户实体转换为最小化候选项 DTO。
     *
     * @param user 用户实体
     * @return 用户候选项 DTO
     */
    private InternalUserOptionDTO toUserOptionDTO(SysUser user) {
        InternalUserOptionDTO dto = new InternalUserOptionDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setTenantId(user.getTenantId());
        dto.setPrimaryUnitId(user.getPrimaryUnitId());
        dto.setAvatar(user.getAvatar());
        return dto;
    }

    /**
     * 将组织实体转换为内部组织 DTO。
     *
     * @param unit 组织单元实体
     * @return 内部组织 DTO
     */
    private InternalOrgDTO toOrgDTO(SysOrgUnit unit) {
        InternalOrgDTO dto = new InternalOrgDTO();
        dto.setId(unit.getId());
        dto.setTenantId(unit.getTenantId());
        dto.setParentId(unit.getParentId());
        dto.setName(unit.getName());
        dto.setType(unit.getType());
        dto.setUnitCode(unit.getUnitCode());
        dto.setPath(unit.getPath());
        dto.setStatus(unit.getStatus());
        return dto;
    }
}
