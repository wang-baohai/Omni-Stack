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
import com.omni.auth.service.LoginProjectionRules;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 审计日志服务实现。
 * <p>将 {@link AuditEvent} 持久化到 {@code sys_audit_log} 表，
 * {@code extra} 字段以 JSON 字符串形式存储。</p>
 *
 * @author Omni-Stack Team
 * @see AuditLogService
 * @see AuditEvent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
     * <p>与登录记录投影共享同一安全脱敏规则（RF02）：IP 可靠掩码、UA 仅留类别，
     * extra 丢弃凭据/地址类敏感键，保留其他审计事件的必要非秘密语义；不重写历史审计。</p>
     */
    private AuditLogVO toVO(SysAuditLog entity) {
        Map<String, Object> extraMap = Collections.emptyMap();
        if (entity.getExtra() != null && !entity.getExtra().isBlank()) {
            try {
                extraMap = redactExtra(objectMapper.readValue(entity.getExtra(),
                        new TypeReference<Map<String, Object>>() {}));
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
                .ipAddress(LoginProjectionRules.maskIp(entity.getIpAddress()))
                .userAgent(LoginProjectionRules.summarizeUserAgent(entity.getUserAgent()))
                .description(entity.getDescription())
                .extra(extraMap)
                .createBy(entity.getCreateBy())
                .createTime(entity.getCreateTime())
                .build();
    }

    /** 与旧审计页面同批的敏感键不得回显（IP/UA/凭据/会话/验证码/token）。 */
    private static final Set<String> SENSITIVE_EXTRA_KEYS = Set.of(
            "ip", "ipaddress", "ip_address", "clientip", "useragent", "user_agent", "ua",
            "password", "pwd", "token", "accesstoken", "refreshtoken", "code", "state",
            "sessionid", "session_id", "captcha", "captchacode", "secret", "authorization");

    private Map<String, Object> redactExtra(Map<String, Object> extra) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            if (e.getKey() != null && SENSITIVE_EXTRA_KEYS.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            safe.put(e.getKey(), e.getValue());
        }
        return safe;
    }
}
