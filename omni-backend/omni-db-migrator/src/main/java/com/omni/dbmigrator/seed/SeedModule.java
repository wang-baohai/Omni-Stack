package com.omni.dbmigrator.seed;

/**
 * 种子数据所属模块声明。
 *
 * @param id               稳定模块 ID
 * @param enabledByDefault 当前默认完整预设是否启用
 */
public record SeedModule(String id, boolean enabledByDefault) {
}
