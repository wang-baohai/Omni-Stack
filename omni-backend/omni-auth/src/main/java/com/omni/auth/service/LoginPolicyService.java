package com.omni.auth.service;

import com.omni.auth.dto.AuthLoginPolicyVO;
import com.omni.auth.dto.LoginPolicyUpdateRequest;

/**
 * 登录保护策略服务接口（D01）。
 *
 * @author Omni-Stack Team
 */
public interface LoginPolicyService {

    /**
     * 读取本租户登录保护策略；无持久化行时返回默认视图，不写库。
     *
     * @param tenantId 租户 ID
     * @return 策略视图
     */
    AuthLoginPolicyVO getPolicy(Long tenantId);

    /**
     * 更新本租户登录保护策略（乐观并发）。成功返回新视图；陈旧版本抛出 code409。
     * 配置保存与 AUTH_CONFIG_UPDATED 安全审计在同一事务内完成。
     *
     * @param tenantId  租户 ID
     * @param request   更新请求
     * @param operator  可信操作者用户名
     * @return 更新后的策略视图
     */
    AuthLoginPolicyVO updatePolicy(Long tenantId, LoginPolicyUpdateRequest request, String operator);
}
