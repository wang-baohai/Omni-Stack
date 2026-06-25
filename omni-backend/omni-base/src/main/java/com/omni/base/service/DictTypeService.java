package com.omni.base.service;

import com.omni.base.dto.CreateDictTypeRequest;
import com.omni.base.dto.DictTypeQuery;
import com.omni.base.dto.UpdateDictTypeRequest;
import com.omni.base.entity.SysDictType;
import com.omni.common.core.result.PageResult;

/**
 * 字典类型服务接口。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.impl.DictTypeServiceImpl
 */
public interface DictTypeService {

    /**
     * 分页查询字典类型列表。
     *
     * @param tenantId 租户 ID
     * @param query    查询条件
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<SysDictType> listTypes(Long tenantId, DictTypeQuery query, int page, int size);

    /**
     * 按 ID 查询字典类型。
     *
     * @param id 字典类型 ID
     * @return 字典类型实体
     */
    SysDictType getTypeById(Long id);

    /**
     * 创建字典类型。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @param operator 操作人
     * @return 创建的实体
     */
    SysDictType createType(Long tenantId, CreateDictTypeRequest request, String operator);

    /**
     * 更新字典类型。
     *
     * @param id       字典类型 ID
     * @param request  更新请求
     * @param operator 操作人
     * @return 更新后的实体
     */
    SysDictType updateType(Long id, UpdateDictTypeRequest request, String operator);

    /**
     * 删除字典类型（级联删除关联的字典数据）。
     *
     * @param id 字典类型 ID
     */
    void deleteType(Long id);

    /**
     * 切换字典类型启用状态。
     *
     * @param id     字典类型 ID
     * @param status 目标状态
     */
    void toggleStatus(Long id, Integer status);
}
