package com.omni.auth.controller;

import com.omni.auth.dto.LoginRecordQuery;
import com.omni.auth.dto.LoginRecordVO;
import com.omni.auth.service.LoginRecordService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录记录控制器（D02）。
 * <p>现有安全审计的独立只读投影，数据源唯一为 {@code sys_audit_log}。
 * 权限码 {@code system:loginrecord:list}。</p>
 *
 * @author Omni-Stack Team
 * @see LoginRecordService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/login-record")
@RequiredArgsConstructor
public class LoginRecordController {

    private final LoginRecordService loginRecordService;

    /**
     * 分页查询本租户登录记录（脱敏、白名单映射、服务端固定事件谓词）。
     *
     * @param tenantId 可信租户 ID（请求头 {@code X-Tenant-Id}）
     * @param query    查询参数
     * @return 脱敏登录记录分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:loginrecord:list')")
    public R<PageResult<LoginRecordVO>> list(@RequestHeader("X-Tenant-Id") Long tenantId,
                                             @Valid LoginRecordQuery query) {
        return R.ok(loginRecordService.listLoginRecords(tenantId, query));
    }
}
