# 常见故障与排查手册

排查原则：先确定失败层级，再使用 Trace ID、业务 ID 和日志证据定位；不要通过重复重启或直接改数据库掩盖根因。

## 1. 启动失败

| 现象 | 检查 | 处理 |
|---|---|---|
| Maven 插件不兼容 | `java -version`、`JAVA_HOME` | 切换到 JDK 25 后使用 `./mvnw` |
| 应用等待 Nacos | Nacos 健康、8848/9848、身份配置 | 先恢复 Nacos，再重启目标服务 |
| migrator 失败 | migrator 日志、DATABASECHANGELOG | 修复新 changeSet 或环境，不改已执行 changeSet |
| MySQL 连接拒绝 | 容器健康、端口、账号、数据库 | 确认目标 Compose project 和连接串 |
| 未选依赖反复重试 | CLI dev plan、profile | 使用正确预设或显式关闭可选集成 |

## 2. 登录失败

- CAPTCHA 不能为空或错误：刷新图片并使用新的 `captchaKey`。
- 用户不存在：确认租户和租户内用户名。
- 登录后循环回登录页：检查令牌有效期、本机时间和 Gateway 身份头。
- 社交回调失败：检查回调 URI、PKCE、`state` 和客户端配置。
- Portal 403：检查 `SUPPLIER` 角色、Portal 关联和供应商状态。

认证日志在登录日志中，不在 Auth 操作日志中查找。

## 3. 菜单或权限异常

1. 用 API 查看 JWT authorities 和 `/api/auth/menus` 返回。
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

