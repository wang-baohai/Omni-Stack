package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.base.dto.UserJobLogQuery;
import com.omni.base.entity.SysUserJobLog;
import com.omni.base.mapper.SysUserJobLogMapper;
import com.omni.base.service.UserJobLogService;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户任务执行日志服务实现。
 *
 * @author Omni-Stack Team
 * @see UserJobLogService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserJobLogServiceImpl implements UserJobLogService {

    private final SysUserJobLogMapper sysUserJobLogMapper;

    /** {@inheritDoc} */
    @Override
    public PageResult<SysUserJobLog> listLogs(Long tenantId, UserJobLogQuery query, int page, int size) {
        LambdaQueryWrapper<SysUserJobLog> wrapper = new LambdaQueryWrapper<SysUserJobLog>()
                .eq(query.getJobId() != null, SysUserJobLog::getJobId, query.getJobId())
                .eq(query.getJobType() != null && !query.getJobType().isBlank(),
                        SysUserJobLog::getJobType, query.getJobType())
                .eq(query.getStatus() != null, SysUserJobLog::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, SysUserJobLog::getFireTime, query.getStartTime())
                .le(query.getEndTime() != null, SysUserJobLog::getFireTime, query.getEndTime())
                .orderByDesc(SysUserJobLog::getId);

        Page<SysUserJobLog> pageResult = sysUserJobLogMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public void createLog(SysUserJobLog logEntity) {
        sysUserJobLogMapper.insert(logEntity);
    }
}
