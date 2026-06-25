package com.omni.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户组织关联表 Mapper 接口。
 * <p>操作 {@code sys_user_unit} 关联表，管理用户与组织单元的多对多关系。
 * 一个用户可关联多个组织单元，其中 {@code is_primary = 1} 的为主组织。</p>
 *
 * @author Omni-Stack Team
 * @see SysOrgUnitMapper
 */
public interface SysUserUnitMapper {

    /**
     * 插入用户组织关联。
     *
     * @param userId    用户 ID
     * @param unitId    组织单元 ID
     * @param isPrimary 是否主组织（0-否，1-是）
     */
    @Insert("INSERT INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (#{userId}, #{unitId}, #{isPrimary})")
    void insert(@Param("userId") Long userId, @Param("unitId") Long unitId, @Param("isPrimary") Integer isPrimary);

    /**
     * 删除用户的所有组织关联。
     *
     * @param userId 用户 ID
     */
    @Delete("DELETE FROM sys_user_unit WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 查询用户关联的所有组织单元 ID。
     *
     * @param userId 用户 ID
     * @return 组织单元 ID 列表
     */
    @Select("SELECT unit_id FROM sys_user_unit WHERE user_id = #{userId}")
    List<Long> selectUnitIdsByUserId(@Param("userId") Long userId);
}
