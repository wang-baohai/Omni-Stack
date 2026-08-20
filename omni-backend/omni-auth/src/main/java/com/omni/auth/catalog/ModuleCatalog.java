package com.omni.auth.catalog;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 脚手架模块目录的不可变运行时模型。
 *
 * @param version 目录协议版本
 * @param modules 按依赖拓扑排序的模块列表
 */
public record ModuleCatalog(String version, List<ModuleDefinition> modules) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 固化模块列表，避免启动后被调用方修改。
     */
    public ModuleCatalog {
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    /**
     * 返回需要通过事件执行租户初始化的模块 ID。
     *
     * @return 按依赖顺序排列的模块 ID
     */
    public List<String> eventProvisioningModuleIds() {
        return modules.stream()
                .filter(module -> TenantProvisioningMode.EVENT == module.tenantProvisioning())
                .map(ModuleDefinition::id)
                .toList();
    }

    /**
     * 返回由 Auth 本地事务完成租户初始化的模块 ID。
     *
     * @return 按依赖顺序排列的模块 ID
     */
    public List<String> localProvisioningModuleIds() {
        return modules.stream()
                .filter(module -> TenantProvisioningMode.LOCAL == module.tenantProvisioning())
                .map(ModuleDefinition::id)
                .toList();
    }

    /**
     * 单个脚手架模块定义。
     *
     * @param id                      稳定模块 ID
     * @param kind                    模块类别
     * @param dependencies            直接依赖模块 ID
     * @param tenantProvisioning      租户初始化模式
     * @param permissionRoots         权限树根编码
     * @param provisioningSeedIds     初始化种子断言 ID
     */
    public record ModuleDefinition(
            String id,
            String kind,
            List<String> dependencies,
            TenantProvisioningMode tenantProvisioning,
            List<String> permissionRoots,
            List<String> provisioningSeedIds) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 固化所有集合字段。
         */
        public ModuleDefinition {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            permissionRoots = permissionRoots == null ? List.of() : List.copyOf(permissionRoots);
            provisioningSeedIds = provisioningSeedIds == null ? List.of() : List.copyOf(provisioningSeedIds);
        }
    }

    /**
     * 租户初始化执行位置。
     */
    public enum TenantProvisioningMode {
        /** Auth 本地事务执行。 */
        LOCAL,
        /** 目标服务通过可靠事件执行。 */
        EVENT,
        /** 不需要租户级初始化。 */
        NONE
    }
}
