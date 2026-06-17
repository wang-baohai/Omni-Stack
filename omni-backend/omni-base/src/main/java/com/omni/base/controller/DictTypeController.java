package com.omni.base.controller;

import com.omni.base.dto.CreateDictTypeRequest;
import com.omni.base.dto.DictTypeQuery;
import com.omni.base.dto.UpdateDictTypeRequest;
import com.omni.base.entity.SysDictType;
import com.omni.base.service.DictTypeService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典类型控制器。
 * <p>提供字典类型的增删改查接口，路径映射 {@code /api/base/dict/type}。</p>
 *
 * @author Omni-Stack Team
 * @see DictTypeService
 */
@Slf4j
@RestController
@RequestMapping("/api/base/dict/type")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    /**
     * 分页查询字典类型列表。
     *
     * @param tenantId 租户 ID
     * @param typeCode 类型编码（模糊匹配，可选）
     * @param typeName 类型名称（模糊匹配，可选）
     * @param status   状态过滤（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('dict:type:list')")
    public R<PageResult<SysDictType>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String typeName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        DictTypeQuery query = new DictTypeQuery();
        query.setTypeCode(typeCode);
        query.setTypeName(typeName);
        query.setStatus(status);
        return R.ok(dictTypeService.listTypes(tenantId, query, page, size));
    }

    /**
     * 按 ID 查询字典类型。
     *
     * @param id 字典类型 ID
     * @return 字典类型实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:type:list')")
    public R<SysDictType> getById(@PathVariable Long id) {
        return R.ok(dictTypeService.getTypeById(id));
    }

    /**
     * 创建字典类型。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @return 创建的实体
     */
    @PostMapping
    @PreAuthorize("hasAuthority('dict:type:create')")
    public R<SysDictType> create(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @Valid @RequestBody CreateDictTypeRequest request) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(dictTypeService.createType(tenantId, request, operator));
    }

    /**
     * 更新字典类型。
     *
     * @param id      字典类型 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:type:update')")
    public R<SysDictType> update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateDictTypeRequest request) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(dictTypeService.updateType(id, request, operator));
    }

    /**
     * 删除字典类型（级联删除关联的字典数据）。
     *
     * @param id 字典类型 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:type:delete')")
    public R<Void> delete(@PathVariable Long id) {
        dictTypeService.deleteType(id);
        return R.ok();
    }

    /**
     * 切换字典类型启用状态。
     *
     * @param id     字典类型 ID
     * @param status 目标状态
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('dict:type:update')")
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestParam Integer status) {
        dictTypeService.toggleStatus(id, status);
        return R.ok();
    }
}
