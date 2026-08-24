# S3-04 黄金服务端到端证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

关键提交：

- `6401e33 fix(scaffold): align generated seed assertion digest`
- `6d6dbed fix(starter): provide load balancer for auth clients`
- `3c4f56f test(scaffold): automate integrated golden service gate`

## 1. 验收结论

CLI 0.6.1、服务模板 1.2.0 的固定黄金输入 `inventory-sample` 已完成从生成到运行的闭环：

- 生成包包含 28 个受锁文件管理的文件。
- dry-run 产生 21 项接入操作和 32 个最终文件变更，未写入目标。
- `--apply` 以跨文件事务完成 32 个变更，事务备份和临时文件全部清理。
- 后端 11 个相关 reactor 项目通过 JDK 25 `clean install`。
- 前端生产构建、ESLint 和 Compose 配置校验通过。
- 既有隔离数据库完成首次增量迁移，第二次执行无 changeSet。
- 生成服务真实启动，外部接口和内部接口权限矩阵全部符合预期。
- 黄金服务、生成包、数据库、权限、changelog、单库授权和临时目录已清理，无样例模块进入仓库。

## 2. 可重复自动化门禁

`tools/omni-cli/scripts/service-golden.mjs` 不再把生成模块临时复制到真实 reactor。它会：

1. 在系统临时目录复制一份不含 `.git`、本地环境文件、依赖缓存和构建产物的工作区。
2. 生成并校验 `inventory-sample` 锁文件。
3. 在内存渲染 32 个接入文件并原子应用。
4. 执行 `clean install -pl omni-inventory-sample,omni-db-migrator -am`。
5. 使用非生产占位环境变量执行 `docker compose config --quiet`。
6. 校验临时路径后递归删除整个临时树。

```text
命令：cd tools/omni-cli && npm test
结果：14 passed，0 failed
```

```text
命令：设置 JAVA_HOME=JDK 25 后执行 npm run test:golden
结果：11/11 reactor projects SUCCESS
DB Migrator：22 passed，0 failed
Inventory Sample：1 passed，0 failed
Compose config：通过
最终输出：generated=28, integrated=32
```

```text
命令：cd omni-frontend && npm ci && npm run build && npm run lint
结果：318 个锁定依赖，0 vulnerabilities；build/lint 均为 exit 0
```

## 3. 既有数据库增量与幂等

验证只使用 Docker Compose 项目 `omni-g1` 的隔离 MySQL（宿主端口 23316），没有连接或修改默认栈。
首次迁移结果：

- `platform-generated-inventory-sample-create-database`：执行 1 条。
- `auth-generated-inventory-sample-permissions`：执行 1 条。
- 新服务数据库 common schema：执行 3 条。
- 既有 platform/Auth/业务 changeSet 校验和均通过。
- 权限断言：3 行，SHA-256 为
  `dee57b7e3bbb35f6e86c8bb2ed128457aaeae735e8e40d0b8913a95383d8634b`。

紧接着第二次执行结果：

```text
exit=0 runningChangesets=0 failures=0 sampleAssertionPass=1
```

本轮真实数据库复验发现并修复了三个不能由纯模板测试覆盖的问题：

1. 新权限不能追加到已经执行的 `auth.sql`，否则会造成既有 Auth changeSet 校验和漂移；现改为独立
   seed 和 `adoption-upgrade` forward-only changeSet。
2. Windows CRLF 会造成资源摘要与 JAR 内 LF 不一致；资源摘要现按严格 UTF-8 和规范换行计算。
3. JDBC 规范摘要会转义 `|`、`=`、反斜杠和控制字符；CLI 生成预期摘要现与运行时算法完全一致，
   并固定真实 JDBC 回归值。

## 4. 服务启动与权限矩阵

生成服务首次启动暴露出 `omni-common-service` 提供 Auth Feign 客户端却未传递 LoadBalancer 的问题。
Starter 已补充 `spring-cloud-starter-loadbalancer`，并增加类路径契约测试；修复后服务连接隔离 MySQL、
Redis 和 RocketMQ 成功，在 28110/28111 端口启动。

隔离 Nacos 将 8848 和 9848 映射到不保持 `+1000` 关系的宿主端口，Nacos 3 客户端无法从宿主机推导
gRPC 端口。因此只在这次宿主机运行验证中关闭 Nacos config/discovery；Compose 内部网络仍使用
`nacos:8848/9848`，正式生成配置未改变。

外部接口矩阵：

| 场景 | 预期 | 实际 |
|---|---:|---:|
| 管理端健康检查 | 200 | 200 |
| 绕过 Gateway 直连业务接口 | 403 | 403 |
| Gateway 标记存在但缺用户/租户 | 401 | 401 |
| 身份合法但缺 `inventory-sample:read` | 403 | 403 |
| 身份合法且具备 read scope | 200 | 200 |

内部接口矩阵：

| 场景 | 预期 | 实际 |
|---|---:|---:|
| 缺少内部令牌 | 401 | 401 |
| 错误内部令牌 | 401 | 401 |
| 正确内部令牌 | 200 | 200 |

XSS 设置在测试时因关闭服务发现无法回源 Auth，按设计启用安全基线并继续提供服务，证明失败策略为
安全降级而不是关闭防护。

## 5. 清理证明

- 黄金服务 PTY 已优雅停止，28110/28111 监听数均为 0。
- `omni-g1` 中临时权限、两条 changelog、`omni_inventory_sample` 数据库和 `omni_app` 单库授权均为 0。
- 手工黄金 worktree、生成包和自动化 `omni-service-golden-*` 目录均不存在。
- 主工作区没有 `inventory-sample` 模块、事务备份、临时 SQL/Python/Node 脚本或生成产物。

## 6. S3-04 状态

S3-04 已达到计划中黄金样例的生成、校验、接入、后端构建、前端构建、Compose、数据库、启动、
权限矩阵、内部令牌和清理目标。固定样例只以自动化输入和证据存在，不作为重复业务模块提交。
