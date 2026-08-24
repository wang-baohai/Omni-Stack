# S3-02 create-service 生成器内核证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

代码提交：`e0191b6 feat(scaffold): add safe create-service generator core`

## 1. 当前结论

`@omni-stack/cli` 已升级到 0.2.0，并实现 `omni create-service <service-id>` 与
`omni service validate <service-id>` 的首个可用内核。命令默认 dry-run，只有显式 `--apply`
才写入；生成过程使用同级 staging 和原子重命名，失败时清理 staging。

这一里程碑生成的是“待接入服务包”，不直接修改当前 monorepo。服务包包含后端模块、前端
View/API/i18n、权限自然键、catalog/Gateway/Compose/父 POM 接入片段、模块维护文档、四语言
完成状态及 `omni-service.lock.json`。父 POM、Gateway、Compose、catalog、权限种子和前端注册的
结构化合并属于 S3-03，当前没有把未实现的合并能力声明为完成。

## 2. 安全与契约

- service-id、Java package、API 前缀、数据库、表前缀、端口和 DataScope 表均严格校验。
- 默认扫描现有 `application.yml` 选择未占用的服务、管理和 XXL-JOB 端口。
- 已存在 catalog 模块、未知非空目录、损坏锁文件、输入不一致、文件哈希漂移及额外文件均拒绝覆盖。
- 同一输入和完整生成物重复执行返回 `unchanged`，不会重复插入内容。
- DataScope 必须显式声明领域表，生成实现对这些表默认返回 `1 = 0`，在实现 owner 规则前失败关闭。
- `--mq` 自动启用 Job，确保 Transactional Outbox relay 有执行器；OperLog 只启用所需 MQ transport。
- 后端/Gateway 使用 `/api/<module>`，前端按 Axios `/api` baseURL 生成 `/<module>`，避免重复 `/api/api`。
- 所有生成文件记录 generator/template 版本，锁文件登记规范化输入与 SHA-256。

## 3. 生成内容

黄金输入 `inventory-sample` 共生成 25 个受锁文件管理的文件：

- Maven POM、Application、Security、TenantTablePolicy、可选 DataScopeTablePolicy。
- 模块状态 Controller、Service 接口/实现、Mapper 包说明、应用配置和租户契约测试。
- Vue View、typed API、简体中文和英文 i18n 片段。
- catalog、Gateway route、Compose service、父 POM module、权限自然键和菜单接入片段。
- 中文维护说明、四语言状态和生成接入计划。

生成器不创建虚假 Entity、业务表、Mapper、状态机、Saga、审批补偿或跨服务幂等逻辑。

## 4. 自动化验证

```text
命令：cd tools/omni-cli && npm test
CLI：0.2.0
Tests：10 passed, 0 failed
覆盖：catalog、preset、dry-run、原子写入、幂等、DataScope 约束、MQ→Job、漂移和未知目录阻断
```

```text
命令：cd tools/omni-cli && npm run test:golden
生成：omni-inventory-sample，25 个文件
JDK：25
Maven：BUILD SUCCESS
Tests：1 passed, 0 failed
耗时：6.566 s（Maven）
清理：系统临时生成包和 omni-backend/.omni-service-golden-* 均由 finally 删除
```

前端生成物另以临时 API/View 放入当前前端工程执行真实质量门：

```text
npm run build：通过，vue-tsc + Vite build 成功
npm run lint：通过，0 errors / 0 warnings
清理后再次 npm run build：通过，tsconfig.tsbuildinfo 已恢复，无黄金文件残留
```

Vite/Rolldown 对 `@vueuse/core` 的两个 `INVALID_ANNOTATION` 提示和 Maven 对旧 Javassist/Lombok 的
提示为当前依赖基线警告，本次没有新增 lint、类型或编译警告。

## 5. 质量门发现并修正的问题

黄金编译首次发现 RocketMQ 依赖 groupId 错误，修正为 `com.alibaba.cloud`；第二次发现生成服务不能
依赖 Starter 的 `provided` Lombok，模板改为显式声明 Lombok。前端实际构建后的契约复核发现 Axios
baseURL 会导致重复 `/api`，已拆分后端和前端 API 前缀并增加回归断言。

这些问题均在重新从零生成后验证，没有直接修改黄金输出绕过模板事实来源。

## 6. 后续条件

S3-03 需要继续实现 XML/YAML/TypeScript/SQL 的结构化集成事务，包括冲突扫描、Git 脏文件保护、
备份 diff、catalog 写后校验和整组回滚；完成后才能把生成包安全合入目标 monorepo。随后 S3-04
补充最小依赖启动、health、401、403、内部 API 401/503 和 Compose 配置验证。
