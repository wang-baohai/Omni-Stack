# 脚手架升级实施基线

> 基线时间：2026-08-20（Asia/Shanghai）
> 基线提交：`09a29fe10af9c7ddffe5001238d048947868dc98`
> 实施分支：`codex/scaffold-upgrade`
> 对应任务：S0-01
> 说明：本文件只保存可审计摘要，不保存 Secret、JWT、真实业务数据或大体积原始日志。

## 1. 环境与仓库事实

| 项目 | 基线值 |
|---|---|
| 操作系统 | Windows，PowerShell，Asia/Shanghai |
| 构建 JDK | OpenJDK 25.0.2（Maven 命令显式设置 `JAVA_HOME=C:\APP\JDK25\jdk-25.0.2`） |
| 系统默认 Java | Java 8；不能用于本项目构建，属于环境陷阱 |
| Node.js | 22.22.3 |
| npm | 10.9.8 |
| Docker Client / Server | 29.6.2 / 29.6.2 |
| Docker Compose | 5.3.1 |
| Maven POM | 18 个：根父工程 1 个、子模块 17 个 |
| 后端 Java 源文件 | 777 个 main、132 个 test；统计时排除 `target` |
| 前端页面 / 组件 / API | 59 / 31 / 44 |
| SQL | 25 个 |
| Markdown 文档 | 52 个（基线提交口径） |
| scripts 根文件 | 31 个 |

实施计划和上位路线图在基线提交后作为本分支新增文档，因此不计入 `09a29fe` 的 Markdown 数量。

## 2. 验证结果

### 2.1 后端

执行范围：`omni-backend` 全 Maven reactor，使用 Maven Wrapper 3.9.16 和 JDK 25。

| 验证 | 结果 | 通过 / 失败 / 跳过 | 备注 |
|---|---|---:|---|
| `./mvnw clean install` | 成功 | reactor 18 / 0 / 0 | 总耗时约 3 分 06 秒 |
| 全量 Surefire | 成功 | 494 / 0 / 4 | 4 个 MySQL 拦截器集成测试因全量命令未注入隔离数据库连接而条件跳过 |
| CRM MySQL 拦截器集成测试 | 成功 | 4 / 0 / 0 | 在隔离的 `crm_it` 数据库、宿主机端口 13306 上单独复验 |
| 合并测试事实 | 成功 | 498 / 0 / 0 | 不能把两次执行简单描述为“一次全量 0 skip”；合并口径如左 |

已知非阻断警告：

- Lombok 在 JDK 25 上访问终止弃用的 `sun.misc.Unsafe` 方法。
- Maven 对 `javassist` 有 effective model 警告。
- Auth/Base 存在既有 deprecated API 编译提示。

这些警告进入后续依赖与代码债务清单，不在 S0-01 顺手修改。

### 2.2 前端

| 验证 | 退出码 | 结果 |
|---|---:|---|
| `npm run build` | 0 | vue-tsc + Vite/Rolldown 构建成功，约 24.54 秒 |
| `npm run lint` | 0 | 0 error、197 warning，其中 84 个可自动修复 |

构建中出现的 `INVALID_ANNOTATION` 来自 `@vueuse/core` 第三方产物，不是项目源码错误。197 个 lint warning 是 WP-08 的输入基线，质量门升级为零 warning 前必须逐类消除。

### 2.3 Compose 与运行时

Docker Desktop 启动后，复用现有容器和数据卷，不重建、不覆盖配置：

| 分组 | 容器 | 状态 |
|---|---|---|
| 基础设施 | MySQL、Redis、Nacos、RocketMQ NameServer、RocketMQ Broker、XXL-JOB | running；有健康检查者均 healthy |
| 后端 | Auth、Base、Gateway、Workflow、CRM、SRM、Procurement、Asset | running + healthy |
| 前端 | Frontend | running；镜像未声明 healthcheck |

总计 15/15 running，14/14 已声明 healthcheck 的容器 healthy。MySQL 宿主机端口为 `127.0.0.1:13306`，不是默认 3306。

### 2.4 Playwright

命令：`npm run test:e2e`，浏览器为 Chromium。

| 场景 | 结果 |
|---|---:|
| 公共页面 | 7 passed |
| 登录后角色场景 | 11 passed |
| failed | 0 |
| 总场景 | 18 |

首次基线执行在未提供角色令牌时得到 7 passed / 11 skipped。随后新增 test-classpath-only 的
`E2eTokenFixture`：它从隔离环境读取已启用用户和授权，复用运行中 Auth 的 Redis JWK，通过生产
`JwtTokenServiceImpl` 签发 10 分钟令牌；不读取用户密码，不启动测试登录端点。启用
`E2E_MUTATIONS=true` 复跑后为 **18 passed / 0 failed / 0 skipped**，耗时约 12.2 秒。

变更型用例实际完成了用户任务创建、触发、日志轮询和 `finally` 清理。令牌 JSON 只在系统临时目录短暂存在，Playwright 结束后已经删除。

测试令牌必须通过测试范围内的正式夹具产生，不得：

- 破解 CAPTCHA；
- 新增生产免验证码端点；
- 把 JWT、私钥或密码提交到仓库或打印到日志；
- 复用历史脚本中硬编码的过期令牌。

## 3. 数据库结构快照

只读查询 `information_schema` 得到下列运行态表数量：

| 数据库 | 表数 |
|---|---:|
| `omni_auth` | 20 |
| `omni_base` | 8 |
| `omni_workflow` | 54 |
| `omni_crm` | 12 |
| `omni_srm` | 21 |
| `omni_procurement` | 15 |
| `omni_asset` | 6 |
| `nacos_config` | 10 |
| `xxl_job` | 8 |

业务数据库共 136 张表；另有 Nacos/XXL-JOB 18 张 vendor 表。`omni_auth` 当前存在一个存储过程 `sp_init_tenant`。详细来源、结构职责和迁移处置见 [migration-inventory.md](scaffold-upgrade/migration-inventory.md)。

## 4. 当前缺陷与风险清单

| ID | 级别 | 事实 | 阻断门 | 后续处置 |
|---|---|---|---|---|
| BL-01 | 已关闭 | 登录态 E2E 原缺少受控短期 Token 夹具 | G0 | test-only 夹具已实现并复跑 18/18 |
| BL-02 | 高 | 历史 PowerShell 脚本中存在硬编码的过期 JWT | G1 安全审查、最终 G5 | 立即停止使用；WP-10 删除或改为临时环境变量，并增加 Secret 扫描 |
| BL-03 | 中 | 前端 lint 有 197 warning | G2 | WP-08 清零，不批量禁用规则 |
| BL-04 | 中 | 默认 Java 是 8，遗漏 `JAVA_HOME` 会产生误导性失败 | G1 | 标准化开发命令和 preflight 检查 |
| BL-05 | 中 | 当前 25 个 SQL 分布在三个区域，DDL、seed、修复、检查和样例混放 | G1 | S0-03 台账；S0-04 起迁移到 Liquibase/seed 体系 |
| BL-06 | 低 | 前端构建有第三方 Rolldown 注解警告 | G4 | 锁定依赖版本并在依赖升级时复验 |

## 5. 基线结论

- 编译、后端测试、前端生产构建和现有 Compose 运行栈均可用。
- 现有数据库可读，九个目标数据库及租户初始化存储过程已经完成基线盘点。
- 使用 MySQL 8.4.10 同版本、无宿主机端口的随机临时容器完成九库 `mysqldump` 流式恢复；恢复后的表数逐库与源一致，`omni_auth.sp_init_tenant` 为 1。没有将 dump 写入仓库或宿主机，临时容器已删除。
- 受控短期 Token 夹具已补齐，18 个 E2E 场景全部实际通过；S0-01 和 G0 完成。
- 该结论只证明基线可继续实施，不授权提前删除旧 SQL 或脚本；删除仍受 G1 和 WP-10 替代物验证门约束。
