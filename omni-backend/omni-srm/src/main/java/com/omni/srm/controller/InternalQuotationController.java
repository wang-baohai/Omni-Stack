package com.omni.srm.controller;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.dto.quotation.QuotationVO;
import com.omni.srm.service.QuotationService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受内部令牌保护的报价批量查询接口。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/quotation")
public class InternalQuotationController {

    private final QuotationService quotationService;

    /**
     * 查询指定 RFQ 的有效报价头行快照。
     *
     * @param headerTenantId 请求头租户 ID
     * @param tenantId 查询租户 ID
     * @param rfqId RFQ ID
     * @return 有效报价快照
     */
    @GetMapping("/batch")
    public R<List<QuotationVO>> batch(
            @RequestHeader("X-Tenant-Id") @Positive Long headerTenantId,
            @RequestParam @Positive Long tenantId,
            @RequestParam @Positive Long rfqId) {
        if (!headerTenantId.equals(tenantId)) {
            throw new BusinessException(403, "请求头与查询参数的租户 ID 不一致");
        }
        return R.ok(quotationService.listValidByRfq(tenantId, rfqId));
    }
}
