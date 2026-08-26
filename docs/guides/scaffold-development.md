# 项目预设、轻量模式、服务创建与 CRUD 生成教程

本指南面向使用 Omni-Stack 快速创建新项目的开发者。优先使用 CLI 和模块目录，不手工复制现有服务后全局替换字符串。

## 1. 选择项目预设

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

预设决定需要的后端模块、前端页面、种子、迁移、Compose profile 和外部依赖。`supply-chain` 包含 SRM、Procurement 和 Asset；单领域项目选择更小预设可以减少启动时间和认知负担。

详细差异见 [预设快速选择](../preset-quick-selection.md) 和 [依赖矩阵](../preset-dependency-matrix.md)。

## 2. 轻量开发模式

CLI 开发命令支持服务选择、依赖规划和轻量启动。先运行 doctor：

```bash
npm --prefix tools/omni-cli run dev -- doctor
npm --prefix tools/omni-cli run dev -- dev plan --preset crm
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

计划输出是本次运行的事实来源。缺少的 MQ、Workflow 或调度集成必须显式关闭，不能让服务因未选择的依赖反复重试。

## 3. 创建新服务

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

生成器会建立 Maven 模块、应用入口、配置、健康检查、Docker 集成、前端/权限/文档集成计划和锁文件。执行前审查计划，执行后运行：

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

新服务含 `@RequestBody` 时必须实现 `XssConfigProvider`。Servlet 业务服务使用 `omni-common-service`，不要复制 Gateway reactive 安全链。

## 4. 生成 CRUD

先编写声明式规格，再预览：

```bash
npm --prefix tools/omni-cli run dev -- crud plan path/to/spec.yaml
npm --prefix tools/omni-cli run dev -- crud generate path/to/spec.yaml
```

生成内容包括前后端类型、接口、服务层、数据层、权限、迁移、测试和国际化待办。生成后仍必须人工补充领域状态机、数据范围映射、幂等和业务校验；CRUD 生成器不替代领域设计。

## 5. 数据库与权限

- Schema 变化只新增 forward-only Liquibase changeSet。
- 种子 SQL 只保留稳定基础数据。
- 新写接口同步更新 `scripts/sql/seed/auth.sql`。
- 刷新 `database/seed/manifest.yaml` 摘要与断言。
- fresh 和 upgrade 两条路径都要通过。

不要新增 `migrate-*.sql`、临时修复 SQL 或手工执行顺序说明作为正式交付。

## 6. 自定义预设

自定义预设必须声明依赖闭包、互斥条件、前后端裁剪范围、迁移和种子选择。维护者应执行五预设黄金矩阵并更新锁文件。完整步骤见 [自定义预设教程](../custom-preset-tutorial.md) 和 [预设维护说明](../preset-maintenance.md)。

## 7. 完成定义

新模块只有同时满足以下条件才算完成：

- JDK 25 Reactor 构建通过。
- 前端 lint 0 warning 且 production build 通过。
- 权限和数据范围有负向测试。
- fresh/upgrade 和目标预设通过。
- 文档、四语言键和截图清单已更新。
- 没有残留临时脚本、生成目录或测试凭据。

