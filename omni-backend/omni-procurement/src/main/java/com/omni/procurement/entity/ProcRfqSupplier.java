package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 询价供应商邀请及报价关联快照。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_rfq_supplier")
public class ProcRfqSupplier extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 询价单 ID。 */
    private Long rfqId;

    /** 供应商 ID。 */
    private Long supplierId;

    /** 邀请时供应商名称快照。 */
    private String supplierNameSnapshot;

    /** 实际发送邀请时间。 */
    private LocalDateTime invitedTime;

    /** SRM 报价 ID。 */
    private Long quotationId;

    /** SRM 报价业务版本。 */
    private Integer quotationVersion;

    /** 最近一次已接受报价提交请求 ID。 */
    private String quotationRequestId;

    /** 最近一次已接受报价事件发生时间。 */
    private LocalDateTime quotationTime;

    /** INVITED/QUOTED/EXPIRED。 */
    private String status;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
