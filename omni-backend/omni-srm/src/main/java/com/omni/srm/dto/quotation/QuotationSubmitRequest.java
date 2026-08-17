package com.omni.srm.dto.quotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商门户报价提交请求。 */
@Data
public class QuotationSubmitRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端幂等请求 ID。 */
    @NotBlank
    @Size(max = 64)
    private String requestId;

    /** Procurement 询价单 ID。 */
    @NotNull
    @Positive
    private Long rfqId;

    /** 当前报价版本；首次提交固定为 0。 */
    @NotNull
    @Min(0)
    private Integer version;

    /** 报价有效期。 */
    @NotNull
    private LocalDateTime validUntil;

    /** 报价行；必须完整覆盖询价行。 */
    @Valid
    @NotEmpty
    @Size(max = 200)
    private List<QuotationLineRequest> lines;
}
