package com.omni.procurement.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * SRM 内部供应商查询契约。
 *
 * @author Omni-Stack Team
 */
public final class SrmSupplierContracts {

    private SrmSupplierContracts() {
    }

    /** 供应商批量查询请求。 */
    @Data
    public static class BatchRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 租户 ID。 */ @NotNull @Positive private Long tenantId;
        /** 供应商 ID。 */
        @NotEmpty @Size(max = 100)
        private List<@NotNull @Positive Long> supplierIds;
    }

    /** 不含联系人和账户信息的供应商摘要。 */
    @Data
    public static class Summary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 供应商 ID。 */ private Long id;
        /** 供应商编号。 */ private String supplierNo;
        /** 供应商名称。 */ private String name;
        /** 生命周期状态。 */ private String status;
        /** 供应商等级。 */ private String levelCode;
        /** 供应品类。 */ private String categoryCode;
    }
}
