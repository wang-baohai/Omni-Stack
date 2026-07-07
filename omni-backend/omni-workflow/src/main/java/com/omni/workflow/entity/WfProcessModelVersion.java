package com.omni.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程模型版本实体。
 * <p>
 * 每次保存草稿或发布都对应一条版本记录，
 * 包含 BPMN XML、设计器 JSON、Flowable 部署信息。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("wf_process_model_version")
public class WfProcessModelVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 关联 wf_process_model.id */
    private Long modelId;

    /** 业务版本号（1, 2, 3...） */
    private Integer version;

    /** 版本状态: DRAFT/PUBLISHED/FAILED/ARCHIVED */
    private String status;

    /** BPMN XML 内容 */
    private String bpmnXml;

    /** 可视化设计器 JSON */
    private String designerJson;

    /** BPMN XML 的 SHA-256 摘要 */
    private String xmlSha256;

    /** Flowable 部署 ID */
    private String deploymentId;

    /** Flowable 流程定义 ID */
    private String processDefinitionId;

    /** Flowable 引擎 process key */
    private String engineProcessKey;

    /** Flowable 引擎版本号 */
    private Integer engineVersion;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 发布人 */
    private String publishBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
