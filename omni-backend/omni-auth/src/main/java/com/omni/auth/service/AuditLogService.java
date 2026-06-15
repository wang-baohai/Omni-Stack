package com.omni.auth.service;

import com.omni.auth.dto.AuditLogQuery;
import com.omni.auth.event.AuditEvent;
import com.omni.common.core.result.PageResult;

/**
 * 审计日志服务接口。
 */
public interface AuditLogService {

    /**
     * 保存审计事件到数据库。
     *
     * @param event 审计事件
     */
    void save(AuditEvent event);

    /**
     * 分页查询审计日志。
     *
     * @param tenantId 租户ID（强制隔离）
     * @param query    查询参数
     * @return 分页结果
     */
    PageResult<AuditLogVO> listAuditLogs(Long tenantId, AuditLogQuery query);
}
