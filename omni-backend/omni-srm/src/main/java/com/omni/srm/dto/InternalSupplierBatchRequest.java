package com.omni.srm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 内部供应商批量查询请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalSupplierBatchRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    @NotNull(message = "tenantId 不能为空")
    @Positive(message = "tenantId 必须为正整数")
    private Long tenantId;

    /** 供应商 ID 列表。 */
    @Valid
    @NotEmpty(message = "supplierIds 不能为空")
    @Size(max = 100, message = "supplierIds 单次最多查询 100 条")
    private List<@NotNull(message = "supplierIds 不能包含空值")
            @Positive(message = "supplierIds 必须为正整数") Long> supplierIds;
}
