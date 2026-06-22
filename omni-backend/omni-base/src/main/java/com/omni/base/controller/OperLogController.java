package com.omni.base.controller;

import com.omni.base.dto.OperLogQuery;
import com.omni.base.service.OperLogService;
import com.omni.base.service.OperLogVO;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询控制器。
 * <p>只读接口，提供分页查询操作日志。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/base/oper-log")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    /**
     * 分页查询操作日志。
     *
     * @param tenantId 租户ID
     * @param query    查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('base:operlog:list')")
    public R<PageResult<OperLogVO>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            OperLogQuery query) {
        return R.ok(operLogService.listOperLogs(tenantId, query));
    }
}
