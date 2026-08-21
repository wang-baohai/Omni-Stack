# S0-04 db-migrator 骨架证据

> 日期：2026-08-20
> 状态：历史检查点已完成；Compose one-shot、正式 changeSet 与 G1 最终结果见 [s0-08-g1.md](s0-08-g1.md)。
> Liquibase：5.0.2，由 Spring Boot 4.0.6 依赖管理锁定。

## 已交付

- Maven reactor 新增 `omni-db-migrator`，不依赖 Nacos、Redis、RocketMQ、XXL-JOB 或业务模块。
- 应用为 non-web one-shot，支持命令名：`validate`、`status`、`migrate`、`adopt-current`、`verify-seed`。
- `validate` 使用 Liquibase offline MySQL 元数据，不需要数据库凭据，不创建 `DATABASECHANGELOG`。
- `status` 只允许读取已存在 Liquibase 历史表的目标，禁止借“查询状态”隐式接管数据库。
- `migrate` 在任何变更前检查九个目标：非空且没有历史表的数据库会失败关闭，要求先备份并执行 `adopt-current`。
- `adopt-current` 和 `verify-seed` 当前显式失败，不提供不安全的空实现；分别等待 S0-06 指纹/备份门和 S0-05 seed manifest。
- `database/changelog` 已建立平台根文件和九个目标目录；平台 changeSet 只负责幂等创建数据库，业务/vendor changeSet 仍为空，不能用于生产 fresh 初始化。
- Docker 后端构建模板已识别新模块 POM。

## 验证

| 验证 | 结果 |
|---|---|
| `./mvnw -pl omni-db-migrator -am test` | 6 passed / 0 failed / 0 skipped |
| `./mvnw clean install`（JDK 25，19 模块） | BUILD SUCCESS；504 tests / 0 failures / 0 errors / 4 skipped；耗时 02:55 |
| 全部 10 个 YAML（平台 + 9 目标）offline validate | 通过，无数据库连接和写入 |
| 在 09a29fe 非空未接管库执行 `migrate` | 按预期拒绝，首个目标为 Auth |
| 拒绝后检查九库 Liquibase 历史表 | 0，证明安全门在任何写入前触发 |
| 新增文件 Secret/JWT/私钥模式扫描 | 无命中 |

## 设计说明

MySQL 在不选择默认数据库时，Liquibase 5 的 `validate()` 会尝试初始化空 catalog 下的历史表。实现因此改用 `OfflineConnection`，避免看似只读的 validate 修改环境。Fresh migrate 将以一个固定、幂等的 `omni_auth` 建库语句完成最小引导，然后平台历史保存在 `omni_auth.DATABASECHANGELOG`；在 S0-05/S0-06 完成前不会对现有数据库运行该路径。

## 当时记录的下一步（现均已在 S0-05～S0-08 完成）

1. 将 25 个旧 SQL 的结构拆入平台、七个业务、Flowable vendor、Nacos vendor、XXL-JOB vendor 和公共 MQ changeSet。
2. 建立 seed manifest 及 `verify-seed`。
3. 实现基线指纹、备份证据校验和显式确认后的 `adopt-current`。
4. fresh/upgrade 验证通过后再把 migrator 作为 Compose one-shot 启动依赖；在此之前保留旧 SQL mounts。
