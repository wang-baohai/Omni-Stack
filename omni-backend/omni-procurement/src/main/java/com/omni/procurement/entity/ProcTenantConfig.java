package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 采购租户级默认配置。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_tenant_config")
public class ProcTenantConfig extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认币种。 */
    private String currencyCode;

    /** 初始化完成时间。 */
    private LocalDateTime initializedTime;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
