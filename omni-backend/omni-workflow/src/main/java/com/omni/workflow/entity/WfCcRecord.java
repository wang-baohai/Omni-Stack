package com.omni.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抄送记录实体。
 * <p>
 * 由 {@code CcNotifyDelegate} 在 ServiceTask 执行时写入，
 * 前端查询展示用户的抄送消息列表。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("wf_cc_record")
public class WfCcRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 流程实例 ID */
    private String processInstanceId;

    /** 来源活动节点 ID */
    private String sourceActivityId;

    /** 被抄送人用户 ID */
    private Long userId;

    /** 流程标题 */
    private String title;

    /** 已读状态: 0-未读, 1-已读 */
    private Integer readStatus;

    /** 创建时间 */
    private LocalDateTime createTime;
}
