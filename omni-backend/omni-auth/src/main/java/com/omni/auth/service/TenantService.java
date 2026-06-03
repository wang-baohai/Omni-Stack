package com.omni.auth.service;

import com.omni.auth.dto.TenantOption;

import java.util.List;

/**
 * 租户服务接口，提供租户相关的查询操作。
 */
public interface TenantService {

    /**
     * 查询所有活跃租户列表。
     *
     * @return 租户选项列表，供登录页租户选择器使用
     */
    List<TenantOption> listActiveTenants();
}
