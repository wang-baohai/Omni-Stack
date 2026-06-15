package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.dto.AuditLogQuery;
import com.omni.auth.entity.SysAuditLog;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysAuditLogMapper;
import com.omni.auth.service.AuditLogService;
import com.omni.auth.service.AuditLogVO;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void save(AuditEvent event) {
        SysAuditLog entity = new SysAuditLog();
        entity.setTenantId(event.getTenantId());
        entity.setEventType(event.getEventType());
        entity.setUsername(event.getUsername());
        entity.setUserId(event.getUserId());
        entity.setIpAddress(event.getIpAddress());
        entity.setUserAgent(event.getUserAgent());
        entity.setDescription(event.getDescription());
        entity.setCreateBy(event.getCreateBy());
        entity.setCreateTime(java.time.LocalDateTime.now());

        // 序列化 extra Map 为 JSON 字符串
        if (event.getExtra() != null && !event.getExtra().isEmpty()) {
            try {
                entity.setExtra(objectMapper.writeValueAsString(event.getExtra()));
            } catch (JsonProcessingException e) {
                log.warn("序列化审计日志 extra 字段失败: {}", e.getMessage());
                entity.setExtra("{}");
            }
        }

        sysAuditLogMapper.insert(entity);
    }

    @Override
    public PageResult<AuditLogVO> listAuditLogs(Long tenantId, AuditLogQuery query) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAuditLog::getTenantId, tenantId);

        if (query.getEventType() != null && !query.getEventType().isBlank()) {
            wrapper.in(SysAuditLog::getEventType, (Object[]) query.getEventType().split(","));
        }
        if (query.getUsername() != null && !query.getUsername().isBlank()) {
            wrapper.like(SysAuditLog::getUsername, query.getUsername());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(SysAuditLog::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(SysAuditLog::getCreateTime, query.getEndTime());
        }

        wrapper.orderByDesc(SysAuditLog::getCreateTime);

        Page<SysAuditLog> mpPage = sysAuditLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<AuditLogVO> records = mpPage.getRecords().stream()
                .map(this::toVO)
                .toList();

        return new PageResult<>(records, mpPage.getTotal(), mpPage.getSize(), mpPage.getCurrent());
    }

    /**
     * 将实体转换为视图对象。
     */
    private AuditLogVO toVO(SysAuditLog entity) {
        Map<String, Object> extraMap = Collections.emptyMap();
        if (entity.getExtra() != null && !entity.getExtra().isBlank()) {
            try {
                extraMap = objectMapper.readValue(entity.getExtra(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.warn("反序列化审计日志 extra 字段失败: id={}", entity.getId());
            }
        }

        return AuditLogVO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .eventType(entity.getEventType())
                .username(entity.getUsername())
                .userId(entity.getUserId())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .description(entity.getDescription())
                .extra(extraMap)
                .createBy(entity.getCreateBy())
                .createTime(entity.getCreateTime())
                .build();
    }
}
