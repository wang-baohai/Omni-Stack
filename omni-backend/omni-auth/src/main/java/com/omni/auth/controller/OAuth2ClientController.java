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
 * <p>提供 OAuth2 已注册客户端的增删改查接口，路径映射在 {@code /api/auth/oauth2-client}。</p>
 * <p>需要认证后才能访问（不在 Gateway AuthFilter 白名单中）。</p>
 *
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
     * @param page 页码（默认 1）
     * @param size 每页数量（默认 10）
     * @return 分页客户端列表
     */
    @GetMapping("/list")
    public R<PageResult<OAuth2ClientVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(oAuth2ClientService.listClients(page, size));
    }

    /**
     * 获取单个 OAuth2 客户端详情。
     *
     * @param id 客户端内部 ID
     * @return 客户端详情
     */
    @GetMapping("/{id}")
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
     * @param request 创建请求参数
     * @return 创建后的客户端详情
     */
    @PostMapping
    public R<OAuth2ClientVO> create(@Valid @RequestBody CreateOAuth2ClientRequest request) {
        return R.ok(oAuth2ClientService.createClient(request));
    }

    /**
     * 更新已有的 OAuth2 客户端。
     *
     * @param id      客户端内部 ID
     * @param request 更新请求参数
     * @return 更新后的客户端详情
     */
    @PutMapping("/{id}")
    public R<OAuth2ClientVO> update(@PathVariable String id,
                                    @Valid @RequestBody UpdateOAuth2ClientRequest request) {
        return R.ok(oAuth2ClientService.updateClient(id, request));
    }

    /**
     * 删除指定的 OAuth2 客户端。
     *
     * @param id 客户端内部 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        oAuth2ClientService.deleteClient(id);
        return R.ok(null);
    }
}
