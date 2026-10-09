package com.omni.auth.catalog;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块 provisioning seed ID 解析后的不可变目录。
 *
 * @param seedsById 按 seed ID 索引的定义
 */
public record ProvisioningSeedCatalog(Map<String, SeedDefinition> seedsById) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 固化 seed 索引。
     */
    public ProvisioningSeedCatalog {
        seedsById = seedsById == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(seedsById));
    }

    /**
     * 返回目录中声明的全部角色自然键。
     *
     * @return 去重且保持模块声明顺序的角色编码
     */
    public List<String> roleCodes() {
        return seedsById.values().stream()
                .flatMap(seed -> seed.roleCodes().stream())
                .distinct()
                .toList();
    }

    /**
     * 单条种子定义。
     *
     * @param id        seed ID
     * @param moduleId  所属模块 ID
     * @param roleCodes 需要从默认租户克隆的角色自然键
     */
    public record SeedDefinition(String id, String moduleId, List<String> roleCodes) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 固化角色编码列表。
         */
        public SeedDefinition {
            roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        }
    }
}
