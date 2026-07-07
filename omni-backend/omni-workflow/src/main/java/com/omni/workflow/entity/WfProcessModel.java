package com.omni.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程模型主实体。
 * <p>
 * 管理模型元数据，通过 {@code currentDraftVersionId} / {@code currentPublishedVersionId}
 * 指向 {@link WfProcessModelVersion} 实现草稿/发布双轨制。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("wf_process_model")
public class WfProcessModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 模型标识（BPMN process id，同租户唯一） */
    private String modelKey;

    /** 模型名称 */
    private String modelName;

    /** 流程分类 */
    private String category;

    /** 状态: 0-已归档, 1-正常 */
    private Integer status;

    /** 当前草稿版本 ID */
    private Long currentDraftVersionId;

    /** 当前已发布版本 ID */
    private Long currentPublishedVersionId;

    /** 创建人 */
    private String createBy;

    /** 更新人 */
    private String updateBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
