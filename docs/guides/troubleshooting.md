# 常见故障与排查手册

排查原则：先确定失败层级，再使用 Trace ID、业务 ID 和日志证据定位；不要通过重复重启或直接改数据库掩盖根因。 构建、检查和诊断程序均在本机 Docker 中运行，宿主只作编辑、只读审计和 Docker 编排。分别保存 HTTP 状态与响应体 R.code，不把业务码直接写成 HTTP 状态。

四语文档按[已授权 AI 复核政策](../adr/0003-authorized-ai-translation-review.md)以实际 AI 语义复核通过验收，不要求人工翻译复核。[本轮复核范围](../reference/evidence/troubleshooting-guide-review-2026-10-17.json)仅覆盖文档与当前源码，没有重新触发登录、任务、消息、缓存刷新或日志归档；步骤可操作不代表当前故障已复测解决。

## 1. 启动失败

| 现象 | 检查 | 处理 |
|---|---|---|
| Maven 插件不兼容 | `java -version`、`JAVA_HOME` | 切换到 JDK 25 后使用 `./mvnw` |
| 应用等待 Nacos | Nacos 健康、8848/9848、身份配置 | 先恢复 Nacos，再重启目标服务 |
| migrator 失败 | migrator 日志、DATABASECHANGELOG | 修复新 changeSet 或环境，不改已执行 changeSet |
| MySQL 连接拒绝 | 容器健康、端口、账号、数据库 | 确认目标 Compose project 和连接串 |
| 未选依赖反复重试 | `dev status` / `dev doctor`（无 `dev plan` 子命令，计划由 `dev up` 启动前打印）、profile | 使用正确预设或显式关闭可选集成 |

## 2. 登录失败

- CAPTCHA 不能为空或错误：刷新图片并使用新的 `captchaKey`。
- 用户不存在：确认租户和租户内用户名。
- 登录后循环回登录页：检查令牌有效期、本机时间和 Gateway 身份头。

- 社交回调失败：检查回调 URI、后端 HMAC 签名的 `state` 和提供商客户端配置（外部社交登录不涉前端 PKCE）；前端 PKCE（`code_verifier`/`code_challenge`）属于本地 `/oauth2/authorize` SPA 流程，需单独排查。
- Portal 403：检查 `SUPPLIER` 角色、Portal 关联和供应商状态。

登录事件查看登录日志，它是安全审计登录事件的只读投影；其他认证与安全事件查看安全审计记录。Auth 不使用业务操作日志记录认证行为。

## 3. 菜单或权限异常

1. 在私有环境核对 JWT 的 `scope` 权限集合和 `/api/auth/menus` 返回；不要把完整 JWT 贴进日志或支持请求。
2. 检查权限种子、角色关系和组织范围。
3. 权限变化后重新登录。
4. 用无权限账号直调写 API，确认后端返回 403。
5. 如果只有前端按钮异常，核对 `v-permission` 与后端权限码是否完全一致。

## 4. 数据看不到或越权

- 确认请求经过 Gateway，而不是伪造身份头直连业务服务。
- 检查租户 ID、当前用户、组织和角色范围。
- 检查领域 DataPermission 表名和列映射。
- 子表必须通过聚合根继承，不给不存在的 owner 列追加条件。
- 检查 DataPermission 拦截器是否位于 Pagination 之前。

## 5. Workflow

- 模型无法保存：检查 BPMN XML 与设计器状态。
- 校验失败：按校验列表修复 process id、连线、候选人配置和表达式。
- 发布冲突：检查同模型并发操作和当前草稿。
- 启动失败：按业务单号查启动请求预留记录和重试状态。
- 审批人为空：使用候选人预览检查角色、锚点、组织范围和兜底策略。
- 会签结果异常：检查 `MI_END` 历史任务删除原因和计数变量。

## 6. XXL-JOB

- 系统任务不可见：检查 `@XxlJob` 与 `@SystemJobMeta` 双注解。
- 个人任务无处理器：Bean 名必须等于 `typeCode`。
- 创建留下孤儿记录：检查注册失败后的数据库删除补偿。
- 立即执行无日志：检查 Admin、执行器注册、处理器异常和日志写入。

## 7. MQ 与跨服务

1. 在 Outbox 按消息 ID、Topic 或业务 Key 查询。
2. 检查 PENDING、FAILED、DEAD_LETTER 状态和下一次重试时间。
3. 检查 Broker 投递和消费者 Inbox。
4. 使用 producer traceId 与 consumer traceId 在 Loki/Tempo 关联。
5. 只在确认下游幂等后手工重发。

Relay 跨租户扫描是设计行为；外部查询接口仍必须按租户过滤。

## 8. 前端

- 空白页：检查动态菜单加载、路由 chunk、浏览器控制台和 API 401/403/404。
- 动态表单字段类型错误：检查 JSON Schema `type`、`enum`、`options` 和 required。
- 语言未持久化：检查 `omni-lang` 是否为 `zh-CN`、`en-US`、`ja-JP` 或 `ko-KR`。
- 日期/金额格式错误：检查是否使用当前 locale，接口值不得因翻译而改变。
- lint 失败：不得降低规则，修正源码并保持 `--max-warnings 0`。

## 9. 请求支持时提供

- 适用版本与提交 ID。
- 目标预设和 Compose project 名。
- 时间范围、Trace ID、业务 ID。
- 脱敏后的错误响应和相关服务日志。
- 可重复步骤、期望结果和实际结果。

不要提供密码、CAPTCHA 答案、JWT、内部令牌、私钥或未经脱敏的个人数据。

## 10. 从旧运行说明迁入的排障步骤

以下步骤已按当前源码校准；旧截图、旧部署端口和历史验收通过不能证明本次故障已经解决。构建和诊断程序放在本机 Docker 中运行，宿主只作编辑和 Docker 编排。

<!-- omni:modules:auth -->
| 症状 | 先核对 | 源码和恢复边界 |
|---|---|---|
| 验证码图片不显示 | Auth 健康、Auth 到 Redis 的连接与私有密码配置 | `CaptchaServiceImpl`；校验会一次性取走记录，失败或过期后刷新并由人重新输入，不重放旧码 |
| 登录成功后立即 401 | Gateway 是否能取到 Auth `/oauth2/jwks`、`kid`、签名、`exp` 与时钟 | `JwkKeyProvider` 与 Gateway 验签；密码登录使用的代码回退值为 900 秒，仓库 `application.yml` 配置为 7200 秒。以部署覆盖后的 `auth.token.access-token-ttl` 和实际 `exp` 为准，其他 OAuth grant 单独核对 |
| 菜单为空或按钮隐藏 | `scope`、`sys_role_permission`、`permission_code`、前端 `v-permission` | 权限变更后重新登录；菜单隐藏与 API 403 分别验证，不能通过硬编码路由补权限 |
| 社交回调失败 | provider 注册 URI 与 `AUTH_OAUTH2_*_REDIRECT_URI` 完全一致、服务端 `state` 签名配置、容器出站连接 | 本地回调进入 Gateway 8102，容器 Auth 8080 不是外部回调；配置步骤见[认证教程](authentication.md) |
<!-- omni:modules:end -->

<!-- omni:modules:platform -->
| 症状 | 先核对 | 源码和恢复边界 |
|---|---|---|
| 字典值更新后仍旧 | 租户、`typeCode` 和 `DictDataServiceImpl` 的租户缓存键 | 写操作应立即失效；有 `dict:data:refresh` 权限时调用 `POST /api/base/dict/data/refresh-cache?typeCode={code}` 再回读，不使用旧的 PUT `/refresh`；30 分钟 TTL 不是正确性保证 |
| 删除字典类型后关联数据残留 | `DictTypeServiceImpl.deleteType()` 同事务删除当前租户同类型数据、类型及缓存 | 只按本次返回 ID 和唯一编码回查；失败检查事务与租户，不手工删共享数据 |
| 业务操作日志缺失或延迟 | 来源服务 Outbox、正式 relay、Broker、`OperLogConsumer.operlogConsumer()` 与 Inbox 去重 | 先查同步请求是否提交，再跟踪生产/消费 Trace；日志异步到达不是请求回滚证据，Auth 登录仍查询安全审计投影 |
| 日志归档不执行 | Base 执行器注册、XXL 调度记录、`OperLogArchiver` 的 `operLogArchiveHandler` 双注解 | 核对一次执行和归档记录；多实例使用单实例路由，未确定当前任务结果前不重复触发，不为取证归档共享日志 |
<!-- omni:modules:end -->

