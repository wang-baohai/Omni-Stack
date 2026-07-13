package com.omni.crm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScope;
import com.omni.crm.service.ActivityService;
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

/** CRM 跟进活动控制器。 */
@RestController
@RequestMapping("/api/crm/activity")
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityService activityService;

    /** 分页查询活动。 */
    @GetMapping("/list") @PreAuthorize("hasAuthority('crm:activity:list')") @CrmDataScope(permissionCode = "crm:activity:list")
    public R<PageResult<CrmViews.ActivityVO>> list(@Valid CrmRequests.ActivityQuery query) {
        return R.ok(activityService.list(query));
    }

    /** 查询聚合根活动时间线。 */
    @GetMapping("/timeline") @PreAuthorize("hasAuthority('crm:activity:list')") @CrmDataScope(permissionCode = "crm:activity:list")
    public R<List<CrmViews.ActivityVO>> timeline(@RequestParam String rootType, @RequestParam Long rootId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return R.ok(activityService.timeline(rootType, rootId, limit));
    }

    /** 查询活动详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('crm:activity:list')") @CrmDataScope(permissionCode = "crm:activity:list")
    public R<CrmViews.ActivityVO> get(@PathVariable Long id) { return R.ok(activityService.get(id)); }

    /** 创建活动。 */
    @PostMapping @PreAuthorize("hasAuthority('crm:activity:create')") @CrmDataScope(permissionCode = "crm:activity:create")
    @OperLog(module = "CRM跟进活动", operType = OperType.CREATE)
    public R<CrmViews.ActivityVO> create(@Valid @RequestBody CrmRequests.CreateActivityRequest request) {
        return R.ok(activityService.create(request));
    }

    /** 更新活动。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('crm:activity:update')") @CrmDataScope(permissionCode = "crm:activity:update")
    @OperLog(module = "CRM跟进活动", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.ActivityVO> update(@PathVariable Long id,
                                         @Valid @RequestBody CrmRequests.UpdateActivityRequest request) {
        return R.ok(activityService.update(id, request));
    }

    /** 删除未完成活动。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('crm:activity:delete')") @CrmDataScope(permissionCode = "crm:activity:delete")
    @OperLog(module = "CRM跟进活动", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id, @RequestParam Integer version) { activityService.delete(id, version); return R.ok(); }

    /** 完成活动。 */
    @PostMapping("/{id}/complete") @PreAuthorize("hasAuthority('crm:activity:complete')")
    @CrmDataScope(permissionCode = "crm:activity:complete") @OperLog(module = "CRM跟进活动", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.ActivityVO> complete(@PathVariable Long id,
                                           @Valid @RequestBody CrmRequests.CompleteActivityRequest request) {
        return R.ok(activityService.complete(id, request));
    }

    /** 取消活动。 */
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('crm:activity:cancel')")
    @CrmDataScope(permissionCode = "crm:activity:cancel") @OperLog(module = "CRM跟进活动", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.ActivityVO> cancel(@PathVariable Long id,
                                         @Valid @RequestBody CrmRequests.CancelActivityRequest request) {
        return R.ok(activityService.cancel(id, request));
    }

    /** 重新计划已取消活动。 */
    @PostMapping("/{id}/reschedule") @PreAuthorize("hasAuthority('crm:activity:update')")
    @CrmDataScope(permissionCode = "crm:activity:update") @OperLog(module = "CRM跟进活动", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.ActivityVO> reschedule(@PathVariable Long id,
                                             @Valid @RequestBody CrmRequests.RescheduleActivityRequest request) {
        return R.ok(activityService.reschedule(id, request));
    }
}
