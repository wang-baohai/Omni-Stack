package com.omni.base.service;

import com.omni.common.core.result.PageResult;
import com.omni.common.mqlog.entity.SysMqMessage;

import java.time.LocalDateTime;

/**
 * 跨服务 MQ 消息聚合服务。
 *
 * @author Omni-Stack Team
 */
public interface MqMessageAggregationService {

    /**
     * 聚合 Base 与 CRM 发件箱记录。
     */
    PageResult<SysMqMessage> list(Long tenantId, Integer status, String topic, String msgKey,
                                  String serviceName, LocalDateTime beginTime, LocalDateTime endTime,
                                  int page, int size);

    /**
     * 按消息 ID 查询消息。
     */
    SysMqMessage getByMsgId(Long tenantId, String msgId);

    /**
     * 重发消息。
     */
    void resend(Long tenantId, String msgId);

    /**
     * 忽略死信。
     */
    void skip(Long tenantId, String msgId);
}
