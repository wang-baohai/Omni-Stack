# Omni-Stack 学习与维护 Wiki

适用基线：主干 `fc165e3` 及其文档整改分支。默认读者具备基本 Java/Vue 知识，第一次接触本项目。中文是内容基准；[English](index.en.md)、[日本語](index.jp.md)、[한국어](index.kr.md) 与中文同步，实际译文复核状态（区分 HUMAN_REVIEW 与所有者授权的 AI_REVIEW）见 [文档清单](../docs-manifest.yaml)。

## 1. 从零开始

按顺序完成前四步，再按角色选择业务路径。每一步应在本机 Docker 中验证预期结果。

| 步骤 | 阅读和操作 | 完成后应看到 |
|---|---|---|
| 1 | [获取代码、认识目录与首次启动](../guides/quick-start.md) | migrator 正常退出，所选服务健康，前端可访问 |
| 2 | [登录与租户](../guides/authentication.md) | 真实验证码登录，正确租户和用户身份 |
| 3 | [角色、菜单、按钮和数据范围](../guides/permissions.md) | 管理员与员工看见不同能力，越权请求被拒绝 |
| 4 | [正式种子、教学数据和测试数据](data-and-fixtures.md) | 能区分空环境、基础种子和业务演示记录 |
| 5 | [预设选择](../preset-quick-selection.md)、[生成项目](../guides/scaffold-development.md) | 能解释启动子集与生成裁剪工程的区别 |

## 2. 按角色学习业务

| 角色或目标 | 路径 | 前置条件 |
|---|---|---|
| 平台管理员 | [系统、安全与审计](../guides/system-security-audit.md) | core；租户管理员角色 |
| 员工和审批人 | [Workflow](../guides/workflow-approval.md)、[个人与系统任务](../guides/scheduling.md) | workflow；候选人和任务所有权 |
| 销售人员 | [CRM 线索到商机](../guides/crm-flow.md) | crm；销售角色和数据范围 |
| 供应商管理员、供应商 | [SRM 准入与门户](../guides/srm-flow.md) | supply-chain；邀请、SUPPLIER 角色和有效关联 |
| 申请人、采购员、供应商 | [请购到收货](../guides/procurement-flow.md) | supply-chain；审批模型、有效供应商和采购权限 |
| 资产管理员、使用人、审批人 | [建卡、领用、调拨与处置](../guides/asset-flow.md) | supply-chain；合格资产化收货和审批模型 |
| 运维人员 | [运维、备份和升级](../guides/operations-upgrade.md)、[排错](../guides/troubleshooting.md) | 已确认本地环境与备份边界 |

初始种子不承诺存在历史截图中的订单或审批。截图里的业务号只是示例；使用本次操作返回的 ID。不要用 SQL 改状态跳过审批、报价或收货。

## 3. 学习源码和二次开发

- [源码阅读与断点路径](source-debugging.md)：从请求追到权限、事务和事件，查看测试入口与失败条件。
- [服务生成与 CRUD](../guides/scaffold-development.md)：先 dry-run，再生成、接入并验证。
- [预设依赖矩阵](../preset-dependency-matrix.md)、[维护](../preset-maintenance.md)、[自定义](../custom-preset-tutorial.md)、[升级](../preset-upgrade-guide.md)：事实表由 catalog 派生。
- [架构](../architecture.md)、[API 契约](../api-contract.md)、[核心流程](../core-flows.md)：维护者的当前工程事实。
- [后端规范](../backend-patterns.md)、[前端规范](../frontend-patterns.md)：修改代码前按任务阅读。

## 4. 常用术语

| 术语 | 在本项目中的含义 |
|---|---|
| preset / 预设 | 模块选择；catalog 计算完整依赖闭包 |
| migrator | 应用启动前执行结构迁移、幂等种子与引导配置的一次性进程 |
| tenant / 租户 | 数据隔离边界；ALL 数据范围仍不能跨租户 |
| RBAC / DataScope | 功能权限与行级可见范围，两者同时有效 |
| Gateway assertion | 网关签名的身份断言，下游不信任客户端身份头 |
| Outbox / Inbox | 同事务保存待发事件／消费去重凭据 |
| Saga | 多服务步骤及补偿，不能代替跨服务 ACID 事务 |
| fixture | 有明确租户、业务键和清理边界的测试或教学数据准备 |
| ADR | 记录决定、理由、后果和替代关系的历史决策 |
| Harness | 事实、约束、工具和验证形成的工程执行闭环 |

## 5. 文档网站与维护

[独立文档站](site-maintenance.md)介绍 Docker 构建、4173 端口、本地搜索、静态产物和跨平台源码链接。仓库 Markdown 是唯一正文来源；网站的语言目录由清单生成，不再维护第二套 Wiki。

当前事实、设计、教程、ADR 和验收历史各有职责，见 [仓库治理](../repository-governance.md)。旧验收记录只证明当时的范围，不能代替当前步骤验证。站点预览不代表译文复核或发布已完成。
