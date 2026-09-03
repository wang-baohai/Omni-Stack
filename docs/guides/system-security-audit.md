# 系统配置、安全配置与审计日志

本指南说明租户管理员和运维人员如何维护基础配置、XSS 防护、在线会话、登录记录、操作日志与可靠消息记录。

## 1. 系统基础数据

系统管理包含租户、组织、用户、角色、权限、OAuth2 客户端、在线用户、授权记录、审计记录和 XSS 配置；基础数据包含字典。修改前先确认当前租户和权限范围。

字典适合稳定的展示枚举。接口仍传输稳定值，翻译只改变显示文案，不能改变状态码、权限码或业务参数。

## 2. OAuth2 客户端

客户端配置至少包含客户端 ID、允许的授权方式、回调 URI、作用域和是否需要用户同意。生产客户端必须：

- 使用 HTTPS 回调。
- 精确登记回调地址，不使用宽泛通配符。
- 通过 Secret 管理客户端密钥。
- 只开放业务需要的授权方式和作用域。
- 定期轮换凭据并审查授权记录。

## 3. XSS 防护

所有包含 `@RequestBody` 的新后端服务必须实现 `XssConfigProvider` SPI。公共组件提供请求过滤、Jackson 字符串净化和 Gateway 安全响应头三层防护。

租户管理员可以切换 XSS 状态并维护规则。任何写操作都必须立即清除：

- `xss:enabled:{tenantId}`
- `xss:rules:{tenantId}`

不能只等待缓存 TTL。规则变更后使用包含事件属性、脚本协议和富文本边界的测试请求复验。

## 4. 安全响应头

Gateway 对所有响应添加：

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy`

这些响应头不能替代输入净化、输出编码、CSP 或权限控制。生产反向代理不应覆盖为更弱配置。

## 5. 三类审计信息

| 信息 | 位置 | 主要用途 |
|---|---|---|
| 登录日志 | `sys_login_log` | 登录成功/失败、来源和认证方式 |
| 操作日志 | 管理端“操作日志” | 业务写操作、操作者、结果和 Trace ID |
| MQ 消息记录 | 管理端“消息记录” | Outbox 状态、重试、死信和跨服务关联 |

`omni-auth` 不使用 `@OperLog`，认证行为由登录日志完整留存。业务服务的操作日志通过可靠消息发送，查询必须按租户过滤；后台 relay 扫描所有租户是有意设计。

## 6. 排障工作流

1. 从用户报错页面或响应头取得 Trace ID。
2. 在操作日志确认请求、操作者和结果。
3. 若涉及异步业务，在 MQ 消息记录按消息 Key、Topic 或 Trace ID 查找。
4. 在 Grafana/Tempo 查看同步调用链，在 Loki 查看生产与消费 Trace 关联。
5. 失败消息达到最大重试后进入死信，只能在确认幂等和下游状态后重发或跳过。

## 7. 生产安全清单

- 替换所有开发默认账号、数据库口令、Nacos 身份和内部共享令牌。
- 不公开 MySQL、Redis、Nacos、XXL-JOB、Prometheus、Grafana、Tempo、Loki 或 Actuator 管理端口。
- 使用最小权限账号和 TLS。
- 配置日志保留期、脱敏、告警接收人和审计导出。
- 定期验证备份可恢复，而不是只确认备份文件存在。
- 截图、测试报告和支持工单不得包含密码、CAPTCHA 答案、JWT、内部令牌或真实个人数据。

## 8. 管理端页面四语言截图

正式图片由文档专用 Playwright 用例 `omni-frontend/e2e-docs/flows/management.flows.spec.ts` 在真实运行栈上生成，按语言分目录存放，不复用其他语言图片、不使用占位图或模拟响应。

- 前置条件：本地 Compose 全栈运行，前端 `127.0.0.1:3000`；`omni-auth` 与 `omni-base` 健康。
- 操作者：`admin`（`SUPER_ADMIN`，具备系统管理、基础数据与监控菜单权限）。
- 操作：登录后依次进入租户、组织、角色、权限、字典、在线用户、审计日志、授权记录、XSS 防护与操作日志页面。
- 预期状态：页面标题与列标签按当前语言渲染，列表展示真实系统数据；无记录时呈现产品自身空状态（不是截图失败）。
- 令牌：`E2eTokenFixture` 在测试进程内签发短期 JWT（TTL 1200 秒），收尾即销毁，不写入文档、日志或版本库。
- 本组全部为**只读采集**：不创建、不修改、不删除任何配置或审计数据，因此不需写入开关，也无数据收尾。

敏感信息说明：授权记录页仅展示 OAuth2 `client_id`（公开标识符）、主体与授权类型，**不含 client secret、令牌或密码**；在线用户页在当前环境为空状态，不含任何会话令牌。

| 页面 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 租户管理（tenant） | ![租户管理（简体中文）](../images/zh-CN/system-tenants.png) | ![租户管理（英文）](../images/en-US/system-tenants.png) | ![租户管理（日文）](../images/ja-JP/system-tenants.png) | ![租户管理（韩文）](../images/ko-KR/system-tenants.png) |
| 组织管理（organization） | ![组织管理（简体中文）](../images/zh-CN/system-organizations.png) | ![组织管理（英文）](../images/en-US/system-organizations.png) | ![组织管理（日文）](../images/ja-JP/system-organizations.png) | ![组织管理（韩文）](../images/ko-KR/system-organizations.png) |
| 角色管理（role，含 data-scope 入口） | ![角色管理（简体中文）](../images/zh-CN/system-roles.png) | ![角色管理（英文）](../images/en-US/system-roles.png) | ![角色管理（日文）](../images/ja-JP/system-roles.png) | ![角色管理（韩文）](../images/ko-KR/system-roles.png) |
| 权限管理（permission，含 menu 节点） | ![权限管理（简体中文）](../images/zh-CN/system-permissions.png) | ![权限管理（英文）](../images/en-US/system-permissions.png) | ![权限管理（日文）](../images/ja-JP/system-permissions.png) | ![权限管理（韩文）](../images/ko-KR/system-permissions.png) |
| 字典管理（dictionary） | ![字典管理（简体中文）](../images/zh-CN/system-dictionaries.png) | ![字典管理（英文）](../images/en-US/system-dictionaries.png) | ![字典管理（日文）](../images/ja-JP/system-dictionaries.png) | ![字典管理（韩文）](../images/ko-KR/system-dictionaries.png) |
| 在线用户（online-user，空状态） | ![在线用户（简体中文）](../images/zh-CN/system-online-users.png) | ![在线用户（英文）](../images/en-US/system-online-users.png) | ![在线用户（日文）](../images/ja-JP/system-online-users.png) | ![在线用户（韩文）](../images/ko-KR/system-online-users.png) |
| 审计日志（audit） | ![审计日志（简体中文）](../images/zh-CN/system-audit-log.png) | ![审计日志（英文）](../images/en-US/system-audit-log.png) | ![审计日志（日文）](../images/ja-JP/system-audit-log.png) | ![审计日志（韩文）](../images/ko-KR/system-audit-log.png) |
| 授权记录（oauth2） | ![授权记录（简体中文）](../images/zh-CN/system-auth-records.png) | ![授权记录（英文）](../images/en-US/system-auth-records.png) | ![授权记录（日文）](../images/ja-JP/system-auth-records.png) | ![授权记录（韩文）](../images/ko-KR/system-auth-records.png) |
| XSS 防护（xss） | ![XSS 防护（简体中文）](../images/zh-CN/system-xss-config.png) | ![XSS 防护（英文）](../images/en-US/system-xss-config.png) | ![XSS 防护（日文）](../images/ja-JP/system-xss-config.png) | ![XSS 防护（韩文）](../images/ko-KR/system-xss-config.png) |
| 操作日志（operation-log） | ![操作日志（简体中文）](../images/zh-CN/system-operation-log.png) | ![操作日志（英文）](../images/en-US/system-operation-log.png) | ![操作日志（日文）](../images/ja-JP/system-operation-log.png) | ![操作日志（韩文）](../images/ko-KR/system-operation-log.png) |

尚未覆盖的两个 required flow：`config`（参数配置）与 `login-record`（登录记录）。实测 `sys_permission` 中无对应权限码，前端也无对应 view 目录，属产品当前未提供页面；按约束**不删除 required flow、不自行标记 exempt**，在覆盖清单中保留为显式 gap。

更多信息见 [可靠消息](../mq-reliability.md)、[可观测性](../observability.md) 和 [Docker 部署](../docker-deployment.md)。

