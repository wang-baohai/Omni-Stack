package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录保护策略实体，映射 {@code sys_auth_login_policy} 表（D01）。
 * <p>
 * 租户级登录保护配置，{@code tenant_id} 为主键（一租户一行）。
 * 无 override 行时认证读取内置默认（阈值 10 / 窗口 3600 秒，source=DEFAULT）。
 * {@code version} 为乐观并发控制字段，首次保存产生 1。
 * 本表不继承 {@code BaseEntity}（主键为 tenant_id 而非自增 id），操作者与时间由服务层显式写入。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("sys_auth_login_policy")
public class SysAuthLoginPolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID（主键） */
    @TableId(type = IdType.INPUT)
    private Long tenantId;

    /** 窗口内锁定阈值，允许 3~10 */
    private Integer failureThreshold;

    /** 固定失败窗口秒数，允许 3600~86400 */
    private Integer failureWindowSeconds;

    /** 乐观并发版本 */
    private Long version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建操作者 */
    private String createBy;

    /** 更新操作者 */
    private String updateBy;
}
