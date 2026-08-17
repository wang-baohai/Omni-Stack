package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.quotation.QuotationInvitationDetailVO;
import com.omni.srm.dto.quotation.QuotationInvitationSummaryVO;
import com.omni.srm.dto.quotation.QuotationSubmitRequest;
import com.omni.srm.dto.quotation.QuotationVO;
import com.omni.srm.service.QuotationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SRM 供应商门户报价接口。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/srm/portal/quotation")
public class PortalQuotationController {

    private final QuotationService quotationService;

    /**
     * 查询当前供应商收到的 RFQ 邀请。
     *
     * @return 邀请摘要列表
     */
    @GetMapping("/invitations")
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:quotation')")
    public R<List<QuotationInvitationSummaryVO>> invitations() {
        return R.ok(quotationService.listPortalInvitations());
    }

    /**
     * 查询当前供应商的 RFQ 邀请详情。
     *
     * @param rfqId RFQ ID
     * @return 邀请详情
     */
    @GetMapping("/invitations/{rfqId}")
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:quotation')")
    public R<QuotationInvitationDetailVO> invitation(@PathVariable @Positive Long rfqId) {
        return R.ok(quotationService.getPortalInvitation(rfqId));
    }

    /**
     * 幂等提交或更新报价。
     *
     * @param request 报价请求
     * @return 服务端报价快照
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:quotation')")
    @OperLog(module = "SRM门户报价", operType = OperType.CREATE, recordSnapshot = false)
    public R<QuotationVO> submit(@Valid @RequestBody QuotationSubmitRequest request) {
        return R.ok(quotationService.submit(request));
    }
}
