package com.omni.common.service.identity;

import com.omni.common.service.config.ServiceIdentityProperties;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 公共、管理和内部路径判定器。
 *
 * @author Omni-Stack Team
 */
@RequiredArgsConstructor
public class ServicePathPolicy {

    private final ServiceIdentityProperties properties;

    /**
     * 判断是否为管理或公开路径。
     *
     * @param uri 请求路径
     * @return 是否无需业务身份
     */
    public boolean isPublicOrManagement(String uri) {
        return matches(uri, properties.getManagementPaths()) || matches(uri, properties.getPublicPaths());
    }

    /**
     * 判断是否为内部 API 路径。
     *
     * @param uri 请求路径
     * @return 是否为内部路径
     */
    public boolean isInternal(String uri) {
        return matches(uri, properties.getInternalPaths());
    }

    private boolean matches(String uri, List<String> prefixes) {
        return uri != null && prefixes != null && prefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .anyMatch(uri::startsWith);
    }
}
