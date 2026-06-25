package com.omni.base.service;

import com.omni.base.dto.UserJobLogQuery;
import com.omni.base.entity.SysUserJobLog;
import com.omni.common.core.result.PageResult;

/**
 * 用户任务执行日志服务接口。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.impl.UserJobLogServiceImpl
 */
public interface UserJobLogService {

    /**
     * 分页查询执行日志。
     *
     * @param tenantId 租户 ID
     * @param query    查询条件（任务 ID、类型、状态、时间范围）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<SysUserJobLog> listLogs(Long tenantId, UserJobLogQuery query, int page, int size);

    /**
     * 创建执行日志记录（由 Consumer 调用）。
     *
     * @param logEntity 日志实体
     */
    void createLog(SysUserJobLog logEntity);
}
