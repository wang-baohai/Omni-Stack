# S3-03 服务结构化接入与原子事务证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

代码提交：

- `762221b feat(scaffold): add read-only service integration planner`
- `ff4e264 feat(scaffold): render validated service integration changes`
- `e862d84 refactor(database): derive application grants from migration catalog`
- `89c30d2 feat(scaffold): apply atomic service integration`

## 1. 当前结论

CLI 0.5.0 已提供 `omni service integrate <service-id> --source <generated-package>`。默认模式只读
渲染；追加 `--apply` 后，才会将已完成全部后置校验的变更作为跨文件事务写入。它校验服务包锁
文件后，解析当前 monorepo 的 Maven XML、Gateway/Compose/catalog/seed manifest/Liquibase YAML、
前端 TypeScript AST、迁移器 Java 目录、权限 SQL 和 Dockerfile。

真实 `inventory-sample` 服务包输出 19 项操作和 30 个文件变更，除原有后端、前端、基础设施、
权限与文档接入外，还创建服务 Liquibase 主文件、登记平台建库 SQL 和 DB Migrator 迁移目标。Git
目标文件清洁检查通过，planner 报告 `ready`，默认 dry-run 确认未写入工作区。

## 2. 安全门

- Maven POM 使用 `fast-xml-parser` 解析，不依赖未校验的字符串查找。
- Gateway、Compose、catalog 和 manifest 禁止重复 YAML key，并校验目标节点类型。
- menu、router 和 locale 使用 TypeScript AST 定位对象及现有 key。
- 生成树的每个源文件必须存在，目标文件或目录已存在即冲突。
- route id、API Path、Compose service、catalog id、菜单、图标和翻译 key 重复均阻断。
- CLI 默认通过 `git status --porcelain -- <targets>` 阻断目标脏文件；该检查没有用户参数可关闭。
- 单元测试只能通过内部函数参数隔离 Git 子进程，并显式产生 warning，不改变生产命令默认值。
- 写入前逐文件比对渲染时内容，拒绝并发漂移、已有创建目标和任意符号链接祖先。
- 全部新内容先写入目标同目录临时文件；修改目标先原子改名为备份，再原子替换。
- 任意一步失败均逆序恢复原文件、删除新文件和临时文件，只删除事务创建且仍为空的目录。
- 写后逐文件按完整内容复核；成功后清理备份，清理异常作为明确 warning 返回。

## 3. 权限与新租户决策

`AuthTenantTemplateProvisioner` 会汇总 `modules.yaml` 中全部 `permissionRoots`，从默认租户按自然键
克隆权限树，并把 SUPER_ADMIN 的模板关联映射到新租户。因此生成服务应：

1. 在新模块 catalog 条目中声明 permission root。
2. 新模块没有领域默认数据时保持 `tenantProvisioning: none` 和空 `provisioningSeedIds`。
3. 默认租户权限使用 tenant + permissionCode 的 `NOT EXISTS` 幂等 SQL，不分配固定 ID。
4. 新权限自然键断言挂到 Auth 模块的 provisioningSeedIds 和 manifest，而不是伪造新服务事件消费者。

该设计保持 `ModuleCatalogContractTest` 的 seed/assertion 一一对应，同时避免让租户初始化等待一个没有
领域数据可初始化的空模块。

## 4. 自动化与依赖审计

```text
命令：cd tools/omni-cli && npm test
CLI：0.5.0
Tests：14 passed, 0 failed
新增覆盖：19 项计划、30 文件内存渲染、完整写入、第五文件故障注入与全量回滚
```

```text
命令：npm audit --json
生产依赖：16
漏洞：0 low / 0 moderate / 0 high / 0 critical
```

生成模板的菜单契约已同步修正为三层：`<id>` DIRECTORY、`<id>:overview` MENU、`<id>:read`
API；View 位于 `views/<id>/overview/index.vue`，符合动态路由约定。带连字符的 locale 根 key 使用
字符串字面量，生成的 locale/menu 片段均通过 TypeScript 语法解析测试。

```text
命令：cd tools/omni-cli && npm run test:golden
结果：JDK 25 编译 8 个生成源文件，1 个契约测试通过，BUILD SUCCESS
生成锁文件：26 个文件（含服务 Liquibase 主文件）
```

```text
命令：cd omni-backend && .\mvnw.cmd -pl omni-db-migrator -am test
结果：21 passed, 0 failed，BUILD SUCCESS
```

DB Migrator 的业务账号授权已改为从 `MigrationTargetCatalog` 动态筛选非 vendor 目标，不再维护
第二份数据库名清单。目录测试校验固定核心目标、ID 唯一和两个 vendor，但允许脚手架追加业务库。

## 5. S3-03 结论

S3-03 的 planner、完整渲染、Git 清洁门、数据库接入、事务写入、故障回滚、黄金编译和依赖审计
已经闭环。尚未执行的是把示例服务真正保留在仓库中；这不是 S3-03 的交付物，CLI 真实复验使用的
临时生成包已在验证后清理，工作区没有残留示例模块或事务临时文件。
