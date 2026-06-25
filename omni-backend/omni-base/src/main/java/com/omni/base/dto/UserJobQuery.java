package com.omni.base.dto;

import lombok.Data;

/**
 * 用户任务查询参数。
 *
 * @author Omni-Stack Team
 */
@Data
public class UserJobQuery {

    /** 任务名称（模糊匹配） */
    private String jobName;

    /** 任务类型编码 */
    private String jobType;

    /** 状态过滤 */
    private Integer status;

    /** 创建人过滤（用户自助模式） */
    private String createBy;
}
