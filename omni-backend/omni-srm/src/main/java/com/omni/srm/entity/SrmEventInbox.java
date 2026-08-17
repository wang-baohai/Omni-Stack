package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SRM 领域事件收件箱实体。
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("srm_event_inbox")
public class SrmEventInbox implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String eventId;
    private String eventType;
    private String sourceService;
    private String aggregateType;
    private String aggregateId;
    private String payload;
    private String status;
    private LocalDateTime processedTime;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
