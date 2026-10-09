package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.dto.LoginRecordQuery;
import com.omni.auth.dto.LoginRecordVO;
import com.omni.auth.entity.SysAuditLog;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysAuditLogMapper;
import com.omni.auth.service.LoginProjectionRules;
import com.omni.auth.service.LoginRecordService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录记录查询服务实现（D02）。
 * <p>固定服务端谓词 {@code tenant_id=trusted AND event_type IN (LOGIN_SUCCESS,LOGIN_FAILED)}，
 * 再组合过滤；list 与 COUNT 使用完全相同条件（MyBatis-Plus 分页）。method/reason/provider 仅从
 * extra 白名单元数据映射，历史缺失归 UNKNOWN/null，绝不返回 extra 原文或任何凭据。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRecordServiceImpl implements LoginRecordService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> RESULT_SET = Set.of("SUCCESS", "FAILURE");
    private static final Set<String> METHOD_SET = Set.of("PASSWORD", "SESSION", "SOCIAL", "UNKNOWN");
    private static final Set<String> REASON_WHITELIST = Set.of(
            "wrong_password", "user_not_found", "account_locked", "invalid_captcha",
            "user_disabled", "tenant_unavailable", "provider_denied", "state_invalid", "callback_error");
    private static final int MAX_SPAN_DAYS = 90;
    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_USERNAME_LEN = 64;
    private static final String PROVIDER_SQL_LIST = LoginProjectionRules.PROVIDER_SQL_LIST;

    private final SysAuditLogMapper sysAuditLogMapper;

    /** {@inheritDoc} */
    @Override
    public PageResult<LoginRecordVO> listLoginRecords(Long tenantId, LoginRecordQuery query) {
        validate(query);

        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        // 固定谓词：可信租户 + 登录事件集合，客户端不可覆盖
        wrapper.eq(SysAuditLog::getTenantId, tenantId)
                .in(SysAuditLog::getEventType, AuditEvent.LOGIN_SUCCESS, AuditEvent.LOGIN_FAILED);

        if (query.getUsername() != null && !query.getUsername().isBlank()) {
            wrapper.like(SysAuditLog::getUsername, query.getUsername());
        }
        // 结果过滤 → 收窄到对应事件类型（仍在固定集合内）
        if (query.getResult() != null && !query.getResult().isBlank()) {
            if ("SUCCESS".equalsIgnoreCase(query.getResult())) {
                wrapper.eq(SysAuditLog::getEventType, AuditEvent.LOGIN_SUCCESS);
            } else {
                wrapper.eq(SysAuditLog::getEventType, AuditEvent.LOGIN_FAILED);
            }
        }
        // 方式过滤 → 基于 extra JSON 白名单（DB 级，保证 list 与 COUNT 同条件）
        if (query.getMethod() != null && !query.getMethod().isBlank()) {
            applyMethodFilter(wrapper, query.getMethod().toUpperCase());
        }
        // 时间：默认近 7 天；跨度<=90 天
        LocalDateTime start = query.getStartTime();
        LocalDateTime end = query.getEndTime();
        if (start == null && end == null) {
            end = LocalDateTime.now();
            start = end.minusDays(DEFAULT_RANGE_DAYS);
        }
        wrapper.ge(SysAuditLog::getCreateTime, start);
        wrapper.le(SysAuditLog::getCreateTime, end);

        // 稳定分页：create_time DESC, id DESC
        wrapper.orderByDesc(SysAuditLog::getCreateTime).orderByDesc(SysAuditLog::getId);

        Page<SysAuditLog> mpPage = sysAuditLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<LoginRecordVO> records = mpPage.getRecords().stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, mpPage.getTotal(), mpPage.getSize(), mpPage.getCurrent());
    }

    private void validate(LoginRecordQuery query) {
        if (query.getPage() < 1) {
            throw new BusinessException(400, "page 必须 >= 1");
        }
        if (query.getSize() < 1 || query.getSize() > 100) {
            throw new BusinessException(400, "size 必须在 1~100");
        }
        if (query.getUsername() != null && query.getUsername().length() > MAX_USERNAME_LEN) {
            throw new BusinessException(400, "username 长度不能超过 " + MAX_USERNAME_LEN);
        }
        if (query.getResult() != null && !query.getResult().isBlank()
                && !RESULT_SET.contains(query.getResult().toUpperCase())) {
            throw new BusinessException(400, "result 取值非法");
        }
        if (query.getMethod() != null && !query.getMethod().isBlank()
                && !METHOD_SET.contains(query.getMethod().toUpperCase())) {
            throw new BusinessException(400, "method 取值非法");
        }
        LocalDateTime start = query.getStartTime();
        LocalDateTime end = query.getEndTime();
        if ((start == null) != (end == null)) {
            throw new BusinessException(400, "startTime 与 endTime 必须成对提供");
        }
        if (start != null && end != null) {
            if (start.isAfter(end)) {
                throw new BusinessException(400, "startTime 不能晚于 endTime");
            }
            // 精确比较时长（Duration）而非截断 toDays()：跨度严格 <= 90 天
            if (Duration.between(start, end).compareTo(Duration.ofDays(MAX_SPAN_DAYS)) > 0) {
                throw new BusinessException(400, "单次查询跨度不能超过 90 天");
            }
        }
    }

    /**
     * 方式过滤（RF01/RF02）：每个分支整体用括号包裹，杜绝内部 OR 越过顶层 tenant/event/time 约束；
     * SQL 归类规则与 {@link LoginProjectionRules#classifyMethod} 完全一致（provider 白名单优先，
     * 缺 method/JSON null/未知方法归 UNKNOWN），保证 list 与 COUNT、筛选与展示同语义。
     */
    private void applyMethodFilter(LambdaQueryWrapper<SysAuditLog> wrapper, String method) {
        String providerExpr = "LOWER(JSON_UNQUOTE(JSON_EXTRACT(extra, '$.provider')))";
        String methodExpr = "LOWER(JSON_UNQUOTE(JSON_EXTRACT(extra, '$.method')))";
        String isSocial = providerExpr + " IN (" + PROVIDER_SQL_LIST + ")";
        String notSocial = "(" + providerExpr + " IS NULL OR " + providerExpr + " NOT IN (" + PROVIDER_SQL_LIST + "))";
        switch (method) {
            case "PASSWORD" -> wrapper.apply(
                    "(" + methodExpr + " = {0} AND " + notSocial + ")", "password");
            case "SESSION" -> wrapper.apply(
                    "(" + methodExpr + " = {0} AND " + notSocial + ")", "session");
            case "SOCIAL" -> wrapper.apply("(" + isSocial + ")");
            case "UNKNOWN" -> wrapper.apply(
                    "(" + notSocial + " AND (" + methodExpr + " IS NULL OR " + methodExpr
                            + " NOT IN ('password','session')))");
            default -> { }
        }
    }

    private LoginRecordVO toVO(SysAuditLog entity) {
        Map<String, Object> extra = parseExtra(entity.getExtra());
        String method = LoginProjectionRules.classifyMethod(extra);
        String provider = mapProvider(extra, method);
        String reason = entity.getEventType().equals(AuditEvent.LOGIN_FAILED)
                ? mapReason(extra) : null;
        String username = truncate(entity.getUsername(), MAX_USERNAME_LEN);

        return LoginRecordVO.builder()
                .id(entity.getId())
                .createTime(entity.getCreateTime())
                .username(username == null || username.isBlank() ? "(unknown)" : username)
                .result(entity.getEventType().equals(AuditEvent.LOGIN_SUCCESS) ? "SUCCESS" : "FAILURE")
                .method(method)
                .provider(provider)
                .reasonCode(reason)
                .ipMasked(LoginProjectionRules.maskIp(entity.getIpAddress()))
                .clientSummary(LoginProjectionRules.summarizeUserAgent(entity.getUserAgent()))
                .build();
    }

    private String mapProvider(Map<String, Object> extra, String method) {
        if (!"SOCIAL".equals(method)) {
            return null;
        }
        String pv = String.valueOf(extra.get("provider")).toLowerCase();
        return LoginProjectionRules.PROVIDER_WHITELIST.contains(pv) ? pv : null;
    }

    private String mapReason(Map<String, Object> extra) {
        Object r = extra.get("reason");
        if (r == null) {
            return null;
        }
        String rv = String.valueOf(r);
        return REASON_WHITELIST.contains(rv) ? rv : null;
    }

    private Map<String, Object> parseExtra(String extra) {
        if (extra == null || extra.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(extra, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.warn("解析登录事件 extra 失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
