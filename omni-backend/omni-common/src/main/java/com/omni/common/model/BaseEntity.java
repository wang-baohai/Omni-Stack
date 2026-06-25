package com.omni.common.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类，包含通用审计字段。
 * <p>
 * 所有业务实体应继承此类，自动获得主键、创建/更新时间、创建/更新人等公共字段。
 * MyBatis-Plus 的 {@code MetaObjectHandler} 会在插入时自动填充
 * {@code createTime}/{@code createBy}，更新时自动填充 {@code updateTime}/{@code updateBy}。
 * </p>
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@link #id} — 雪花算法生成的主键，由 MyBatis-Plus {@code IdType.ASSIGN_ID} 策略自动分配</li>
 *   <li>{@link #createTime}/{@link #updateTime} — 由 MetaObjectHandler 自动填充的 {@link LocalDateTime}</li>
 *   <li>{@link #createBy}/{@link #updateBy} — 从 SecurityContext 中提取的当前用户名</li>
 * </ul>
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，雪花算法自动生成，无需手动赋值 */
    private Long id;

    /** 创建时间，由 MetaObjectHandler 在 INSERT 时自动填充 */
    private LocalDateTime createTime;

    /** 更新时间，由 MetaObjectHandler 在 UPDATE 时自动填充 */
    private LocalDateTime updateTime;

    /** 创建人用户名，从 SecurityContext 提取，INSERT 时自动填充 */
    private String createBy;

    /** 更新人用户名，从 SecurityContext 提取，UPDATE 时自动填充 */
    private String updateBy;
}
