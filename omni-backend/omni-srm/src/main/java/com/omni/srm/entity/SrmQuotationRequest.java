package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 报价请求幂等历史实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_quotation_request")
public class SrmQuotationRequest extends SrmTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端请求 ID。 */
    private String requestId;

    /** 报价 ID；预留阶段可以为空。 */
    private Long quotationId;

    /** RFQ ID。 */
    private Long rfqId;

    /** 供应商 ID。 */
    private Long supplierId;

    /** 规范化请求 SHA-256。 */
    private String requestHash;

    /** 本次请求完成后的报价版本。 */
    private Integer targetVersion;

    /** RESERVED 或 COMPLETED。 */
    private String status;
}
