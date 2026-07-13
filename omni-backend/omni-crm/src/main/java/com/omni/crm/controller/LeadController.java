package com.omni.crm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScope;
import com.omni.crm.service.LeadService;
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

/** CRM 线索控制器。 */
@RestController
@RequestMapping("/api/crm/lead")
@RequiredArgsConstructor
public class LeadController {
    private final LeadService leadService;

    /** 分页查询线索。 */
    @GetMapping("/list") @PreAuthorize("hasAuthority('crm:lead:list')") @CrmDataScope(permissionCode = "crm:lead:list")
    public R<PageResult<CrmViews.LeadVO>> list(@Valid CrmRequests.LeadQuery query) { return R.ok(leadService.list(query)); }

    /** 查询线索详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('crm:lead:list')") @CrmDataScope(permissionCode = "crm:lead:list")
    public R<CrmViews.LeadVO> get(@PathVariable Long id) { return R.ok(leadService.get(id)); }

    /** 创建线索。 */
    @PostMapping @PreAuthorize("hasAuthority('crm:lead:create')") @CrmDataScope(permissionCode = "crm:lead:create")
    @OperLog(module = "CRM线索", operType = OperType.CREATE)
    public R<CrmViews.LeadVO> create(@Valid @RequestBody CrmRequests.CreateLeadRequest request) {
        return R.ok(leadService.create(request));
    }

    /** 更新线索。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('crm:lead:update')") @CrmDataScope(permissionCode = "crm:lead:update")
    @OperLog(module = "CRM线索", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.LeadVO> update(@PathVariable Long id, @Valid @RequestBody CrmRequests.UpdateLeadRequest request) {
        return R.ok(leadService.update(id, request));
    }

    /** 删除线索。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('crm:lead:delete')") @CrmDataScope(permissionCode = "crm:lead:delete")
    @OperLog(module = "CRM线索", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        CrmRequests.VersionRequest request = new CrmRequests.VersionRequest(); request.setVersion(version);
        leadService.delete(id, request); return R.ok();
    }

    /** 检查重复候选。 */
    @PostMapping("/duplicate-check") @PreAuthorize("hasAuthority('crm:lead:list')") @CrmDataScope(permissionCode = "crm:lead:list")
    public R<List<CrmViews.DuplicateCandidateVO>> duplicate(@Valid @RequestBody CrmRequests.LeadDuplicateRequest request) {
        return R.ok(leadService.duplicateCheck(request));
    }

    /** 分配线索负责人。 */
    @PostMapping("/{id}/assign") @PreAuthorize("hasAuthority('crm:lead:assign')") @CrmDataScope(permissionCode = "crm:lead:assign")
    @OperLog(module = "CRM线索", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.LeadVO> assign(@PathVariable Long id, @Valid @RequestBody CrmRequests.AssignRequest request) {
        return R.ok(leadService.assign(id, request));
    }

    /** 批量分配线索负责人。 */
    @PostMapping("/batch-assign") @PreAuthorize("hasAuthority('crm:lead:assign')") @CrmDataScope(permissionCode = "crm:lead:assign")
    @OperLog(module = "CRM线索", operType = OperType.UPDATE)
    public R<List<CrmViews.LeadVO>> batchAssign(@Valid @RequestBody CrmRequests.BatchAssignRequest request) {
        return R.ok(leadService.batchAssign(request));
    }

    /** 标记线索合格。 */
    @PostMapping("/{id}/qualify") @PreAuthorize("hasAuthority('crm:lead:update')") @CrmDataScope(permissionCode = "crm:lead:update")
    @OperLog(module = "CRM线索", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.LeadVO> qualify(@PathVariable Long id, @Valid @RequestBody CrmRequests.VersionRequest request) {
        return R.ok(leadService.qualify(id, request));
    }

    /** 判定线索无效。 */
    @PostMapping("/{id}/disqualify") @PreAuthorize("hasAuthority('crm:lead:disqualify')") @CrmDataScope(permissionCode = "crm:lead:disqualify")
    @OperLog(module = "CRM线索", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.LeadVO> disqualify(@PathVariable Long id,
                                         @Valid @RequestBody CrmRequests.DisqualifyLeadRequest request) {
        return R.ok(leadService.disqualify(id, request));
    }

    /** 重新激活线索。 */
    @PostMapping("/{id}/reopen") @PreAuthorize("hasAuthority('crm:lead:update')") @CrmDataScope(permissionCode = "crm:lead:update")
    @OperLog(module = "CRM线索", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.LeadVO> reopen(@PathVariable Long id, @Valid @RequestBody CrmRequests.VersionRequest request) {
        return R.ok(leadService.reopen(id, request));
    }

    /** 幂等转换线索。 */
    @PostMapping("/{id}/convert") @PreAuthorize("hasAuthority('crm:lead:convert')") @CrmDataScope(permissionCode = "crm:lead:convert")
    @OperLog(module = "CRM线索转换", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"customerName", "contactName", "opportunityName"})
    public R<CrmViews.ConversionResultVO> convert(@PathVariable Long id,
                                                  @Valid @RequestBody CrmRequests.ConvertLeadRequest request) {
        return R.ok(leadService.convert(id, request));
    }
}
