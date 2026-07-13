package com.omni.auth.controller.internal;

import com.omni.auth.service.InternalDirectoryService;
import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内部 API 控制器，供其他微服务通过 Feign 调用。
 * <p>不经过 Gateway，仅通过 {@code X-Internal-Token} 认证。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    /** 内部目录查询服务 */
    private final InternalDirectoryService internalDirectoryService;

    /**
     * 根据用户 ID 获取用户基本信息。
     *
     * @param id       用户 ID
     * @param tenantId 租户 ID
     * @return 用户 DTO
     */
    @GetMapping("/users/{id}")
    public R<InternalUserDTO> getUserById(@PathVariable Long id,
                                          @RequestParam Long tenantId) {
        return R.ok(internalDirectoryService.getUserById(id, tenantId));
    }

    /**
     * 批量获取用户基本信息。
     *
     * @param ids      用户 ID 列表（逗号分隔）
     * @param tenantId 租户 ID
     * @return 用户 DTO 列表
     */
    @GetMapping("/users/batch")
    public R<List<InternalUserDTO>> getUsersByIds(@RequestParam List<Long> ids,
                                                   @RequestParam Long tenantId) {
        return R.ok(internalDirectoryService.getUsersByIds(ids, tenantId));
    }

    /**
     * 搜索租户内启用的负责人候选用户。
     * <p>仅返回负责人选择所需的最小字段，且最多返回 100 条。</p>
     *
     * @param tenantId 租户 ID
     * @param keyword  用户名或昵称关键字，可为空
     * @param limit    最大返回数量
     * @return 用户候选项列表
     */
    @GetMapping("/users/options")
    public R<List<InternalUserOptionDTO>> searchUserOptions(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(internalDirectoryService.searchEnabledUserOptions(tenantId, keyword, limit));
    }

    /**
     * 根据组织单元 ID 获取组织信息。
     *
     * @param id       组织单元 ID
     * @param tenantId 租户 ID
     * @return 组织 DTO
     */
    @GetMapping("/orgs/{id}")
    public R<InternalOrgDTO> getOrgById(@PathVariable Long id,
                                        @RequestParam Long tenantId) {
        return R.ok(internalDirectoryService.getOrgById(id, tenantId));
    }

    /**
     * 批量获取组织单元信息。
     *
     * @param ids      组织单元 ID 列表（逗号分隔）
     * @param tenantId 租户 ID
     * @return 组织 DTO 列表
     */
    @GetMapping("/orgs/batch")
    public R<List<InternalOrgDTO>> getOrgsByIds(@RequestParam List<Long> ids,
                                                 @RequestParam Long tenantId) {
        return R.ok(internalDirectoryService.getOrgsByIds(ids, tenantId));
    }
}
