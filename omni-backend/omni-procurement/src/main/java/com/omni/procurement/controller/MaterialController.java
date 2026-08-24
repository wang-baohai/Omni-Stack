package com.omni.procurement.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.MaterialRequests;
import com.omni.procurement.dto.MaterialViews;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.procurement.service.MaterialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料目录控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    /**
     * 查询两级品类树。
     *
     * @return 品类树
     */
    @GetMapping("/category/list")
    @PreAuthorize("hasAuthority('procurement:material:list')")
    @ServiceDataScope(permissionCode = "procurement:material:list")
    public R<List<MaterialViews.CategoryVO>> listCategories() {
        return R.ok(materialService.listCategories());
    }

    /**
     * 创建品类。
     *
     * @param request 创建请求
     * @return 新品类
     */
    @PostMapping("/category")
    @PreAuthorize("hasAuthority('procurement:material:create')")
    @ServiceDataScope(permissionCode = "procurement:material:create")
    @OperLog(module = "采购物料品类", operType = OperType.CREATE,
            entityClass = ProcMaterialCategory.class, idExpr = "#result.data.id")
    public R<MaterialViews.CategoryVO> createCategory(
            @Valid @RequestBody MaterialRequests.CreateCategoryRequest request) {
        return R.ok(materialService.createCategory(request));
    }

    /**
     * 更新品类。
     *
     * @param id 品类 ID
     * @param request 更新请求
     * @return 更新后品类
     */
    @PutMapping("/category/{id}")
    @PreAuthorize("hasAuthority('procurement:material:update')")
    @ServiceDataScope(permissionCode = "procurement:material:update")
    @OperLog(module = "采购物料品类", operType = OperType.UPDATE,
            entityClass = ProcMaterialCategory.class, idExpr = "#id")
    public R<MaterialViews.CategoryVO> updateCategory(
            @PathVariable Long id, @Valid @RequestBody MaterialRequests.UpdateCategoryRequest request) {
        return R.ok(materialService.updateCategory(id, request));
    }

    /**
     * 删除品类。
     *
     * @param id 品类 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/category/{id}")
    @PreAuthorize("hasAuthority('procurement:material:delete')")
    @ServiceDataScope(permissionCode = "procurement:material:delete")
    @OperLog(module = "采购物料品类", operType = OperType.DELETE,
            entityClass = ProcMaterialCategory.class, idExpr = "#id")
    public R<Void> deleteCategory(@PathVariable Long id,
                                  @RequestParam @Min(value = 0, message = "乐观锁版本不能小于 0") Integer version) {
        materialService.deleteCategory(id, version);
        return R.ok();
    }

    /**
     * 分页查询物料。
     *
     * @param query 查询条件
     * @return 物料分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('procurement:material:list')")
    @ServiceDataScope(permissionCode = "procurement:material:list")
    public R<PageResult<MaterialViews.MaterialVO>> list(@Valid MaterialRequests.MaterialQuery query) {
        return R.ok(materialService.page(query));
    }

    /**
     * 查询物料详情。
     *
     * @param id 物料 ID
     * @return 物料详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:material:list')")
    @ServiceDataScope(permissionCode = "procurement:material:list")
    public R<MaterialViews.MaterialVO> get(@PathVariable Long id) {
        return R.ok(materialService.get(id));
    }

    /**
     * 创建物料。
     *
     * @param request 创建请求
     * @return 新物料
     */
    @PostMapping
    @PreAuthorize("hasAuthority('procurement:material:create')")
    @ServiceDataScope(permissionCode = "procurement:material:create")
    @OperLog(module = "采购物料", operType = OperType.CREATE,
            entityClass = ProcMaterial.class, idExpr = "#result.data.id")
    public R<MaterialViews.MaterialVO> create(
            @Valid @RequestBody MaterialRequests.CreateMaterialRequest request) {
        return R.ok(materialService.create(request));
    }

    /**
     * 更新物料，物料编码保持不变。
     *
     * @param id 物料 ID
     * @param request 更新请求
     * @return 更新后物料
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:material:update')")
    @ServiceDataScope(permissionCode = "procurement:material:update")
    @OperLog(module = "采购物料", operType = OperType.UPDATE,
            entityClass = ProcMaterial.class, idExpr = "#id")
    public R<MaterialViews.MaterialVO> update(
            @PathVariable Long id, @Valid @RequestBody MaterialRequests.UpdateMaterialRequest request) {
        return R.ok(materialService.update(id, request));
    }

    /**
     * 删除物料。
     *
     * @param id 物料 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:material:delete')")
    @ServiceDataScope(permissionCode = "procurement:material:delete")
    @OperLog(module = "采购物料", operType = OperType.DELETE,
            entityClass = ProcMaterial.class, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id,
                          @RequestParam @Min(value = 0, message = "乐观锁版本不能小于 0") Integer version) {
        materialService.delete(id, version);
        return R.ok();
    }
}
