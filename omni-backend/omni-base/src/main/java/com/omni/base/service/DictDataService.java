package com.omni.base.service;

import com.omni.base.dto.CreateDictDataRequest;
import com.omni.base.dto.UpdateDictDataRequest;
import com.omni.base.entity.SysDictData;
import com.omni.common.core.result.PageResult;

import java.util.List;

/**
 * 字典数据服务接口。
 *
 * @author Omni-Stack Team
 */
public interface DictDataService {

    /**
     * 按字典类型编码查询字典数据列表（分页）。
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<SysDictData> listDataByTypeCode(Long tenantId, String typeCode, int page, int size);

    /**
     * 按字典类型编码查询已启用的字典数据（用于缓存）。
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     * @return 启用的字典数据列表
     */
    List<SysDictData> listEnabledData(Long tenantId, String typeCode);

    /**
     * 创建字典数据。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @param operator 操作人
     * @return 创建的实体
     */
    SysDictData createData(Long tenantId, CreateDictDataRequest request, String operator);

    /**
     * 更新字典数据。
     *
     * @param id       字典数据 ID
     * @param request  更新请求
     * @param operator 操作人
     * @return 更新后的实体
     */
    SysDictData updateData(Long id, UpdateDictDataRequest request, String operator);

    /**
     * 删除字典数据。
     *
     * @param id 字典数据 ID
     */
    void deleteData(Long id);

    /**
     * 手动刷新指定类型编码的 Redis 缓存。
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     */
    void refreshCache(Long tenantId, String typeCode);
}
