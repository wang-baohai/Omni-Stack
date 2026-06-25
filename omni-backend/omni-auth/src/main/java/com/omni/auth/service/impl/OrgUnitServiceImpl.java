package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.auth.dto.CreateOrgUnitRequest;
import com.omni.auth.dto.UpdateOrgUnitRequest;
import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.service.OrgUnitService;
import com.omni.auth.service.OrgUnitTreeNode;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织单元服务实现类。
 * <p>基于物化路径管理树形结构，支持 CRUD 和级联删除操作。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.service.OrgUnitService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgUnitServiceImpl implements OrgUnitService {

    private final SysOrgUnitMapper sysOrgUnitMapper;

    /**
     * {@inheritDoc}
     *
     * <p>查询租户全部组织单元，按 sort 排序后构建树形结构。</p>
     */
    @Override
    public List<OrgUnitTreeNode> getOrgTree(Long tenantId) {
        List<SysOrgUnit> all = sysOrgUnitMapper.selectList(
                new LambdaQueryWrapper<SysOrgUnit>()
                        .eq(SysOrgUnit::getTenantId, tenantId)
                        .orderByAsc(SysOrgUnit::getSort));
        return buildTree(all, 0L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysOrgUnit getById(Long id) {
        SysOrgUnit unit = sysOrgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BusinessException(404, "组织单元不存在");
        }
        return unit;
    }

    /**
     * {@inheritDoc}
     *
     * <p>根据父节点的物化路径自动计算新节点的 path 和 depth。</p>
     */
    @Override
    @Transactional
    public SysOrgUnit createOrgUnit(Long tenantId, CreateOrgUnitRequest request) {
        // 计算物化路径
        String parentPath;
        int depth;
        if (request.getParentId() == 0L) {
            parentPath = "/";
            depth = 1;
        } else {
            SysOrgUnit parent = sysOrgUnitMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException(404, "父级组织单元不存在");
            }
            parentPath = parent.getPath();
            depth = parent.getDepth() + 1;
        }

        SysOrgUnit unit = new SysOrgUnit();
        unit.setTenantId(tenantId);
        unit.setParentId(request.getParentId());
        unit.setName(request.getName());
        unit.setType(request.getType());
        unit.setSort(request.getSort() != null ? request.getSort() : 0);
        unit.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        // 先插入获取自增 ID，再更新 path；设置临时占位值以通过 NOT NULL 约束
        unit.setPath("/");
        unit.setDepth(depth);
        sysOrgUnitMapper.insert(unit);
        unit.setPath(parentPath + unit.getId() + "/");
        sysOrgUnitMapper.updateById(unit);

        log.info("已创建组织单元: {} (path={})", unit.getName(), unit.getPath());
        return unit;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅更新非 null 字段。</p>
     */
    @Override
    @Transactional
    public SysOrgUnit updateOrgUnit(Long id, UpdateOrgUnitRequest request) {
        SysOrgUnit unit = sysOrgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BusinessException(404, "组织单元不存在");
        }
        if (request.getName() != null) {
            unit.setName(request.getName());
        }
        if (request.getType() != null) {
            unit.setType(request.getType());
        }
        if (request.getSort() != null) {
            unit.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            unit.setStatus(request.getStatus());
        }
        sysOrgUnitMapper.updateById(unit);
        log.info("已更新组织单元: {}", unit.getName());
        return unit;
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过物化路径前缀匹配找到所有后代节点，按深度倒序删除（先叶子后父节点）。</p>
     */
    @Override
    @Transactional
    public void deleteOrgUnit(Long id) {
        SysOrgUnit unit = sysOrgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BusinessException(404, "组织单元不存在");
        }
        // 查找所有后代节点（包括自身）
        List<SysOrgUnit> descendants = sysOrgUnitMapper.selectDescendantsByPath(unit.getPath());
        // 按深度倒序删除，先删叶子节点
        descendants.sort(Comparator.comparing(SysOrgUnit::getDepth).reversed());
        for (SysOrgUnit desc : descendants) {
            sysOrgUnitMapper.deleteById(desc.getId());
        }
        log.info("已删除组织单元 {} 及其 {} 个后代节点", unit.getName(), descendants.size() - 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysOrgUnit> getDescendants(Long unitId) {
        SysOrgUnit unit = sysOrgUnitMapper.selectById(unitId);
        if (unit == null) {
            throw new BusinessException(404, "组织单元不存在");
        }
        return sysOrgUnitMapper.selectDescendantsByPath(unit.getPath());
    }

    /**
     * 递归构建组织树。
     *
     * @param all      全部组织单元记录
     * @param parentId 当前父级 ID
     * @return 树形节点列表
     */
    private List<OrgUnitTreeNode> buildTree(List<SysOrgUnit> all, Long parentId) {
        Map<Long, List<SysOrgUnit>> grouped = all.stream()
                .collect(Collectors.groupingBy(SysOrgUnit::getParentId));
        return buildChildren(grouped, parentId);
    }

    /**
     * 递归构建子节点。
     */
    private List<OrgUnitTreeNode> buildChildren(Map<Long, List<SysOrgUnit>> grouped, Long parentId) {
        List<SysOrgUnit> children = grouped.getOrDefault(parentId, new ArrayList<>());
        return children.stream()
                .map(u -> OrgUnitTreeNode.builder()
                        .id(u.getId())
                        .parentId(u.getParentId())
                        .name(u.getName())
                        .type(u.getType())
                        .path(u.getPath())
                        .depth(u.getDepth())
                        .sort(u.getSort())
                        .status(u.getStatus())
                        .children(buildChildren(grouped, u.getId()))
                        .build())
                .collect(Collectors.toList());
    }
}
