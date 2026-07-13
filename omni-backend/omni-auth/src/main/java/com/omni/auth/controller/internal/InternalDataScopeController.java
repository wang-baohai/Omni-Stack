package com.omni.auth.controller.internal;

import com.omni.auth.service.DataScopeService;
import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部数据权限范围接口。
 * <p>仅供携带有效 {@code X-Internal-Token} 的受信任微服务调用。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/internal/data-scopes")
@RequiredArgsConstructor
public class InternalDataScopeController {

    /** 数据范围解析服务 */
    private final DataScopeService dataScopeService;

    /**
     * 按完整权限码解析用户的数据权限范围。
     *
     * @param userId         用户 ID
     * @param tenantId       租户 ID
     * @param permissionCode 完整权限码
     * @return 权限码对应的数据权限范围
     */
    @GetMapping("/{userId}")
    public R<InternalDataScopeDTO> getDataScope(@PathVariable Long userId,
                                                @RequestParam Long tenantId,
                                                @RequestParam String permissionCode) {
        return R.ok(dataScopeService.resolveDataScope(userId, tenantId, permissionCode));
    }
}
