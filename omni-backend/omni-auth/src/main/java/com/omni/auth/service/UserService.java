package com.omni.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.omni.auth.entity.SysUser;
import com.omni.common.core.result.PageResult;

import java.util.List;

/**
 * SysUser Service.
 */
public interface UserService extends IService<SysUser> {

    /**
     * Authenticate user with username and password.
     */
    SysUser authenticate(String username, String password, Long tenantId);

    /**
     * Find user by username within a tenant.
     */
    SysUser findByUsername(String username, Long tenantId);

    /**
     * Get user roles.
     */
    List<String> getUserRoles(Long userId);

    /**
     * Get user permissions.
     */
    List<String> getUserPermissions(Long userId);

    /**
     * Paginated list of users in a tenant.
     */
    PageResult<SysUser> listUsers(Long tenantId, int page, int size);
}
