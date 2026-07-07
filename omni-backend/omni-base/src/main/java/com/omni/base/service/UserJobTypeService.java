package com.omni.base.service;

import com.omni.base.dto.CreateUserJobTypeRequest;
import com.omni.base.dto.UpdateUserJobTypeRequest;
import com.omni.base.dto.UserJobTypeQuery;
import com.omni.base.entity.SysUserJobType;
import com.omni.common.core.result.PageResult;

import java.util.List;

/**
 * 任务类型服务接口。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.impl.UserJobTypeServiceImpl
 */
public interface UserJobTypeService {

    /**
     * 分页查询任务类型列表。
     *
     * @param query 查询条件（编码、名称、状态）
     * @param page  页码
     * @param size  每页大小
     * @return 分页结果
     */
    PageResult<SysUserJobType> listTypes(UserJobTypeQuery query, int page, int size);

    /**
     * 按 ID 查询任务类型。
     *
     * @param id 任务类型 ID
     * @return 任务类型实体
     * @throws com.omni.common.core.result.BusinessException 类型不存在时抛出 404
     */
    SysUserJobType getTypeById(Long id);

    /**
     * 查询所有启用类型的列表（供前端下拉）。
     *
     * @return 启用状态的任务类型列表
     */
    List<SysUserJobType> listEnabledTypes();

    /**
     * 按类型编码查询启用状态的任务类型。
     *
     * @param typeCode 类型编码
     * @return 启用状态的任务类型，不存在或已禁用时返回 null
     */
    SysUserJobType getEnabledTypeByCode(String typeCode);

    /**
     * 创建任务类型。
     *
     * @param request 创建请求
     * @return 创建成功后的任务类型实体
     * @throws com.omni.common.core.result.BusinessException 编码已存在时抛出 400
     */
    SysUserJobType createType(CreateUserJobTypeRequest request);

    /**
     * 更新任务类型。
     *
     * @param id      任务类型 ID
     * @param request 更新请求
     * @return 更新后的任务类型实体
     */
    SysUserJobType updateType(Long id, UpdateUserJobTypeRequest request);

    /**
     * 删除任务类型。
     *
     * @param id 任务类型 ID
     */
    void deleteType(Long id);

    /**
     * 切换任务类型状态。
     *
     * @param id     任务类型 ID
     * @param status 目标状态（1=启用，0=禁用）
     */
    void toggleStatus(Long id, Integer status);
}
