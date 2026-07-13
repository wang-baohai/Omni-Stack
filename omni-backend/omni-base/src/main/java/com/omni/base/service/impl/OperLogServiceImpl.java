package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.base.dto.OperLogQuery;
import com.omni.base.entity.SysOperLog;
import com.omni.base.mapper.SysOperLogMapper;
import com.omni.base.service.OperLogService;
import com.omni.base.service.OperLogVO;
import com.omni.common.core.operlog.OperLogMessage;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 操作日志服务实现。
 *
 * @author Omni-Stack Team
 * @see OperLogService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final SysOperLogMapper sysOperLogMapper;

    /** {@inheritDoc} */
    @Override
    public void save(OperLogMessage message) {
        SysOperLog entity = new SysOperLog();
        entity.setEventId(resolveEventId(message));
        entity.setTenantId(message.getTenantId());
        entity.setOperUsername(message.getOperUsername());
        entity.setOperTime(message.getOperTime());
        entity.setModule(message.getModule());
        entity.setOperType(message.getOperType());
        entity.setRequestMethod(message.getRequestMethod());
        entity.setRequestUrl(message.getRequestUrl());
        entity.setRequestParams(message.getRequestParams());
        entity.setResponseStatus(message.getResponseStatus());
        entity.setIpAddress(message.getIpAddress());
        entity.setUserAgent(message.getUserAgent());
        entity.setExecutionTime(message.getExecutionTime());
        entity.setOldValue(message.getOldValue());
        entity.setNewValue(message.getNewValue());
        entity.setErrorMsg(message.getErrorMsg());
        sysOperLogMapper.insert(entity);
    }

    private String resolveEventId(OperLogMessage message) {
        if (message.getEventId() != null && !message.getEventId().isBlank()) {
            return message.getEventId();
        }
        String legacyIdentity = String.join("|",
                String.valueOf(message.getTenantId()),
                String.valueOf(message.getOperUsername()),
                String.valueOf(message.getOperTime()),
                String.valueOf(message.getModule()),
                String.valueOf(message.getOperType()),
                String.valueOf(message.getRequestMethod()),
                String.valueOf(message.getRequestUrl()),
                String.valueOf(message.getRequestParams()));
        return "legacy-message-" + UUID.nameUUIDFromBytes(
                legacyIdentity.getBytes(StandardCharsets.UTF_8));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<OperLogVO> listOperLogs(Long tenantId, OperLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOperLog::getTenantId, tenantId);

        if (query.getModule() != null && !query.getModule().isBlank()) {
            wrapper.like(SysOperLog::getModule, query.getModule());
        }
        if (query.getOperType() != null && !query.getOperType().isBlank()) {
            wrapper.eq(SysOperLog::getOperType, query.getOperType());
        }
        if (query.getOperUsername() != null && !query.getOperUsername().isBlank()) {
            wrapper.like(SysOperLog::getOperUsername, query.getOperUsername());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(SysOperLog::getOperTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(SysOperLog::getOperTime, query.getEndTime());
        }

        wrapper.orderByDesc(SysOperLog::getOperTime);

        Page<SysOperLog> page = sysOperLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        return new PageResult<>(
                page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(),
                page.getSize(),
                page.getCurrent()
        );
    }

    /**
     * 将实体转换为视图对象。
     *
     * @param entity 操作日志实体
     * @return 操作日志视图对象
     */
    private OperLogVO toVO(SysOperLog entity) {
        return OperLogVO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .operUsername(entity.getOperUsername())
                .operTime(entity.getOperTime())
                .module(entity.getModule())
                .operType(entity.getOperType())
                .requestMethod(entity.getRequestMethod())
                .requestUrl(entity.getRequestUrl())
                .requestParams(entity.getRequestParams())
                .responseStatus(entity.getResponseStatus())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .executionTime(entity.getExecutionTime())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .errorMsg(entity.getErrorMsg())
                .createTime(entity.getCreateTime())
                .build();
    }
}
