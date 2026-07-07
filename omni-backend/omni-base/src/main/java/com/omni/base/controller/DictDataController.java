package com.omni.base.controller;

import com.omni.base.dto.CreateDictDataRequest;
import com.omni.base.dto.DictOptionVO;
import com.omni.base.dto.UpdateDictDataRequest;
import com.omni.base.entity.SysDictData;
import com.omni.base.service.DictDataService;
import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
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

import java.util.List;

/**
 * 字典数据控制器。
 * <p>提供字典数据的增删改查和缓存刷新接口，路径映射 {@code /api/base/dict/data}。</p>
 *
 * @author Omni-Stack Team
 * @see DictDataService
 */
@Slf4j
@RestController
@RequestMapping("/api/base/dict/data")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    /**
     * 按字典类型编码获取字典选项列表（轻量，用于前端下拉组件）。
     * <p>无需特殊权限，仅需登录认证。返回已启用的字典数据，带 Redis 缓存。</p>
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     * @return 字典选项列表
     */
    @GetMapping("/options")
    public R<List<DictOptionVO>> options(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestParam String typeCode) {
        List<DictOptionVO> options = dictDataService.listEnabledData(tenantId, typeCode)
                .stream()
                .map(data -> new DictOptionVO(data.getDictValue(), data.getDictLabel()))
                .toList();
        return R.ok(options);
    }

    /**
     * 按字典类型编码分页查询字典数据列表。
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('dict:data:list')")
    public R<PageResult<SysDictData>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestParam String typeCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(dictDataService.listDataByTypeCode(tenantId, typeCode, page, size));
    }

    /**
     * 创建字典数据。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @return 创建的实体
     */
    @OperLog(module = "字典数据管理", operType = OperType.CREATE)
    @PostMapping
    @PreAuthorize("hasAuthority('dict:data:create')")
    public R<SysDictData> create(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @Valid @RequestBody CreateDictDataRequest request) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(dictDataService.createData(tenantId, request, operator));
    }

    /**
     * 更新字典数据。
     *
     * @param id      字典数据 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    @OperLog(module = "字典数据管理", operType = OperType.UPDATE, entityClass = SysDictData.class, idExpr = "#id")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:data:update')")
    public R<SysDictData> update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateDictDataRequest request) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(dictDataService.updateData(id, request, operator));
    }

    /**
     * 删除字典数据。
     *
     * @param id 字典数据 ID
     * @return 操作结果
     */
    @OperLog(module = "字典数据管理", operType = OperType.DELETE)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:data:delete')")
    public R<Void> delete(@PathVariable Long id) {
        dictDataService.deleteData(id);
        return R.ok();
    }

    /**
     * 手动刷新指定字典类型的 Redis 缓存。
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     * @return 操作结果
     */
    @OperLog(module = "字典数据管理", operType = OperType.UPDATE)
    @PostMapping("/refresh-cache")
    @PreAuthorize("hasAuthority('dict:data:refresh')")
    public R<Void> refreshCache(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestParam String typeCode) {
        dictDataService.refreshCache(tenantId, typeCode);
        return R.ok();
    }
}
