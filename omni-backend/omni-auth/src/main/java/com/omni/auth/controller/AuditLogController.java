package com.omni.auth.controller;

import com.omni.auth.dto.AuditLogQuery;
import com.omni.auth.service.AuditLogService;
import com.omni.auth.service.AuditLogVO;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志控制器，提供审计日志只读分页查询接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志。
     *
     * <p>支持按事件类型、用户名、时间范围筛选，强制按租户隔离。</p>
     *
     * @param query    查询参数
     * @param tenantId 租户ID（由 Gateway 注入）
     * @return 分页审计日志列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:auditlog:list')")
    public R<PageResult<AuditLogVO>> list(AuditLogQuery query,
                                           @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(auditLogService.listAuditLogs(tenantId, query));
    }
}
