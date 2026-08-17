package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单聚合根。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_purchase_order")
public class ProcPurchaseOrder extends ProcOwnedEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户内采购订单编号。 */
    private String poNo;

    /** 来源询价单 ID。 */
    private Long rfqId;

    /** 中标供应商 ID。 */
    private Long supplierId;

    /** 定点时供应商名称快照。 */
    private String supplierNameSnapshot;

    /** SRM 中标报价 ID。 */
    private Long quotationId;

    /** SRM 中标报价版本。 */
    private Integer quotationVersion;

    /** 订单标题。 */
    private String title;

    /** 服务端核算的订单总金额。 */
    private BigDecimal totalAmount;

    /** ISO 4217 币种编码。 */
    private String currencyCode;

    /** DRAFT/SENT/CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED。 */
    private String status;

    /** 订单发送时间。 */
    private LocalDateTime orderTime;

    /** 预计交付日期。 */
    private LocalDate expectedDeliveryDate;

    /** 实际全部收货日期。 */
    private LocalDate actualDeliveryDate;

    /** 收货地址。 */
    private String deliveryAddress;

    /** 收货联系人。 */
    private String contactName;

    /** 收货联系电话。 */
    private String contactPhone;
}
