package com.omni.auth.service;

import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.dto.TenantOption;
import com.omni.auth.dto.UpdateTenantRequest;
import com.omni.auth.entity.SysTenant;
import com.omni.common.core.result.PageResult;

import java.util.List;

/**
 * 租户服务接口，提供租户相关的查询和管理操作。
 * <p>支持活跃租户列表查询（登录页用）和完整的租户 CRUD 管理。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.dto.TenantOption
 * @see com.omni.auth.entity.SysTenant
 */
public interface TenantService {

    /**
     * 查询所有活跃租户列表。
     *
     * @return 租户选项列表，供登录页租户选择器使用
     */
    List<TenantOption> listActiveTenants();

    /**
     * 校验租户是否允许登录。
     *
     * @param tenantId 租户 ID
     */
    void requireLoginAvailable(Long tenantId);

    /**
     * 分页查询租户列表。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 租户分页结果
     */
    PageResult<SysTenant> listTenants(int page, int size);

    /**
     * 获取租户详情。
     *
     * @param id 租户 ID
     * @return 租户实体
     */
    SysTenant getById(Long id);

    /**
     * 创建租户。
     *
     * @param request 创建请求
     * @return 创建的租户
     */
    SysTenant createTenant(CreateTenantRequest request);

    /**
     * 更新租户。
     *
     * @param id      租户 ID
     * @param request 更新请求
     * @return 更新后的租户
     */
    SysTenant updateTenant(Long id, UpdateTenantRequest request);

    /**
     * 删除租户。
     *
     * @param id 租户 ID
     */
    void deleteTenant(Long id);
}
