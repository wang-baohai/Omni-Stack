package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysOrgUnit;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SysOrgUnit Mapper.
 */
public interface SysOrgUnitMapper extends BaseMapper<SysOrgUnit> {

    /**
     * Find all descendants by path prefix.
     */
    @Select("SELECT * FROM sys_org_unit WHERE path LIKE CONCAT(#{pathPrefix}, '%') AND status = 1")
    List<SysOrgUnit> selectDescendantsByPath(@Param("pathPrefix") String pathPrefix);
}
