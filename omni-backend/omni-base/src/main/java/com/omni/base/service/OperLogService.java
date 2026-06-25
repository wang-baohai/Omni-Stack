package com.omni.base.service;

import com.omni.base.dto.OperLogQuery;
import com.omni.common.core.operlog.OperLogMessage;
import com.omni.common.core.result.PageResult;

/**
 * 操作日志服务接口。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.impl.OperLogServiceImpl
 */
public interface OperLogService {

    /**
     * 保存操作日志（从 MQ 消息）。
     *
     * @param message 操作日志消息
     */
    void save(OperLogMessage message);

    /**
     * 分页查询操作日志（租户隔离）。
     *
     * @param tenantId 租户ID
     * @param query    查询条件
     * @return 分页结果
     */
    PageResult<OperLogVO> listOperLogs(Long tenantId, OperLogQuery query);
}
