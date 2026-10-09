package com.omni.common.service.config;

import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.Set;

/**
 * 对已启用的公共安全能力执行失败关闭配置校验。
 *
 * @author Omni-Stack Team
 */
public class ServiceConfigurationValidator implements InitializingBean {

    private static final int MIN_INTERNAL_TOKEN_LENGTH = 32;
    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
            "required_env_value", "changeme", "change-me", "default");

    private final ServiceIdentityProperties properties;

    /**
     * 创建配置校验器。
     *
     * @param properties 服务属性
     */
    public ServiceConfigurationValidator(ServiceIdentityProperties properties) {
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override
    public void afterPropertiesSet() {
        if (!anyFeatureEnabled()) {
            return;
        }
        requireText(properties.getName(), "omni.service.name");
        requireText(properties.getDisplayName(), "omni.service.display-name");
        validatePaths(properties.getManagementPaths(), "management-paths");
        validatePaths(properties.getPublicPaths(), "public-paths");
        validatePaths(properties.getInternalPaths(), "internal-paths");
        if (properties.getInternalApi().isEnabled()
                || properties.getDataScope().isEnabled()
                || properties.getXss().isEnabled()) {
            String token = properties.getInternalApi().getToken();
            requireText(token, "omni.service.internal-api.token");
            if (token.length() < MIN_INTERNAL_TOKEN_LENGTH
                    || FORBIDDEN_TOKENS.contains(token.toLowerCase())) {
                throw new IllegalStateException("omni.service.internal-api.token 必须为至少 32 位的非默认密钥");
            }
        }
    }

    private boolean anyFeatureEnabled() {
        return properties.getGatewayPreauth().isEnabled()
                || properties.getInternalApi().isEnabled()
                || properties.getTenant().isEnabled()
                || properties.getDataScope().isEnabled()
                || properties.getXss().isEnabled();
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 不能为空");
        }
    }

    private void validatePaths(List<String> paths, String name) {
        if (paths == null || paths.stream().anyMatch(path -> path == null || !path.startsWith("/"))) {
            throw new IllegalStateException("omni.service." + name + " 必须全部为绝对路径前缀");
        }
    }
}
