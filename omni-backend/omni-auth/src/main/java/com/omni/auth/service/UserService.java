package com.omni.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.omni.auth.entity.SysUser;
import com.omni.common.core.result.PageResult;

import java.util.List;

/**
 * 用户服务接口，定义用户相关的业务操作。
 */
public interface UserService extends IService<SysUser> {

    /**
     * 使用用户名和密码进行认证。
     *
     * @param username 用户名
     * @param password 明文密码
     * @param tenantId 租户 ID
     * @return 认证成功返回用户实体，失败返回 null
     */
    SysUser authenticate(String username, String password, Long tenantId);

    /**
     * 在指定租户内根据用户名查询用户。
     *
     * @param username 用户名
     * @param tenantId 租户 ID
     * @return 用户实体，不存在返回 null
     */
    SysUser findByUsername(String username, Long tenantId);

    /**
     * 获取用户的角色编码列表。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    List<String> getUserRoles(Long userId);

    /**
     * 获取用户的权限编码列表。
     *
     * @param userId 用户 ID
     * @return 权限编码列表
     */
    List<String> getUserPermissions(Long userId);

    /**
     * 分页查询指定租户下的用户列表。
     *
     * @param tenantId 租户 ID
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @return 分页用户列表
     */
    PageResult<SysUser> listUsers(Long tenantId, int page, int size);
}
