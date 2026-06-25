package com.omni.auth.service;

import com.omni.auth.dto.CreateOrgUnitRequest;
import com.omni.auth.dto.UpdateOrgUnitRequest;
import com.omni.auth.entity.SysOrgUnit;

import java.util.List;

/**
 * 组织单元服务接口，提供组织树 CRUD 操作。
 * <p>基于物化路径实现多级组织架构管理，支持树形查询、创建、更新和删除。</p>
 *
 * @author Omni-Stack Team
 * @see OrgUnitTreeNode
 * @see com.omni.auth.entity.SysOrgUnit
 */
public interface OrgUnitService {

    /**
     * 获取指定租户的组织树。
     *
     * @param tenantId 租户 ID
     * @return 组织树形列表（仅包含顶级节点，子节点嵌套在 children 中）
     */
    List<OrgUnitTreeNode> getOrgTree(Long tenantId);

    /**
     * 获取组织单元详情。
     *
     * @param id 组织单元 ID
     * @return 组织单元实体
     */
    SysOrgUnit getById(Long id);

    /**
     * 创建组织单元。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @return 创建的组织单元
     */
    SysOrgUnit createOrgUnit(Long tenantId, CreateOrgUnitRequest request);

    /**
     * 更新组织单元。
     *
     * @param id      组织单元 ID
     * @param request 更新请求
     * @return 更新后的组织单元
     */
    SysOrgUnit updateOrgUnit(Long id, UpdateOrgUnitRequest request);

    /**
     * 删除组织单元及其所有后代节点。
     *
     * @param id 组织单元 ID
     */
    void deleteOrgUnit(Long id);

    /**
     * 获取指定组织单元的所有后代节点。
     *
     * @param unitId 组织单元 ID
     * @return 后代组织单元列表
     */
    List<SysOrgUnit> getDescendants(Long unitId);
}
