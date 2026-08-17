package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRM 供应商报价头实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_quotation")
public class SrmQuotation extends SrmTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 供应商 ID。 */
    private Long supplierId;

    /** Procurement 询价单 ID。 */
    private Long rfqId;

    /** 询价单号快照。 */
    private String rfqNo;

    /** 供应商名称快照。 */
    private String supplierNameSnapshot;

    /** 客户端幂等请求 ID。 */
    private String requestId;

    /** 报价时间。 */
    private LocalDateTime quotationTime;

    /** 报价有效期。 */
    private LocalDateTime validUntil;

    /** 服务端汇总金额。 */
    private BigDecimal totalAmount;

    /** 币种代码。 */
    private String currencyCode;

    /** 报价状态。 */
    private String status;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
