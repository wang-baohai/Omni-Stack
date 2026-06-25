package com.omni.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务类型注册实体，对应 {@code sys_user_job_type} 表。
 * <p>定义系统支持的任务类型列表，每种类型包含编码、名称、描述及参数模板。
 * 用户在创建自定义任务时，从此表选择任务类型，并根据 {@code paramTemplate} 填写任务参数。
 * 不继承 {@link com.omni.common.core.model.BaseEntity}，字段独立维护。</p>
 *
 * @author Omni-Stack Team
 * @see SysUserJob
 */
@TableName("sys_user_job_type")
public class SysUserJobType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型编码，全局唯一标识，如 "DATA_SYNC"、"REPORT_GEN"、"NOTICE_PUSH" */
    private String typeCode;

    /** 类型名称，中文描述性名称，如 "数据同步"、"报表生成"、"消息推送" */
    private String typeName;

    /** 类型描述，详细说明该类型的用途和适用场景 */
    private String description;

    /** 参数模板，JSON Schema 格式，定义该类型任务所需的参数结构及默认值 */
    private String paramTemplate;

    /** 类型状态：1-启用（可选），0-禁用（不可选择，已创建任务不受影响） */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getParamTemplate() {
        return paramTemplate;
    }

    public void setParamTemplate(String paramTemplate) {
        this.paramTemplate = paramTemplate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
