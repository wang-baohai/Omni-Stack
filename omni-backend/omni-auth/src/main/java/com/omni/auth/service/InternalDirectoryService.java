package com.omni.auth.service;

import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.internal.InternalUserOptionDTO;

import java.util.List;

/**
 * 内部目录查询服务。
 * <p>为受信任微服务提供强制租户隔离的用户、组织和负责人候选查询。</p>
 *
 * @author Omni-Stack Team
 */
public interface InternalDirectoryService {

    /**
     * 查询指定租户内的用户。
     *
     * @param id       用户 ID
     * @param tenantId 租户 ID
     * @return 用户信息，不存在或不属于该租户时返回 null
     */
    InternalUserDTO getUserById(Long id, Long tenantId);

    /**
     * 批量查询指定租户内的用户。
     *
     * @param ids      用户 ID 列表
     * @param tenantId 租户 ID
     * @return 匹配的用户列表
     */
    List<InternalUserDTO> getUsersByIds(List<Long> ids, Long tenantId);

    /**
     * 查询指定租户内的组织单元。
     *
     * @param id       组织单元 ID
     * @param tenantId 租户 ID
     * @return 组织信息，不存在或不属于该租户时返回 null
     */
    InternalOrgDTO getOrgById(Long id, Long tenantId);

    /**
     * 批量查询指定租户内的组织单元。
     *
     * @param ids      组织单元 ID 列表
     * @param tenantId 租户 ID
     * @return 匹配的组织单元列表
     */
    List<InternalOrgDTO> getOrgsByIds(List<Long> ids, Long tenantId);

    /**
     * 搜索指定租户内的启用用户候选项。
     *
     * @param tenantId 租户 ID
     * @param keyword  用户名或昵称关键字，可为空
     * @param limit    最大返回数量，范围为 1 到 100
     * @return 最小化用户候选项列表
     */
    List<InternalUserOptionDTO> searchEnabledUserOptions(Long tenantId, String keyword, int limit);
}
