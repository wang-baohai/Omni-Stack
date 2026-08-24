package com.omni.crm.controller;

import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.crm.service.OwnerOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRM 选项控制器。 */
@RestController
@RequestMapping("/api/crm/options")
@RequiredArgsConstructor
public class OptionsController {
    private final OwnerOptionService ownerOptionService;

    /** 查询负责人候选。 */
    @GetMapping("/owners") @PreAuthorize("hasAuthority('crm:owner:list')") @ServiceDataScope(permissionCode = "crm:owner:list")
    public R<List<CrmViews.OwnerOptionVO>> owners(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return R.ok(ownerOptionService.list(keyword, limit));
    }
}
