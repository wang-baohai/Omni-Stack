package com.omni.procurement.service;

import com.omni.common.core.result.PageResult;
import com.omni.procurement.dto.MaterialRequests;
import com.omni.procurement.dto.MaterialViews;
import com.omni.procurement.entity.ProcMaterial;

import java.util.List;

/**
 * 物料目录服务。
 *
 * @author Omni-Stack Team
 */
public interface MaterialService {

    /**
     * 查询两级品类树。
     *
     * @return 品类树
     */
    List<MaterialViews.CategoryVO> listCategories();

    /**
     * 创建物料品类。
     *
     * @param request 创建请求
     * @return 新品类
     */
    MaterialViews.CategoryVO createCategory(MaterialRequests.CreateCategoryRequest request);

    /**
     * 更新物料品类。
     *
     * @param id 品类 ID
     * @param request 更新请求
     * @return 更新后品类
     */
    MaterialViews.CategoryVO updateCategory(Long id, MaterialRequests.UpdateCategoryRequest request);

    /**
     * 删除物料品类。
     *
     * @param id 品类 ID
     * @param version 乐观锁版本
     */
    void deleteCategory(Long id, Integer version);

    /**
     * 分页查询物料。
     *
     * @param query 查询条件
     * @return 物料分页
     */
    PageResult<MaterialViews.MaterialVO> page(MaterialRequests.MaterialQuery query);

    /**
     * 查询物料详情。
     *
     * @param id 物料 ID
     * @return 物料详情
     */
    MaterialViews.MaterialVO get(Long id);

    /**
     * 创建物料。
     *
     * @param request 创建请求
     * @return 新物料
     */
    MaterialViews.MaterialVO create(MaterialRequests.CreateMaterialRequest request);

    /**
     * 更新物料。
     *
     * @param id 物料 ID
     * @param request 更新请求
     * @return 更新后物料
     */
    MaterialViews.MaterialVO update(Long id, MaterialRequests.UpdateMaterialRequest request);

    /**
     * 删除物料。
     *
     * @param id 物料 ID
     * @param version 乐观锁版本
     */
    void delete(Long id, Integer version);

    /**
     * 查询并校验可用于请购的活动物料。
     *
     * @param id 物料 ID
     * @return 活动物料实体
     */
    ProcMaterial requireActiveForRequisition(Long id);
}
