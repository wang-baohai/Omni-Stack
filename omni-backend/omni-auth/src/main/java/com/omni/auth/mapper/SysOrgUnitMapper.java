package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysOrgUnit;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组织单元 Mapper 接口。
 * <p>提供 {@code sys_org_unit} 表的 CRUD 操作，以及基于物化路径的子树查询。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysOrgUnit
 */
public interface SysOrgUnitMapper extends BaseMapper<SysOrgUnit> {

    /**
     * 根据组织单元 ID 和租户 ID 查询组织单元。
     *
     * @param id       组织单元 ID
     * @param tenantId 租户 ID
     * @return 匹配的组织单元，不存在时返回 null
     */
    @Select("SELECT * FROM sys_org_unit WHERE id = #{id} AND tenant_id = #{tenantId}")
    SysOrgUnit selectByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 在指定租户内批量查询组织单元。
     *
     * @param ids      组织单元 ID 列表
     * @param tenantId 租户 ID
     * @return 匹配的组织单元列表
     */
    @Select("<script>"
            + "SELECT * FROM sys_org_unit WHERE tenant_id = #{tenantId} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<SysOrgUnit> selectByIdsAndTenantId(@Param("ids") List<Long> ids, @Param("tenantId") Long tenantId);

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

    /**
     * 在指定租户内根据物化路径查询启用组织单元 ID。
     *
     * @param tenantId  租户 ID
     * @param pathPrefix 路径前缀
     * @return 当前节点及其启用后代节点 ID
     */
    @Select("SELECT id FROM sys_org_unit "
            + "WHERE tenant_id = #{tenantId} "
            + "AND path LIKE CONCAT(#{pathPrefix}, '%') AND status = 1")
    List<Long> selectDescendantIdsByTenantIdAndPath(@Param("tenantId") Long tenantId,
                                                    @Param("pathPrefix") String pathPrefix);
}
