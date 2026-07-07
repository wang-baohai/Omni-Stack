package com.omni.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程实例扩展实体。
 * <p>
 * 补充 Flowable 未存储的业务字段（标题、发起人姓名、分类等），
 * 与 Flowable 的 {@code ACT_RU_EXECUTION} 通过 {@code processInstanceId} 关联。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("wf_process_instance_ext")
public class WfProcessInstanceExt implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** Flowable 流程实例 ID */
    private String processInstanceId;

    /** 流程定义 Key */
    private String processKey;

    /** 流程模型 ID */
    private Long modelId;

    /** 流程模型版本 ID */
    private Long modelVersionId;

    /** Flowable 流程定义 ID */
    private String processDefinitionId;

    /** Flowable 部署 ID */
    private String deploymentId;

    /** 业务版本号 */
    private Integer businessVersion;

    /** Flowable 引擎版本号 */
    private Integer engineVersion;

    /** 业务主键（外部表单关联） */
    private String businessKey;

    /** 流程标题（显示用） */
    private String title;

    /** 发起人用户 ID */
    private Long startUserId;

    /** 发起人用户名 */
    private String startUserName;

    /** 流程分类 */
    private String category;

    /** 状态: 0-已终止, 1-进行中, 2-已完成 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
