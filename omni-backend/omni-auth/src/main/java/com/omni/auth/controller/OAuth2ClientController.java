package com.omni.auth.controller;

import com.omni.auth.dto.CreateOAuth2ClientRequest;
import com.omni.auth.dto.OAuth2ClientVO;
import com.omni.auth.dto.UpdateOAuth2ClientRequest;
import com.omni.auth.service.OAuth2ClientService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2 客户端管理控制器。
 *
 * <p>提供 OAuth2 已注册客户端的增删改查接口，路径映射在 {@code /api/auth/oauth2-client}。</p>
 * <p>需要认证后才能访问（不在 Gateway AuthFilter 白名单中）。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET    /api/auth/oauth2-client/list} — 分页查询客户端（权限码：{@code system:oauth2:list}）</li>
 *   <li>{@code GET    /api/auth/oauth2-client/{id}} — 客户端详情（权限码：{@code system:oauth2:list}）</li>
 *   <li>{@code POST   /api/auth/oauth2-client} — 创建客户端（权限码：{@code system:oauth2:create}）</li>
 *   <li>{@code PUT    /api/auth/oauth2-client/{id}} — 更新客户端（权限码：{@code system:oauth2:update}）</li>
 *   <li>{@code DELETE /api/auth/oauth2-client/{id}} — 删除客户端（权限码：{@code system:oauth2:delete}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see OAuth2ClientService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth2-client")
@RequiredArgsConstructor
public class OAuth2ClientController {

    /** OAuth2 客户端管理服务 */
    private final OAuth2ClientService oAuth2ClientService;

    /**
     * 分页查询已注册的 OAuth2 客户端列表。
     *
     * <!-- 权限码: system:oauth2:list -->
     *
     * @param page 页码（默认 1）
     * @param size 每页数量（默认 10）
     * @return 分页客户端列表 {@code R<PageResult<OAuth2ClientVO>>}
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:oauth2:list')")
    public R<PageResult<OAuth2ClientVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(oAuth2ClientService.listClients(page, size));
    }

    /**
     * 获取单个 OAuth2 客户端详情。
     *
     * <!-- 权限码: system:oauth2:list -->
     *
     * @param id 客户端内部 ID（路径变量）
     * @return 客户端详情 {@code R<OAuth2ClientVO>}，不存在时返回错误
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:oauth2:list')")
    public R<OAuth2ClientVO> get(@PathVariable String id) {
        OAuth2ClientVO client = oAuth2ClientService.getClient(id);
        if (client == null) {
            return R.fail("客户端不存在");
        }
        return R.ok(client);
    }

    /**
     * 创建新的 OAuth2 客户端。
     *
     * <!-- 权限码: system:oauth2:create -->
     *
     * @param request 创建请求参数（含客户端 ID、密钥、授权类型、回调地址等）
     * @return 创建后的客户端详情 {@code R<OAuth2ClientVO>}
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:oauth2:create')")
    public R<OAuth2ClientVO> create(@Valid @RequestBody CreateOAuth2ClientRequest request) {
        return R.ok(oAuth2ClientService.createClient(request));
    }

    /**
     * 更新已有的 OAuth2 客户端。
     *
     * <!-- 权限码: system:oauth2:update -->
     *
     * @param id      客户端内部 ID（路径变量）
     * @param request 更新请求参数
     * @return 更新后的客户端详情 {@code R<OAuth2ClientVO>}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:oauth2:update')")
    public R<OAuth2ClientVO> update(@PathVariable String id,
                                    @Valid @RequestBody UpdateOAuth2ClientRequest request) {
        return R.ok(oAuth2ClientService.updateClient(id, request));
    }

    /**
     * 删除指定的 OAuth2 客户端。
     *
     * <!-- 权限码: system:oauth2:delete -->
     *
     * @param id 客户端内部 ID（路径变量）
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:oauth2:delete')")
    public R<Void> delete(@PathVariable String id) {
        oAuth2ClientService.deleteClient(id);
        return R.ok(null);
    }
}
