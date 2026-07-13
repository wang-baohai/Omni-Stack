package com.omni.auth.service;

import com.omni.auth.dto.BlacklistRuleVO;
import com.omni.auth.dto.CreateXssRuleRequest;
import com.omni.auth.dto.UpdateXssRuleRequest;
import com.omni.auth.dto.XssSettingsVO;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.security.XssSettings;

/**
 * XSS 防护配置服务接口，提供租户级 XSS 全局开关和黑名单规则的管理操作。
 * <p>支持获取 XSS 设置、切换全局开关、黑名单规则的 CRUD 和启用状态切换。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.dto.XssSettingsVO
 * @see com.omni.auth.dto.BlacklistRuleVO
 */
public interface XssConfigService {

    /**
     * 获取指定租户的 XSS 防护设置（全局开关 + 全部规则列表）。
     *
     * @param tenantId 租户 ID
     * @return XSS 防护设置视图对象
     */
    XssSettingsVO getSettings(Long tenantId);

    /**
     * 直接从数据库获取指定租户的权威运行时 XSS 设置。
     * <p>该方法不读取 Redis，确保显式关闭状态不会与缓存未命中混淆。</p>
     *
     * @param tenantId 租户 ID
     * @return 仅包含启用规则的运行时设置
     */
    XssSettings getAuthoritativeSettings(Long tenantId);

    /**
     * 切换 XSS 防护全局开关。
     *
     * @param tenantId 租户 ID
     * @param enabled  是否启用
     * @param operator 操作人
     */
    void toggleGlobal(Long tenantId, boolean enabled, String operator);

    /**
     * 分页查询黑名单规则列表。
     *
     * @param tenantId 租户 ID
     * @param page     页码
     * @param size     每页大小
     * @return 黑名单规则分页结果
     */
    PageResult<BlacklistRuleVO> listRules(Long tenantId, int page, int size);

    /**
     * 创建黑名单规则。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @param operator 操作人
     * @return 创建的规则视图对象
     */
    BlacklistRuleVO createRule(Long tenantId, CreateXssRuleRequest request, String operator);

    /**
     * 更新黑名单规则。
     *
     * @param id      规则 ID
     * @param request 更新请求
     * @param operator 操作人
     * @return 更新后的规则视图对象
     */
    BlacklistRuleVO updateRule(Long id, UpdateXssRuleRequest request, String operator);

    /**
     * 删除黑名单规则。
     *
     * @param id 规则 ID
     */
    void deleteRule(Long id);

    /**
     * 切换单条黑名单规则的启用状态。
     *
     * @param id      规则 ID
     * @param enabled 是否启用
     */
    void toggleRule(Long id, boolean enabled);
}
