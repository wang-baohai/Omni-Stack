package com.omni.base.controller;

import com.omni.base.dto.CreateUserJobTypeRequest;
import com.omni.base.dto.UpdateUserJobTypeRequest;
import com.omni.base.dto.UserJobTypeQuery;
import com.omni.base.entity.SysUserJobType;
import com.omni.base.service.UserJobTypeService;
import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 任务类型管理控制器。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.UserJobTypeService
 */
@RestController
@RequestMapping("/api/job/user-job-type")
@RequiredArgsConstructor
public class UserJobTypeController {

    private final UserJobTypeService userJobTypeService;

    /**
     * 分页查询任务类型列表。
     *
     * @param typeCode 类型编码（模糊匹配，可选）
     * @param typeName 类型名称（模糊匹配，可选）
     * @param status   状态（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @OperLog(module = "任务类型管理", operType = OperType.QUERY)
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('job:user-job-type:list')")
    public R<PageResult<SysUserJobType>> list(
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String typeName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UserJobTypeQuery query = new UserJobTypeQuery();
        query.setTypeCode(typeCode);
        query.setTypeName(typeName);
        query.setStatus(status);
        return R.ok(userJobTypeService.listTypes(query, page, size));
    }

    /**
     * 按 ID 查询任务类型详情。
     *
     * @param id 任务类型 ID
     * @return 任务类型实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('job:user-job-type:list')")
    public R<SysUserJobType> getById(@PathVariable Long id) {
        return R.ok(userJobTypeService.getTypeById(id));
    }

    /**
     * 查询所有启用类型（供前端下拉）。
     *
     * @return 启用状态的任务类型列表
     */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('job:user-job-type:list')")
    public R<List<SysUserJobType>> listEnabledTypes() {
        return R.ok(userJobTypeService.listEnabledTypes());
    }

    /**
     * 创建任务类型。
     *
     * @param request 创建请求
     * @return 创建成功后的任务类型实体
     */
    @OperLog(module = "任务类型管理", operType = OperType.CREATE)
    @PostMapping
    @PreAuthorize("hasAuthority('job:user-job-type:create')")
    public R<SysUserJobType> create(@Valid @RequestBody CreateUserJobTypeRequest request) {
        return R.ok(userJobTypeService.createType(request));
    }

    /**
     * 更新任务类型。
     *
     * @param id      任务类型 ID
     * @param request 更新请求
     * @return 更新后的任务类型实体
     */
    @OperLog(module = "任务类型管理", operType = OperType.UPDATE, entityClass = SysUserJobType.class, idExpr = "#id")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('job:user-job-type:update')")
    public R<SysUserJobType> update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserJobTypeRequest request) {
        return R.ok(userJobTypeService.updateType(id, request));
    }

    /**
     * 删除任务类型。
     *
     * @param id 任务类型 ID
     * @return 空结果
     */
    @OperLog(module = "任务类型管理", operType = OperType.DELETE)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('job:user-job-type:delete')")
    public R<Void> delete(@PathVariable Long id) {
        userJobTypeService.deleteType(id);
        return R.ok();
    }

    /**
     * 切换任务类型状态。
     *
     * @param id     任务类型 ID
     * @param status 目标状态（1=启用，0=禁用）
     * @return 空结果
     */
    @OperLog(module = "任务类型管理", operType = OperType.UPDATE, entityClass = SysUserJobType.class, idExpr = "#id")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('job:user-job-type:update')")
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestParam Integer status) {
        userJobTypeService.toggleStatus(id, status);
        return R.ok();
    }
}
