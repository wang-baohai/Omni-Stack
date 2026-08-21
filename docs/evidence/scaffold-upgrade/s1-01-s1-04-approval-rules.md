# S1-01～S1-04 请购审批规则业务化实现证据

日期：2026-08-21  
分支：`codex/scaffold-upgrade`  
隔离环境：Compose project `omni-g1`，独立数据库卷 `omni-stack-g1-compose-data`

## 结论

S1-01～S1-04 的数据库兼容、Workflow 安全只读契约、Procurement 业务外观 API 和业务化前端组件已经实现，
并在隔离全栈完成真实验证码登录后的 API 功能流验证。S1-06 已将前端 lint 基线从 197 条 warning
清零并启用 `--max-warnings 0`。G2 尚未关闭：三视口、键盘和浏览器冲突/权限/降级 E2E 仍需在
浏览器控制运行时恢复后补齐。

## 已实现范围

- `proc_approval_route.route_name` 前向迁移与旧数据回填。
- 服务端生成不可编辑的 `APR-{ULID}` 技术编码，新增规则默认优先级为同品类最大值加 10。
- Workflow 已发布版本列表、最多 200 条批量解析和不返回 BPMN/XML 的安全审批图。
- Procurement 流程选项、真实匹配试算、覆盖分析和停删影响分析。
- 规则列表批量聚合 Workflow 元数据；依赖异常时只读降级、写入失败关闭。
- 三步向导、匹配测试器、持续覆盖提示、流程预览、高级信息和移动端卡片布局。
- 默认采购流程纳入 Workflow 必需模型启动初始化器，幂等发布为 `category=purchase`。
- 租户配置行锁跳过会重排行锁 SQL 的 TenantLine 解析，同时保留显式 `tenant_id` 限定。

## 自动化验证

| 验证项 | 结果 |
|---|---|
| 数据迁移二次执行 | 所有目标 `Run: 0`，24 项种子断言通过 |
| 后端完整 Reactor | 19 modules，`clean install` 成功，0 failure |
| Workflow 模块测试 | 46 tests，0 failure，0 error |
| Procurement 模块测试 | 168 tests，0 failure，0 error |
| 前端生产构建 | 通过，2447 modules transformed |
| 当前 lint 门禁 | `eslint . --max-warnings 0`，0 error / 0 warning |

## S1-06 前端质量门禁

- 将动态表单 JSON Schema 解析收敛到共享强类型和运行时守卫，兼容扁平格式与标准
  `object/properties` 格式。
- 为 bpmn-js 画布、Moddle、属性面板、Context Pad 和流程进度 Viewer 建立项目最小类型视图，
  清除 BPMN 设计器中的显式 `any`。
- 将菜单、流程、SRM 风险等异步失败从 `console` 或不透明对象访问改为统一的未知错误提取；
  用户可感知的流程进度加载失败通过正式消息组件呈现。
- 将 lint 脚本固化为零警告门禁，后续新增 warning 会直接导致命令失败。

## 隔离全栈功能流

使用 `/api/auth/captcha` 生成的一次性验证码完成真实密码登录，然后依次执行：

1. 查询 `workflow-options`，返回 1 个 `category=purchase` 的“采购申请审批”发布版本。
2. 创建默认规则，返回 200；技术编码前缀为 `APR-`，默认优先级为 10。
3. 使用测试品类和金额 `100.00` 调用 `match-preview`，结果为 `MATCHED` 且使用默认规则。
4. 安全审批图返回 5 个节点、4 条边，没有原始 BPMN XML 或 designer JSON。
5. 覆盖分析返回 13 个有效物料品类；停删影响分析报告 13 个新增断档。
6. 更新规则名称并停用，乐观锁版本从 0 增加到 1。
7. 删除验收规则，按关键字复查残留记录数为 0。

## 运行时发现并修复的缺陷

1. 默认采购流程只有草稿，导致向导无可选流程：将 `procurement-approval → purchase` 加入必需流程模型的
   启动校正和自动发布清单。
2. TenantLine SQL 解析将 `LIMIT 1 FOR UPDATE` 重排为非法 MySQL 语句：锁查询显式限定租户并使用
   `@InterceptorIgnore(tenantLine = "true")`，增加 Mapper SQL 契约测试防止回归。

## 尚未关闭的 G2 项

- Codex 内置浏览器运行时当前报“无法写入 kernel assets：找不到指定路径”，无法执行真实页面自动化；
  已按浏览器技能重置连接并确认插件脚本存在，但运行时仍失败。
- 待运行时恢复后补做 390×844、768×1024、1440×900 三视口、键盘焦点、权限不足、Workflow
  降级和历史冲突数据的浏览器 E2E，并生成正式截图。
