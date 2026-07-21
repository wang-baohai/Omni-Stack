package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.service.PortalInviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SRM 门户邀请控制器。 */
@RestController
@RequestMapping({"/api/srm/portal/invite", "/api/srm/invite"})
@RequiredArgsConstructor
public class PortalInviteController {
    private final PortalInviteService portalInviteService;

    /** 查询邀请列表。 */
    @GetMapping({"", "/list"})
    @PreAuthorize("hasAnyAuthority('srm:portal:invite', 'srm:invite:list')")
    public R<List<SrmViews.InviteVO>> list() {
        return R.ok(portalInviteService.list());
    }

    /** 创建邀请。 */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('srm:portal:invite', 'srm:invite:create')")
    @OperLog(module = "SRM邀请", operType = OperType.CREATE, recordSnapshot = false)
    public R<SrmViews.InviteVO> create(@Valid @RequestBody SrmRequests.CreateInviteRequest request) {
        return R.ok(portalInviteService.create(request));
    }

    /** 撤销邀请。 */
    @PostMapping("/{inviteId}/revoke")
    @PreAuthorize("hasAnyAuthority('srm:portal:invite', 'srm:invite:revoke')")
    @OperLog(module = "SRM邀请", operType = OperType.UPDATE, idExpr = "#inviteId")
    public R<Void> revoke(@PathVariable Long inviteId) {
        portalInviteService.revoke(inviteId);
        return R.ok();
    }
}
