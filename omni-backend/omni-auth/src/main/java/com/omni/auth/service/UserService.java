package com.omni.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.omni.auth.dto.CreateUserRequest;
import com.omni.auth.dto.RegisterRequest;
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
     * 验证明文密码是否与用户的 BCrypt 哈希匹配。
     *
     * @param rawPassword    明文密码
     * @param encodedPassword BCrypt 哈希值
     * @return 匹配返回 true，否则返回 false
     */
    boolean verifyPassword(String rawPassword, String encodedPassword);

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

    /**
     * 获取用户详情。
     *
     * @param id 用户 ID
     * @return 用户实体
     */
    SysUser getById(Long id);

    /**
     * 分配用户角色（全量替换）。
     *
     * @param userId    用户 ID
     * @param roleIds   角色 ID 列表
     * @param operator  操作人用户名
     * @param ipAddress 操作人 IP 地址
     */
    void assignRoles(Long userId, List<Long> roleIds, String operator, String ipAddress);

    /**
     * 获取用户已分配的角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> getUserRoleIds(Long userId);

    /**
     * 切换用户启用/禁用状态。
     *
     * @param userId    用户 ID
     * @param status    目标状态：1-启用, 0-禁用
     * @param operator  操作人用户名
     * @param ipAddress 操作人 IP 地址
     */
    void toggleStatus(Long userId, Integer status, String operator, String ipAddress);

    /**
     * 创建新用户（管理员操作）。
     * <p>对密码进行 BCrypt 编码，并自动分配默认 USER 角色。</p>
     *
     * @param request   创建用户请求
     * @param operator  操作人用户名
     * @param ipAddress 操作人 IP 地址
     * @return 创建的用户实体（含回填 ID）
     */
    SysUser createUser(CreateUserRequest request, String operator, String ipAddress);

    /**
     * 用户自助注册。
     * <p>校验验证码，对密码进行 BCrypt 编码，自动分配默认 USER 角色。</p>
     *
     * @param request 注册请求
     */
    void registerUser(RegisterRequest request);

    /**
     * 删除用户。
     *
     * @param id        用户 ID
     * @param operator  操作人用户名
     * @param ipAddress 操作人 IP 地址
     */
    void deleteUser(Long id, String operator, String ipAddress);
}
