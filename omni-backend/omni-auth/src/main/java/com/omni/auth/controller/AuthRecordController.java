package com.omni.auth.controller;

import com.omni.auth.service.AuthRecordService;
import com.omni.auth.service.AuthRecordVO;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权记录控制器。
 * <p>提供 OAuth2 授权记录的分页查询接口，路径映射在 {@code /api/auth/auth-record}。</p>
 *
 * @see AuthRecordService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/auth-record")
@RequiredArgsConstructor
public class AuthRecordController {

    private final AuthRecordService authRecordService;

    /**
     * 分页查询授权记录。
     *
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 10）
     * @return 授权记录分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:authrecord:list')")
    public R<PageResult<AuthRecordVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(authRecordService.listRecords(page, size));
    }
}
