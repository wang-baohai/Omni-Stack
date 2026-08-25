# S4-05～S4-07 轻量开发模式验收证据

验收日期：2026-08-25
工作分支：`codex/scaffold-upgrade`

## 1. 实现范围

- 根 Compose 拆分为 `compose.yaml`、`compose.infra.yaml`、`compose.apps.yaml`，同一服务只定义一次。
- 建立 `core`、`workflow`、`crm`、`supply-chain`、`full` profiles；观测栈由 WP-07 接入。
- Omni CLI 增加 `dev up/down/status/doctor/logs` 与 `test deps`。
- lite 模式显式关闭 XXL-JOB、MQ relay/consumer 与 Nacos 客户端，保留 Outbox 本地事务写入。
- 8 个后端应用增加 `application-lite.yml`，通过 `SimpleDiscoveryClient` 提供静态服务解析。
- MQ 消息页显示 `OUTBOX_ONLY` 降级状态，并在未启用异步投递时禁用重发。
- Windows 启停脚本不再要求管理员权限，也不再修改系统端口保留策略。

## 2. 静态与单元验证

| 检查 | 结果 |
|---|---|
| Omni CLI build + test | 39/39 通过（新增开发组合、删除卷确认、doctor、静态发现检查） |
| 五预设结构生成 | core/workflow/crm/supply-chain/full 全部通过 |
| 五 profiles `docker compose config --quiet` | 全部通过 |
| Profile 服务数 | core 8、workflow 9、crm 9、supply-chain 15、full 16 |
| common-job / common-mqlog / base reactor | 9 模块通过；XXL、relay、MQ 运行状态测试通过 |
| 前端 ESLint | 0 error、0 warning |
| 前端 TypeScript + Vite | 2448 modules，构建通过 |
| `omni test deps --module crm` | 10 模块 reactor 通过；CRM 45 tests，0 failure、4 个数据库条件测试按设计跳过 |

## 3. core-lite 隔离运行

测试使用独立 Compose 项目、专用 MySQL 卷和临时空闲端口，不接触默认开发栈。

实际容器共 7 个：

- `mysql`、`redis`；
- `omni-db-migrator`（退出码 0）；
- `omni-auth`、`omni-base`、`omni-gateway`（healthy）；
- `omni-frontend`。

明确未启动：Nacos、RocketMQ、XXL-JOB、Workflow、CRM、SRM、Procurement、Asset。

HTTP 验证：

- `/login`：200；
- `/api/auth/captcha` 经 Frontend → Gateway → Auth：200；
- Auth/Base/Gateway `/actuator/health`：全部 200。

另以独立项目执行 `omni dev up --module frontend`：在本地 `.env` 缺少后端 Secret 的情况下只创建 `omni-frontend` 一个容器，`/login` 返回 200，随后由 `omni dev down` 完整清理。inactive 服务的插值占位不会注入前端容器。

数据卷生命周期：

1. `omni dev down` 后专用 MySQL 卷仍存在；
2. 未确认的删除请求由单元测试拒绝；
3. `omni dev down --volumes --confirm-delete-volumes` 后专用测试卷删除；
4. 隔离容器、网络和测试卷均已清理。

## 4. full 回归

命令：`npm run test:preset-runtime -- --presets=full --skip-build`

结果：

- fresh 数据库迁移通过；
- 16 个 Compose 服务达到预期终态；
- 登录、动态菜单、基础字典、伪造身份头拒绝、JWT 租户身份不可被客户端头覆盖全部通过；
- Playwright 18/18 通过，覆盖公开入口、无预填凭据、Trace ID、各业务模块、用户任务、员工 403 与供应商窄屏门户；
- 生成目录、临时令牌文件、隔离容器、网络和卷由脚本自动清理。

## 5. doctor 现场结果

doctor 已正确通过 Node 24.19、JDK 25、Docker Compose 5.3.1、Docker daemon、磁盘、模块目录和默认端口检查，并且只输出环境变量名称、不输出值。

当前开发者本地 `.env` 是升级前版本，doctor 按失败关闭原则报告缺少数据库、Redis、Nacos 和 XXL-JOB 的 9 个变量名；仓库 `.env.example` 已补齐。该结果证明安全门有效，不属于代码验收失败。
