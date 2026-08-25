# S4-01～S4-04 项目预设验收证据

> 验收日期：2026-08-25  
> 对应计划：WP-05 项目裁剪预设和维护说明  
> 验收范围：S4-01、S4-02、S4-03、S4-04 / G5 预设门

## 1. 验收结论

S4-01～S4-04 已完成。`core`、`workflow`、`crm`、`supply-chain`、`full` 五个正式预设均能在系统临时目录中生成独立工程，通过结构校验、后端构建、前端依赖安装、lint、生产构建、Compose 配置校验、fresh 数据库迁移、隔离启动、健康检查、登录、动态菜单和基础字典冒烟。

`full` 额外通过当前全部 18 条 Playwright E2E，包括用户任务创建、触发、产生日志和清理闭环。所有运行矩阵使用随机宿主机端口、唯一 Compose project 和唯一数据卷；结束后执行 `down -v --remove-orphans` 并删除生成目录和短期令牌文件，未操作默认开发栈。

## 2. S4-01：模块清单和五个正式预设

- 单一事实来源：`scaffold/catalog/modules.yaml`。
- 正式预设：`scaffold/presets/core.yaml`、`workflow.yaml`、`crm.yaml`、`supply-chain.yaml`、`full.yaml`。
- catalog 记录模块 ID、依赖、冲突、后端模块、前端资源、Gateway 路由、Compose 服务、数据库、权限、Nacos、端口、MQ、XXL-JOB、文档、资源提示和兼容性。
- 预设解析统一执行依赖闭包，未知模块、前向依赖、冲突或缺失路径在写文件前失败。
- 生成物写入 `scaffold.lock`，锁定 catalog、预设和最终模块集合。

正式预设的最终模块数：

| 预设 | scaffold.lock 模块数 | 业务边界 |
|---|---:|---|
| core | 7 | 认证、RBAC、组织、字典和基础能力 |
| workflow | 8 | core + Workflow |
| crm | 8 | core + CRM，不引入供应链 |
| supply-chain | 13 | core + Workflow + SRM + Procurement + Asset |
| full | 14 | 当前全部正式模块 |

## 3. S4-02：安全生成和裁剪

预设命令支持 `list`、`explain`、`create`、`validate` 和 `diff`。生成器只允许写入不存在或为空的新目录，先在内存中完成计划和校验，再以事务式复制与裁剪落盘；失败时回滚，不原地修改源仓库。

裁剪校验覆盖：

- Maven reactor 与模块目录。
- Compose 服务、依赖和端口变量。
- Gateway 静态路由和服务发现配置。
- 前端页面、API、路由、i18n 与动态菜单。
- 数据库 changelog、种子清单和 SHA-256 断言。
- 权限根、MQ binding、XXL-JOB 配置和模块文档。
- 被移除模块的反向残留扫描。

生成器测试验证了空目录保护、重复生成一致性、依赖闭包、自定义预设、共享 Workflow 模型过滤、漂移阻断和故障回滚。

## 4. S4-03：四语言维护文档

以下五类文档均提供中文、英文、日文和韩文版本：

- `preset-quick-selection`：预设快速选择。
- `preset-maintenance`：catalog、预设版本、模块增删和故障定位。
- `preset-dependency-matrix`：由 catalog 事实生成的依赖矩阵。
- `custom-preset-tutorial`：合法自定义组合和生成教程。
- `preset-upgrade-guide`：锁文件、兼容性和升级步骤。

维护说明覆盖新增模块、加入或移出预设、依赖和冲突声明、后端/前端/权限/数据库/Compose/MQ/文档落点、版本升级、黄金样例、失败回滚与定位。

## 5. S4-04：五预设静态构建矩阵

命令：`npm run test:preset-golden`（`tools/omni-cli`）。每个预设均重新生成到临时目录，并独立执行 Maven `clean install`、前端 `npm ci`、lint、生产构建和 `docker compose config`。

| 预设 | Maven reactor 项目数（含根） | 前端打包模块数 | npm audit | 结果 |
|---|---:|---:|---:|---|
| core | 14 | 1840 | 0 vulnerabilities | 通过 |
| workflow | 16 | 2292 | 0 vulnerabilities | 通过 |
| crm | 15 | 1878 | 0 vulnerabilities | 通过 |
| supply-chain | 19 | 2411 | 0 vulnerabilities | 通过 |
| full | 20 | 2448 | 0 vulnerabilities | 通过 |

CLI 单元/生成物回归：28 passed、0 failed、0 skipped。前端 lint 为 0 error、0 warning。

## 6. S4-04：五预设 fresh 运行矩阵

运行器：`tools/omni-cli/scripts/preset-runtime-golden.mjs`。每个预设均使用 fresh MySQL 卷启动并执行实际 HTTP 冒烟，不复用默认数据库。

| 预设 | Compose 服务数 | fresh | health | 登录 | 菜单裁剪 | 基础字典 | 身份/租户边界 |
|---|---:|---|---|---|---|---|---|
| core | 8 | 通过 | 通过 | 通过 | 通过 | 通过 | 通过 |
| workflow | 9 | 通过 | 通过 | 通过 | 通过 | 通过 | 通过 |
| crm | 9 | 通过 | 通过 | 通过 | 通过 | 通过 | 通过 |
| supply-chain | 15 | 通过 | 通过 | 通过 | 通过 | 通过 | 通过 |
| full | 16 | 通过 | 通过 | 通过 | 通过 | 通过 | 通过 |

安全断言：

- 匿名请求即使伪造 `X-User-Id` 和角色头，受保护接口仍返回 HTTP 401。
- 已认证请求伪造 `X-Tenant-Id=999999` 时，服务仍以 JWT 租户身份返回与正常请求一致的数据。
- 动态菜单中不存在被裁掉业务模块的路径或权限残留。
- CAPTCHA 只由隔离 Redis 测试过程读取；验证码、JWT、密码和密钥均不写入日志或证据。

## 7. full Playwright E2E

`full` 在隔离 fresh 栈上运行仓库正式 `npm run test:e2e`，结果为 **18 passed、0 failed、0 skipped**：

- 5 个公开入口可渲染，登录和设备表单不预填凭据。
- 公开 API 返回可追踪且唯一的关联 ID。
- Dashboard 不展示模拟运营数字。
- 管理员可进入系统用户、Workflow、CRM、SRM、采购、资产和 MQ 页面。
- 用户任务完成创建、触发、执行日志验证和删除清理。
- 普通员工访问无权限 CRM 深链得到 403，而不是空白页。
- 供应商窄屏门户可用，且后台管理路由被隔离。

E2E 身份由 `E2eTokenFixture` 从隔离数据库读取已启用用户和角色，复用运行中 Auth 的 Redis JWK 及生产令牌服务签发 10 分钟测试令牌。夹具不读取用户密码、不绕过 CAPTCHA 登录实现；令牌 JSON 位于仓库外并在 Playwright 结束后立即删除。`playwright.config.ts` 通过 `E2E_BASE_URL` 指向随机端口，未改变默认本地地址。

应用内 Browser 交互工具本次因本机内核资产路径错误无法启动，因此没有把该工具的人工点击作为证据。仓库正式 Playwright 套件已完成登录后 UI、路由、权限、窄屏和写入闭环复验。

## 8. 验收过程中发现并修复的问题

1. 服务镜像构建曾错误触发已从预设上下文裁掉的 db-migrator；已将 migrator 从服务 reactor 构建中排除并独立构建。
2. MySQL 初始化阶段临时 Unix socket 会过早满足健康检查，导致 migrator 与初始化竞争；已改为 TCP 健康检查。
3. Workflow 预设过滤共享模型后，种子断言行数和 SHA-256 未同步；已由过滤后的真实种子重新计算。
4. 可选运行集成在裁剪预设中仍尝试连接不存在的依赖；已按预设显式关闭被省略的运行集成。
5. 清理异常曾可能掩盖主失败；运行器现保留原始错误并单独报告清理错误。
6. Windows Node 不能直接 `spawnSync` Maven/npm 批处理文件；运行器现通过受约束的 `cmd /c` 参数桥接，并拒绝命令元字符。

## 9. 清理与可重复性

- Compose project 名只允许 `omni-preset-runtime-<preset>-<8位随机值>`。
- 所有宿主端口在启动前动态保留，Compose 启动前统一释放。
- 数据卷名包含隔离 project 前缀，不引用默认卷。
- 成功或失败均执行 `docker compose down -v --remove-orphans`。
- 递归删除前验证目标必须是系统临时目录的非根子目录。
- 短期令牌文件使用不可预测文件名和 `CREATE_NEW`，并在独立 `finally` 中删除。
- 两次 Windows 启动适配失败均验证无残留容器；最终成功执行同样的清理路径。

## 10. 关联提交

- `cb48c0d feat(scaffold): establish complete module catalog`
- `99dcc93 feat(scaffold): generate isolated project presets`
- `a8a24b7 docs(scaffold): publish preset maintenance guides`
- `f90b16c fix(scaffold): disable omitted runtime integrations`
- `08c8828 fix(scaffold): harden preset container startup`
- `eeb4099 test(scaffold): automate preset runtime matrix`
- `b0456fd fix(scaffold): align preset workflow seed assertions`

本证据随最终验收脚本提交一并更新；以上哈希用于追溯核心实现，不替代最终分支历史。
