# Omni-Stack 全功能审查修复报告

> 修复基线：`docs/full-functional-audit-2026-08-14.md`  
> 修复与复验日期：2026-08-17  
> 结论：原审查 32 项问题均已完成代码、配置或交互层修复，并通过与风险相匹配的自动化/运行态复验。

## 1. 总体结论

| 维度 | 结论 |
|---|---|
| 原审查项修复完成度 | **32/32，100%** |
| 阻断项 | **5/5 已关闭** |
| 严重项 | **12/12 已关闭** |
| 一般项 | **15/15 已关闭** |
| 后端质量门 | Maven 18/18 模块 `clean install` 通过；498 项测试 0 failure / 0 error / 0 skipped，包含 CRM 真实 MySQL 集成测试 |
| 前端质量门 | TypeScript/Vite 生产构建通过；ESLint 0 error（保留 197 条历史 warning） |
| 浏览器端到端 | Chromium 18/18 通过，含三角色权限隔离与可逆写入闭环 |
| 依赖安全 | `npm audit` 0 vulnerability |
| 运行态 | 15 个 Compose 容器运行；带 healthcheck 的容器均为 healthy |
| 当前成熟度 | **预生产候选（90/100）**，适合内部试用、验收环境和受控预生产验证 |

“32/32 已修复”表示本次审查记录中的确定性缺陷已经关闭，不等同于无需任何生产准入工作。真实第三方 OAuth 回调、容量/混沌测试、备份恢复演练和独立渗透测试仍属于上线前环境性验证，详见第 6 节。

## 2. 逐条修复对照

### 2.1 阻断项

| ID | 原问题 | 修复与证据 | 状态 |
|---|---|---|---|
| B-01 | 公共 Feign 日期反序列化阻断跨模块流程 | 增加统一 Jackson Feign Decoder，同时兼容 Jackson 2/3 和 `yyyy-MM-dd HH:mm:ss`；补充真实 Feign/JSON 测试。运行态完成 Asset→Procurement 历史回扫，首次创建 8 张资产、重放产生 8 条 duplicate；供应商报价页面也可正常加载。 | 已关闭 |
| B-02 | Auth 用户接口泄露 BCrypt 密码哈希 | 用户读写改为 DTO/VO 白名单映射，实体密码增加 JSON 忽略；用户详情与列表不再序列化密码字段，并有服务测试覆盖。 | 已关闭 |
| B-03 | XSS JSON 请求体防护失效 | 为 Spring Boot 4/Jackson 3 注册字符串反序列化清洗模块并保留 Jackson 2 兼容；补充 MockMvc、Jackson 2/3 请求体集成测试。 | 已关闭 |
| B-04 | SRM 默认无法创建供应商 | SRM 不再要求用户输入模型版本；按租户和 `SRM_SUPPLIER_ONBOARDING` 自动解析当前发布模型。Workflow 启动时幂等初始化并发布必需模型，无法初始化时失败启动。 | 已关闭 |
| B-05 | Asset 调拨/处置模型类别不一致 | SQL、Workflow 初始化器、Asset 服务端 guard 和文档统一为 `ASSET_TRANSFER`、`ASSET_DISPOSAL`；前端移除数字模型 ID 输入。 | 已关闭 |

### 2.2 严重项

| ID | 原问题 | 修复与证据 | 状态 |
|---|---|---|---|
| S-01 | 用户更新实体直绑与批量赋值风险 | 创建/更新用户使用字段白名单 DTO；租户由可信上下文限定，角色和组织归属按当前租户校验，禁止客户端写入密码哈希、状态外字段或跨租户关联。 | 已关闭 |
| S-02 | 测试/lint 门禁红色，Docker 跳过测试 | 修复原有编译和 lint error；Docker 后端构建执行测试，不再使用 `skipTests`；CI 增加前后端、MySQL 集成和 E2E 门禁。 | 已关闭 |
| S-03 | 默认部署边界不安全 | 所有密钥改为 `.env` 必填；Redis 开启认证，内部 API/XXL-JOB/JWK/OAuth state 均显式注入；内部服务与中间件端口绑定 `127.0.0.1`，仅 Frontend/Gateway 作为公开入口。 | 已关闭 |
| S-04 | 静态 HTML 缺少浏览器安全响应头 | Nginx HTML 与静态资源 location 均显式返回 `nosniff`、`DENY`、Referrer-Policy、CSP；修复 `add_header` 继承问题及静态资源重复 Cache-Control。 | 已关闭 |
| S-05 | 前端 E2E 不能证明功能可用 | 删除截图式弱测试，新增断言式 Playwright：公共入口、管理员 7 模块、任务创建/触发/日志/删除、员工 403、供应商窄屏与后台隔离，共 18 条。 | 已关闭 |
| S-06 | Base/Gateway 无模块测试 | 为 Base 所有权、MQ 聚合失败和 Gateway 鉴权、租户头、安全头补充模块测试；全量 Maven 门禁执行通过。 | 已关闭 |
| S-07 | Dashboard 展示虚假运营数据 | 删除模拟统计、增长率和伪造图表，只展示真实能力入口、真实待办和实际技术版本。 | 已关闭 |
| S-08 | 数据库持久化与文档矛盾 | Compose 使用 `omni-mysql-data` 命名卷；停止脚本默认保留，只有显式 `down -v` 才删除。中/英/日/韩部署文档已同步。 | 已关闭 |
| S-09 | XSS 基线规则 fail-open | 基线规则使用安全字面量/有效正则，忽略空白规则，配置源故障时启用本地基线而不是放行；各业务服务实现 XSS 配置 SPI。 | 已关闭 |
| S-10 | 动态菜单失败造成重复导航循环 | 权限 store 引入 `idle/loading/loaded/failed` 状态机和单次加载；失败页提供显式重试，路由守卫不再反复导航/请求。 | 已关闭 |
| S-11 | 权限集合为空时动态菜单 fail-open | Auth 返回空权限时菜单失败关闭；前端也不再把空集合解释为全部可见，并有测试覆盖。 | 已关闭 |
| S-12 | 无权限动态路由显示空白页 | 增加稳定 403/404 catch-all 和权限失败页；员工直接访问无权限 CRM 深链的 E2E 已验证显示 403。 | 已关闭 |

### 2.3 一般项

| ID | 原问题 | 修复与证据 | 状态 |
|---|---|---|---|
| M-01 | 跨服务异常统一为“暂时不可用” | Gateway、Servlet、Feign 统一传播 32 位 Trace ID；聚合/远程调用日志记录 trace、状态、URL 和异常类型，页面保留可追踪错误。 | 已关闭 |
| M-02 | Asset 操作依赖手工数字 ID | 增加 Auth 用户/组织与 SRM 供应商远程候选接口；资产表单改为可搜索名称、账号、组织、供应商编号的选择器，保存 ID 与名称快照。社交账号无主组织时跳过候选而不拖垮整页。 | 已关闭 |
| M-03 | 异步审批缺少处理中反馈 | Procurement/Asset 启动审批后显示同步中/处理中状态并轮询最终结果，支持失败后的明确重试。 | 已关闭 |
| M-04 | Gateway 安全头重复、文档冲突 | 网关只生成一份安全头和一份 Trace ID，先移除伪造入站头；Nginx 负责浏览器 CSP，边界职责已写入架构/API/部署文档。 | 已关闭 |
| M-05 | 启动脚本端口与 Compose 不一致 | Windows/Linux 启动脚本、AGENTS、README 和四语部署文档统一 8100–8107、MySQL 13306 及公开/回环边界。 | 已关闭 |
| M-06 | 前端包体过大 | Vue Router 懒加载、业务 chunk 拆分、Element Plus 组件/图标选择性注册；最大业务相关 JS chunk 约 697 KB，全部低于 750 KB 预算。 | 已关闭 |
| M-07 | CRM MySQL 集成测试默认跳过 | 本地最终门和 CI 均提供真实 MySQL，4 条租户/数据权限/分页拦截器集成测试实际执行，非跳过。 | 已关闭 |
| M-08 | 公开页面预填默认凭据 | 登录、供应商门户、设备验证、管理员新增用户表单全部默认空白；E2E 在干净上下文中验证。租户创建必须显式输入管理员密码。 | 已关闭 |
| M-09 | 运行日志存在可行动告警 | 修复 Mockito agent、旧线程 API、Jackson bridge、Docker JSON 入口和业务服务默认安全用户等项目可控告警；JDK 25 原生访问在容器入口显式授权。保留的 Lombok/Javassist 兼容性提示及 Nacos 可选空配置提示不影响业务启动，列为依赖升级维护项。 | 已关闭 |
| M-10 | 已结束 Workflow 任务返回通用 500 | 历史任务完成/跳过场景改为 409 业务冲突，并返回刷新任务列表的可操作提示。 | 已关闭 |
| M-11 | 登出/令牌失效恢复混淆且不保留回跳 | 显式登出不显示“登录过期”；401 清理用户、权限和动态路由并保存安全回跳地址，登录后恢复原目标。 | 已关闭 |
| M-12 | 接口故障显示为“无数据” | MQ、供应商报价等页面区分 loading/empty/error，显示错误与重试；报价邀请改为进入相关页签后按需加载。 | 已关闭 |
| M-13 | Asset 分配表单缺少可见校验 | 负责人、组织等使用远程选择器，必填/跨字段校验在提交前可见，失败不再静默。 | 已关闭 |
| M-14 | 审批空态文案复用错误 | 采购/资产/工作流空态使用各自业务语义，不再显示不相关“饮水提醒”文案。 | 已关闭 |
| M-15 | 供应商门户移动端不流畅 | 增加窄屏布局、可折叠导航和触控友好间距；390×844 Playwright 场景通过且后台路由被隔离。 | 已关闭 |

## 3. 复验期间额外发现并修复的问题

| 新发现 | 处理结果 |
|---|---|
| 社交登录用户可能没有 `primary_unit_id`，导致 Asset 负责人候选整体 503 | Asset 候选服务跳过无主组织的门户/社交账号，租户不一致仍失败关闭；单元测试和登录后 UI 均通过。 |
| Procurement→Asset 回扫/实时消费缺少 DataScopeContext，幂等写后查询被拒绝 | MQ 消费与历史回扫同时设置 tenant identity 和 `TENANT` 数据范围，并在 `finally` 清理；真实回扫 200，二次重放 `created=0 / duplicate=8`。 |
| Nginx 子 location 的 `add_header` 覆盖 server 级安全头 | HTML 与 assets location 各自显式配置，运行态检查通过。 |
| 静态资源同时使用 `expires` 和 `add_header Cache-Control` 造成重复响应头 | 改为单一 `public, max-age=31536000, immutable`。 |
| npm 锁文件含 axios/form-data/nanoid/postcss 高危公告 | 升级到修复版本；最终 `npm audit` 为 0。 |
| 租户创建仍隐含固定管理员密码 | API 强制接收 8–64 位密码并 BCrypt 哈希，前端仅创建时显示必填密码。 |
| 后端容器入口产生 JDK 25 原生访问/JSONArgs 告警 | 使用固定 JSON 入口和 `--enable-native-access=ALL-UNNAMED`，镜像入口已检查。 |

## 4. 最终验证证据

| 验证项 | 结果 |
|---|---|
| 后端 | JDK 25，Maven Reactor 18/18 `SUCCESS`；498 项测试，0 failure / 0 error / 0 skipped |
| CRM 真实数据库 | `CrmMysqlInterceptorIntegrationTest` 4/4，通过 MySQL 8.4 实例执行 |
| 前端构建 | `vue-tsc -b && vite build` 通过；Docker `npm ci` + build 通过 |
| 前端 lint | 0 error；197 warning 为 BPMN/存量类型维护债，不阻断质量门 |
| E2E | Chromium 18/18，三角色、权限、核心页面、任务写入闭环均通过 |
| 依赖 | `npm audit` 0 vulnerability |
| HTTP 安全 | Nginx HTML/静态资源安全头完整；Gateway `X-Trace-ID` 唯一、`nosniff/DENY/Referrer-Policy` 完整 |
| 运行态联动 | Asset→Procurement 回扫 200；首次 created=8，重放 created=0/duplicate=8 |
| 数据持久化 | Compose 命名卷 `omni-stack-mysql-data` 正常挂载，旧匿名卷保留作迁移恢复点 |
| 容器运行态 | 15/15 容器运行，所有声明 healthcheck 的容器均为 healthy；Asset 最终镜像启动无自动生成默认安全密码告警 |

## 5. 成熟度评估

| 维度 | 评分 | 依据 |
|---|---:|---|
| 功能完整性 | 93 | 主要业务模块可进入，关键增删改查/审批/门户/任务链路有自动化证据 |
| 跨模块流程 | 93 | Auth、Workflow、SRM、Procurement、Asset 的核心 Feign/MQ/审批契约已闭环 |
| 安全与隔离 | 91 | RBAC/DataScope、XSS、密钥、网络边界、安全头和密码输出已系统修复 |
| 操作体验 | 89 | 数字 ID、空白页、错误态、异步反馈、移动端和登出恢复已改善 |
| 工程质量 | 90 | 全量后端、前端 build/lint、真实 MySQL、E2E、依赖审计均有门禁 |
| 文档一致性 | 92 | 架构、API、核心流程、三大业务设计、README 与四语部署文档已同步 |
| 生产运维准备 | 82 | 本地全栈健康，仍需目标环境容量、备份恢复、监控告警和安全演练 |

综合成熟度：**90/100，预生产候选**。

## 6. 不属于本次 32 项缺陷、但生产上线前仍需完成的门禁

1. 使用真实 GitHub/Gitee/Google 应用完成 OAuth 回调与失败恢复验收。
2. 在目标规格上执行容量、长稳、故障注入和 RocketMQ/Redis/MySQL 恢复测试。
3. 演练命名卷备份、跨主机恢复与数据库版本升级回滚。
4. 接入正式指标/日志/告警平台并定义 SLO、告警路由和值班流程。
5. 清理 197 条存量 ESLint warning，重点收敛 BPMN 模块的 `any` 类型。
6. 进行独立依赖/SAST/DAST/渗透测试，并替换所有本地演示账号和临时密钥。

这些项目决定“是否可直接公网生产上线”，但不影响本次功能审查缺陷的关闭结论。
