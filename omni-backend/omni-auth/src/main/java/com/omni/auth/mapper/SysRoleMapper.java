package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper 接口。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户 ID 查询该用户拥有的所有角色。
     * <p>通过 {@code sys_user_role} 关联表进行 JOIN 查询，仅返回启用状态的角色。</p>
     *
     * @param userId 用户 ID
     * @return 用户角色列表
     */
    @Select("SELECT r.* FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
}
