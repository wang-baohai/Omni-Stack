package com.omni.srm.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 服务间供应商摘要，不包含联系人、银行账户等 PII。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class InternalSupplierSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 供应商 ID。 */
    private Long id;
    /** 供应商编号。 */
    private String supplierNo;
    /** 供应商名称。 */
    private String name;
    /** 生命周期状态。 */
    private String status;
    /** 供应商等级。 */
    private String levelCode;
    /** 供应品类。 */
    private String categoryCode;
}
