package com.omni.common.service.identity;

/**
 * 当前 Servlet 业务请求的不可变身份快照。
 *
 * @param userId 用户 ID
 * @param tenantId 租户 ID
 * @param username 用户名
 * @author Omni-Stack Team
 */
public record ServiceRequestIdentity(Long userId, Long tenantId, String username) {
}
