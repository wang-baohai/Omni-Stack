package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 请购审批路由。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_approval_route")
public class ProcApprovalRoute extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户内稳定路由编码，创建后不可修改。 */
    private String routeCode;

    /** 业务可读的审批规则名称。 */
    private String routeName;

    /** 精确品类编码或通配符 *。 */
    private String categoryCode;

    /** 金额下界，包含。 */
    private BigDecimal minAmount;

    /** 金额上界，不包含；null 表示无上限。 */
    private BigDecimal maxAmount;

    /** 已发布工作流模型版本 ID。 */
    private Long modelVersionId;

    /** 管理列表排序优先级，不参与掩盖配置冲突。 */
    private Integer priority;

    /** ACTIVE/INACTIVE。 */
    private String status;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
