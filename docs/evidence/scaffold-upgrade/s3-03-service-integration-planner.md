# S3-03 服务结构化接入 Planner 证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

代码提交：`762221b feat(scaffold): add read-only service integration planner`

## 1. 当前结论

CLI 0.3.0 已提供 `omni service integrate <service-id> --source <generated-package>` 的只读
planner。它校验服务包锁文件后，解析当前 monorepo 的 Maven XML、Gateway/Compose/catalog/seed
manifest YAML、前端 TypeScript AST、权限 SQL 和 Dockerfile，生成精确接入计划但不写入文件。

真实 `inventory-sample` 服务包输出 16 项操作，覆盖后端模块、父 POM、Gateway、Compose、catalog、
Docker POM 缓存层、前端 API/View/menu/router/zh-CN/en-US、权限 seed、manifest 和维护文档。Git
目标文件清洁检查通过，planner 报告 `ready`。

## 2. 安全门

- Maven POM 使用 `fast-xml-parser` 解析，不依赖未校验的字符串查找。
- Gateway、Compose、catalog 和 manifest 禁止重复 YAML key，并校验目标节点类型。
- menu、router 和 locale 使用 TypeScript AST 定位对象及现有 key。
- 生成树的每个源文件必须存在，目标文件或目录已存在即冲突。
- route id、API Path、Compose service、catalog id、菜单、图标和翻译 key 重复均阻断。
- CLI 默认通过 `git status --porcelain -- <targets>` 阻断目标脏文件；该检查没有用户参数可关闭。
- 单元测试只能通过内部函数参数隔离 Git 子进程，并显式产生 warning，不改变生产命令默认值。

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
CLI：0.3.0
Tests：11 passed, 0 failed
新增覆盖：XML/YAML/TypeScript 全部结构目标解析，16 项只读接入计划
```

```text
命令：npm audit --json
生产依赖：16
漏洞：0 low / 0 moderate / 0 high / 0 critical
```

生成模板的菜单契约已同步修正为三层：`<id>` DIRECTORY、`<id>:overview` MENU、`<id>:read`
API；View 位于 `views/<id>/overview/index.vue`，符合动态路由约定。带连字符的 locale 根 key 使用
字符串字面量，生成的 locale/menu 片段均通过 TypeScript 语法解析测试。

## 5. 后续条件

下一子层在内存中构建 16 项目标内容并全部后置解析校验；之后才允许跨文件事务写入。写入必须使用
同目录临时文件、原文件备份、失败逆序回滚和最终备份清理。S3-03 在写入、回滚、幂等、构建和
Compose 校验完成前仍未验收。
