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

`dev` 只有 `up`、`down`、`status`、`doctor`、`logs` 五个子命令；没有 `dev plan` 子命令，计划由 `dev up` 在调用 Docker 前打印。先运行 doctor：

```bash
npm --prefix tools/omni-cli run dev -- dev doctor
npm --prefix tools/omni-cli run dev -- dev up --preset crm
npm --prefix tools/omni-cli run dev -- dev status
npm --prefix tools/omni-cli run dev -- dev logs --module crm --tail 200
npm --prefix tools/omni-cli run dev -- dev down
```

`dev up` 依次打印 `development preset|module: <id>`、`mode: lite|full`、`modules:`、`services:`、`observability:` 与降级行，这段输出是本次运行的事实来源。模式默认按预设/模块推断（`core`、`workflow`、`crm` 为 lite），也可用 `--mode lite|full` 对同一预设显式切换，以便两种模式比较同一契约；包含 SRM/Procurement/Asset 的闭包会拒绝 lite，而不是静默省略实时消费者。lite 显式关闭 Nacos 客户端、XXL-JOB、MQ relay 与自动探测（Outbox 仍然写入），full 不再由 CLI 合成这些开关，`.env` 保持权威。

多行并行隔离用 `--project-name`（Compose project 名，容器/网络/卷按它命名）和 `--set NAME=VALUE`（只允许 Compose 已声明的宿主端口与命名卷名，例如 `OMNI_MYSQL_HOST_PORT=23306`、`OMNI_MYSQL_VOLUME_NAME=wp011-row1-mysql`；Secret 一律拒绝，值不进入日志）。

命名卷默认保留。`dev down` 只有同时给出 `--volumes`、`--confirm-delete-volumes` 和 `--project-name` 才会删卷，并先校验每个卷的 `com.docker.compose.project` 标签确属该 project；不属于就中止，因为 Compose 里的 `omni-stack-mysql-data` 等固定卷名会跨 project 共享。缺省不指定 project 时由 Compose 依目录名推导，删卷路径直接不可用。

## 3. 创建新服务

`create-service` **默认仅预览（preview）**，不会写入任何文件。先审查计划：

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

预览输出的资源/集成清单确认无误后，**必须显式加 `--apply`** 才执行原子写入并建立模块：

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory --apply
```

`--apply` 才会生成 Maven 模块、应用入口、配置、健康检查、Docker 集成、前端/权限/文档集成与锁文件（默认写入仓库当前工作区；若用 `--output` 指定目录，后续构建/集成命令的 cwd 需相应调整）。未跑 `--apply` 前模块尚不存在，直接编译会失败。生成后执行：

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

新服务含 `@RequestBody` 时必须实现 `XssConfigProvider`。Servlet 业务服务使用 `omni-common-service`，不要复制 Gateway reactive 安全链。

## 4. 生成 CRUD

先编写声明式规格，再预览。CRUD 命令是 `generate crud --spec <file>`，默认只读预览（不存在 `crud plan`/`crud generate` 子命令）：

```bash
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --check
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --dry-run
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --apply
```

`--check` 只校验声明，`--dry-run` 渲染变更计划，`--apply` 才跨文件原子写入；`--apply` 与 `--dry-run` 互斥。

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
- `omni dev doctor` 无 FAIL（未启动容器时的 SKIP 不算失败，但也不构成交付证据）。
- 前端 lint 0 warning 且 production build 通过。
- 权限和数据范围有负向测试。
- fresh/upgrade 和目标预设通过。
- 文档、四语言键和截图清单已更新。
- 没有残留临时脚本、生成目录或测试凭据。

