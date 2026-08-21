package com.omni.dbmigrator.seed;

/**
 * 正式种子资源及不可变摘要。
 *
 * @param id       稳定种子源 ID
 * @param module   所属模块
 * @param resource classpath 资源路径
 * @param sha256   文件 SHA-256
 */
public record SeedSource(String id, String module, String resource, String sha256) {
}
