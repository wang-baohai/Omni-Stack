package com.omni.srm.dto.quotation;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** Procurement 内部 RFQ 邀请行契约。 */
@Data
public class ProcurementRfqInvitationLine implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** RFQ 行 ID。 */
    private Long rfqLineId;

    /** 物料编码快照。 */
    private String materialCode;

    /** 物料名称快照。 */
    private String materialName;

    /** 计量单位快照。 */
    private String unit;

    /** 询价数量快照。 */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;

    /** 采购方备注。 */
    private String remark;
}
