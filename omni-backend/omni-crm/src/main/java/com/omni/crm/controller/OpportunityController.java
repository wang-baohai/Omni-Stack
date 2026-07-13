package com.omni.crm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScope;
import com.omni.crm.service.OpportunityService;
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

/** CRM 商机控制器。 */
@RestController
@RequestMapping("/api/crm/opportunity")
@RequiredArgsConstructor
public class OpportunityController {
    private final OpportunityService opportunityService;

    /** 分页查询商机。 */
    @GetMapping("/list") @PreAuthorize("hasAuthority('crm:opportunity:list')") @CrmDataScope(permissionCode = "crm:opportunity:list")
    public R<PageResult<CrmViews.OpportunityVO>> list(@Valid CrmRequests.OpportunityQuery query) {
        return R.ok(opportunityService.list(query));
    }

    /** 查询商机看板。 */
    @GetMapping("/board") @PreAuthorize("hasAuthority('crm:opportunity:list')") @CrmDataScope(permissionCode = "crm:opportunity:list")
    public R<CrmViews.OpportunityBoardVO> board(@RequestParam(required = false) Long pipelineId,
                                                @Valid CrmRequests.OpportunityQuery query) {
        return R.ok(opportunityService.board(pipelineId, query));
    }

    /** 查询商机详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('crm:opportunity:list')") @CrmDataScope(permissionCode = "crm:opportunity:list")
    public R<CrmViews.OpportunityVO> get(@PathVariable Long id) { return R.ok(opportunityService.get(id)); }

    /** 查询商机阶段历史。 */
    @GetMapping("/{id}/stage-history") @PreAuthorize("hasAuthority('crm:opportunity:list')")
    @CrmDataScope(permissionCode = "crm:opportunity:list")
    public R<List<CrmViews.StageHistoryVO>> history(@PathVariable Long id) {
        return R.ok(opportunityService.stageHistory(id));
    }

    /** 创建商机。 */
    @PostMapping @PreAuthorize("hasAuthority('crm:opportunity:create')") @CrmDataScope(permissionCode = "crm:opportunity:create")
    @OperLog(module = "CRM商机", operType = OperType.CREATE, recordSnapshot = false, excludeFields = {"name"})
    public R<CrmViews.OpportunityVO> create(@Valid @RequestBody CrmRequests.CreateOpportunityRequest request) {
        return R.ok(opportunityService.create(request));
    }

    /** 更新商机。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('crm:opportunity:update')") @CrmDataScope(permissionCode = "crm:opportunity:update")
    @OperLog(module = "CRM商机", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"name"})
    public R<CrmViews.OpportunityVO> update(@PathVariable Long id,
                                            @Valid @RequestBody CrmRequests.UpdateOpportunityRequest request) {
        return R.ok(opportunityService.update(id, request));
    }

    /** 删除开放商机。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('crm:opportunity:delete')") @CrmDataScope(permissionCode = "crm:opportunity:delete")
    @OperLog(module = "CRM商机", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        opportunityService.delete(id, version); return R.ok();
    }

    /** 分配商机负责人。 */
    @PostMapping("/{id}/assign") @PreAuthorize("hasAuthority('crm:opportunity:assign')")
    @CrmDataScope(permissionCode = "crm:opportunity:assign") @OperLog(module = "CRM商机", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.OpportunityVO> assign(@PathVariable Long id, @Valid @RequestBody CrmRequests.AssignRequest request) {
        return R.ok(opportunityService.assign(id, request));
    }

    /** 迁移商机阶段。 */
    @PostMapping("/{id}/stage") @PreAuthorize("hasAuthority('crm:opportunity:stage')")
    @CrmDataScope(permissionCode = "crm:opportunity:stage") @OperLog(module = "CRM商机阶段", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.OpportunityVO> stage(@PathVariable Long id,
                                           @Valid @RequestBody CrmRequests.OpportunityStageRequest request) {
        return R.ok(opportunityService.changeStage(id, request));
    }

    /** 重开商机。 */
    @PostMapping("/{id}/reopen") @PreAuthorize("hasAuthority('crm:opportunity:reopen')")
    @CrmDataScope(permissionCode = "crm:opportunity:reopen") @OperLog(module = "CRM商机阶段", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.OpportunityVO> reopen(@PathVariable Long id, @Valid @RequestBody CrmRequests.VersionRequest request) {
        return R.ok(opportunityService.reopen(id, request));
    }
}
