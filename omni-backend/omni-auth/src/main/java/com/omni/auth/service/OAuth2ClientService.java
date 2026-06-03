package com.omni.auth.service;

import com.omni.auth.dto.CreateOAuth2ClientRequest;
import com.omni.auth.dto.OAuth2ClientVO;
import com.omni.auth.dto.UpdateOAuth2ClientRequest;
import com.omni.common.core.result.PageResult;

/**
 * OAuth2 客户端管理服务接口。
 * <p>提供 OAuth2 客户端的 CRUD 操作，底层使用 Spring Authorization Server 的
 * {@code RegisteredClientRepository} 和 MyBatis-Plus 进行分页查询。</p>
 */
public interface OAuth2ClientService {

    /**
     * 分页查询已注册的 OAuth2 客户端列表。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页结果
     */
    PageResult<OAuth2ClientVO> listClients(int page, int size);

    /**
     * 根据内部 ID 获取单个客户端详情。
     *
     * @param id 客户端内部 ID
     * @return 客户端视图对象
     */
    OAuth2ClientVO getClient(String id);

    /**
     * 创建新的 OAuth2 客户端。
     *
     * @param request 创建请求参数
     * @return 创建后的客户端视图对象
     */
    OAuth2ClientVO createClient(CreateOAuth2ClientRequest request);

    /**
     * 更新已有的 OAuth2 客户端。
     *
     * @param id      客户端内部 ID
     * @param request 更新请求参数
     * @return 更新后的客户端视图对象
     */
    OAuth2ClientVO updateClient(String id, UpdateOAuth2ClientRequest request);

    /**
     * 删除指定的 OAuth2 客户端。
     *
     * @param id 客户端内部 ID
     */
    void deleteClient(String id);
}
