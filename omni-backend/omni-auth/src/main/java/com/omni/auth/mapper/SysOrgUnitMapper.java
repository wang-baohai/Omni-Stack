package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysOrgUnit;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组织单元 Mapper 接口。
 */
public interface SysOrgUnitMapper extends BaseMapper<SysOrgUnit> {

    /**
     * 根据物化路径前缀查询所有后代节点。
     *
     * @param pathPrefix 路径前缀
     * @return 满足条件的组织单元列表
     */
    @Select("SELECT * FROM sys_org_unit WHERE path LIKE CONCAT(#{pathPrefix}, '%') AND status = 1")
    List<SysOrgUnit> selectDescendantsByPath(@Param("pathPrefix") String pathPrefix);

    /**
     * 根据物化路径前缀查询所有后代节点的 ID。
     * <p>仅返回 ID 列表，避免加载完整实体，用于数据权限范围计算。</p>
     *
     * @param pathPrefix 路径前缀
     * @return 满足条件的组织单元 ID 列表
     */
    @Select("SELECT id FROM sys_org_unit WHERE path LIKE CONCAT(#{pathPrefix}, '%') AND status = 1")
    List<Long> selectDescendantIdsByPath(@Param("pathPrefix") String pathPrefix);
}
