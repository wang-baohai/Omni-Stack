package com.omni.common.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Servlet 业务服务公共安全属性。
 *
 * @author Omni-Stack Team
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "omni.service")
public class ServiceIdentityProperties {

    /** 服务注册名。 */
    private String name;

    /** 面向用户的服务名称。 */
    private String displayName;

    /** 无需 Gateway 身份的管理路径前缀。 */
    private List<String> managementPaths = new ArrayList<>(List.of("/actuator", "/error"));

    /** 无需登录身份的公开路径前缀。 */
    private List<String> publicPaths = new ArrayList<>();

    /** 内部服务调用路径前缀。 */
    private List<String> internalPaths = new ArrayList<>(List.of("/api/internal/", "/internal/"));

    /** Gateway 预认证配置。 */
    private GatewayPreauth gatewayPreauth = new GatewayPreauth();

    /** 内部 API 配置。 */
    private InternalApi internalApi = new InternalApi();

    /** 租户 SQL 拦截配置。 */
    private Tenant tenant = new Tenant();

    /** 数据权限配置。 */
    private DataScope dataScope = new DataScope();

    /** XSS 设置配置。 */
    private Xss xss = new Xss();

    /** Gateway 预认证配置。 */
    @Getter
    @Setter
    public static class GatewayPreauth {

        /** 是否启用。 */
        private boolean enabled;
    }

    /** 内部 API 配置。 */
    @Getter
    @Setter
    public static class InternalApi {

        /** 是否启用。 */
        private boolean enabled;

        /** 内部服务共享密钥。 */
        private String token;
    }

    /** 租户 SQL 拦截配置。 */
    @Getter
    @Setter
    public static class Tenant {

        /** 是否启用。 */
        private boolean enabled;
    }

    /** 数据权限配置。 */
    @Getter
    @Setter
    public static class DataScope {

        /** 是否启用。 */
        private boolean enabled;
    }

    /** XSS 设置配置。 */
    @Getter
    @Setter
    public static class Xss {

        /** 是否启用 Auth 回源与安全基线。 */
        private boolean enabled;
    }
}
