package com.omni.workflow.service;

import com.omni.workflow.dto.*;

import java.util.List;

/**
 * 工作流身份查询服务接口。
 * <p>
 * 通过 JdbcTemplate 跨库查询 omni_auth，为流程设计器提供用户、角色、组织等身份数据。</p>
 *
 * @author Omni-Stack Team
 */
public interface WorkflowIdentityService {

    /**
     * 查询用户列表（支持关键字搜索）。
     *
     * @param tenantId 租户 ID
     * @param keyword  关键字（用户名/昵称，可选）
     * @return 用户列表
     */
    List<IdentityUserVO> listUsers(Long tenantId, String keyword);

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户 ID
     * @return 角色列表
     */
    List<IdentityRoleVO> listRoles(Long tenantId);

    /**
     * 获取组织架构树。
     *
     * @param tenantId 租户 ID
     * @return 组织树节点列表
     */
    List<OrgTreeNodeVO> getOrgTree(Long tenantId);

    /**
     * 获取组织单元下拉选项（扁平列表）。
     *
     * @param tenantId 租户 ID
     * @return 组织树节点列表（不含 children）
     */
    List<OrgTreeNodeVO> getUnitOptions(Long tenantId);

    /**
     * 模拟解析审批候选人（设计时预览）。
     *
     * @param request  解析请求
     * @param tenantId 租户 ID
     * @return 解析结果
     */
    ResolvePreviewResult resolvePreview(ResolvePreviewRequest request, Long tenantId);
}
