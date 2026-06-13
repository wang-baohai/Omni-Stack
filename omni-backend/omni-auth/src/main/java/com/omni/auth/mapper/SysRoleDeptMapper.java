package com.omni.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色数据范围关联表 Mapper 接口。
 * <p>操作 {@code sys_role_dept} 关联表，管理角色在 CUSTOM 数据范围下的部门权限。</p>
 */
public interface SysRoleDeptMapper {

    /**
     * 批量插入角色部门关联。
     *
     * @param roleId  角色 ID
     * @param deptIds 部门 ID 列表
     */
    @Insert("<script>"
            + "INSERT INTO sys_role_dept (role_id, dept_id) VALUES "
            + "<foreach collection='deptIds' item='deptId' separator=','>"
            + "(#{roleId}, #{deptId})"
            + "</foreach>"
            + "</script>")
    void batchInsert(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);

    /**
     * 删除角色的所有部门关联。
     *
     * @param roleId 角色 ID
     */
    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色关联的所有部门 ID。
     *
     * @param roleId 角色 ID
     * @return 部门 ID 列表
     */
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
