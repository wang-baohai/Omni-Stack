package com.omni.srm.controller;

import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.srm.service.OwnerOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SRM 选项控制器。 */
@RestController
@RequestMapping("/api/srm/options")
@RequiredArgsConstructor
public class OptionsController {
    private final OwnerOptionService ownerOptionService;

    /** 查询负责人候选。 */
    @GetMapping("/owners") @PreAuthorize("hasAuthority('srm:owner:list')") @ServiceDataScope(permissionCode = "srm:owner:list")
    public R<List<SrmViews.OwnerOptionVO>> owners(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return R.ok(ownerOptionService.list(keyword, limit));
    }
}
