package com.omni.procurement.controller;

import com.omni.common.core.result.R;
import com.omni.procurement.dto.RfqViews;
import com.omni.procurement.service.InternalRfqInvitationService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受内部令牌保护的 SRM 询价邀请只读接口。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/procurement/rfq")
public class InternalRfqInvitationController {

    private final InternalRfqInvitationService invitationService;

    /**
     * 查询供应商已经收到的邀请，包含已截止的历史邀请。
     *
     * @param tenantId 租户请求头
     * @param supplierId 供应商 ID
     * @return 邀请摘要
     */
    @GetMapping("/invitations")
    public R<List<RfqViews.InternalInvitationSummary>> list(
            @RequestHeader("X-Tenant-Id") @Positive Long tenantId,
            @RequestParam @Positive Long supplierId) {
        return R.ok(invitationService.list(tenantId, supplierId));
    }

    /**
     * 查询单个已发送邀请及行快照。
     *
     * @param tenantId 租户请求头
     * @param rfqId 询价单 ID
     * @param supplierId 供应商 ID
     * @return 邀请详情
     */
    @GetMapping("/{rfqId}/invitation")
    public R<RfqViews.InternalInvitationDetail> get(
            @RequestHeader("X-Tenant-Id") @Positive Long tenantId,
            @PathVariable @Positive Long rfqId,
            @RequestParam @Positive Long supplierId) {
        return R.ok(invitationService.get(tenantId, rfqId, supplierId));
    }
}
