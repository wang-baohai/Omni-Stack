package com.omni.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待办任务缓存实体。
 * <p>
 * 用于快速查询"待我审批"列表，避免每次联查 Flowable 的 {@code ACT_*} 表。
 * 通过 Flowable TaskListener 同步写入/删除。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("wf_todo_task")
public class WfTodoTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** Flowable 任务 ID */
    private String taskId;

    /** 流程实例 ID */
    private String processInstanceId;

    /** 流程定义 Key */
    private String processKey;

    /** 任务名称 */
    private String taskName;

    /** 处理人用户 ID */
    private Long assigneeId;

    /** 处理人用户名 */
    private String assigneeName;

    /** 流程标题 */
    private String title;

    /** 流程分类 */
    private String category;

    /** 任务创建时间 */
    private LocalDateTime createTime;
}
