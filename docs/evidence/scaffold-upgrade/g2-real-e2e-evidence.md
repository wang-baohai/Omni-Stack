# G2 确定性真实 E2E 执行证据（2026-09-01，HEAD efd651e）

## 执行环境与认证

- 运行栈：`omni-wp09-docs` compose 项目（前端 3000、网关 8102、auth 8100、workflow 8103、procurement 8106、MySQL 13306、Redis 6379）
- 认证：`E2eTokenFixture`（test classpath，commit a8ab673 既有）按 `verifyFullE2e` 模式执行：
  `mvnw -pl omni-auth exec:java -DclasspathScope=test` → 短期 JWT（TTL 600s）→ process-scope 注入 Playwright 子进程 → finally 删除 Token 文件（三轮 TOKEN_CLEANED=True）
- 身份：`admin`(SUPER_ADMIN) / `zhangsan`(EMPLOYEE)，权限链经 auth.sql L451-457/L496-508 实证
- 目标集：`npx playwright test e2e/functional.spec.ts -g "G2"`（6 用例）

## 四轮执行结果

| 轮次 | 结果 | 说明 |
| --- | --- | --- |
| 1 | 2 passed / 4 failed | 暴露 TEST_DEFECT #1：fixture 品类不存在（服务端 404 品类校验）；#2：admin 页面测试缺 authenticate（路由守卫重定向登录页） |
| 2 | 4 passed / 2 failed | 修复 #1/#2 生效；暴露 G2-2/G2-3 与业务契约冲突（见下） |
| 3 | 4 passed / 2 failed | G2-3 失效注入改「二次发布归档」实测不可达（版本记录 1:1 翻转，归档条件永不触发） |
| 4 | **5 passed / 1 skipped**（lint 通过） | 用户决策落地：G2-2 改写入端 409 验收；G2-3 条件 skip（E2E_WORKFLOW_FAULT_INJECTION） |

## 最终状态（G2=PARTIAL）

| 场景 | 状态 | 验收形式 |
| --- | --- | --- |
| G2-1 权限 | PASS | E2E 真实执行（admin 全通；zhangsan 页面 403 + API HTTP 403，AUTHENTICATED_BUT_FORBIDDEN 区分） |
| G2-2 AMBIGUOUS 防御 | **PASS（分层验收）** | API/E2E：重叠创建确定性 409 + 不变式成立证据（唯一命中）+ impact 契约；resolver 单测：脏数据 AMBIGUOUS + conflictingRouteIds |
| G2-3 WORKFLOW_UNAVAILABLE | **DEFERRED_TO_ISOLATED_CI** | 条件 skip（E2E_WORKFLOW_FAULT_INJECTION），断言语义保留；降级语义由 ApprovalRouteInsightServiceImplTest 闭环 |
| G2-4 金额边界 | PASS | E2E 真实执行 |
| G2-5 路由语义 | PASS | E2E 真实执行 |
| G2-6 UI 可用性 | PASS | E2E 真实执行 |

**G2-2 分层验收记录**（用户裁决：方案 a）：
- API/E2E 层：创建同品类重叠 ACTIVE 规则 → `overlap.code === 409`（写入端不变式确定性拒绝）；同品类仅一条 ACTIVE 时 match-preview 唯一命中 fixture 规则；impact 契约保持
- resolver 单测层：`ApprovalRouteResolverTest:76`（脏数据 AMBIGUOUS outcome）、`ApprovalRouteCoverageAnalyzerTest:56`（AMBIGUOUS/GAP segments）
- 未通过 DB 直写制造脏数据，未放宽为 outcome 白名单

**G2-3 DEFERRED_TO_ISOLATED_CI 记录**（用户裁决）：
- 最小定位：单测层用 Mockito mock `workflowClient.getApprovalPreview` 注入；E2E 基础设施无任何可复用 stub（functional.spec.ts/playwright.config.ts 中 route/proxy/fulfill/mock 零匹配）；失效点在 procurement 服务内部 Feign 调用，黑盒 E2E 不可达且禁停共享容器
- 测试保留原断言语义（WORKFLOW_UNAVAILABLE + routeId/routeName），以 `E2E_WORKFLOW_FAULT_INJECTION` 条件 skip 挂起，未删除、未改写
- 服务层既有单测已验证降级语义；浏览器/集成层仍缺真实 dependency outage/fault injection 实证；不因此标记 G2 CLOSED

## G2-2/G2-3 验收标准冲突（非产品缺陷）

**G2-2（断言运行时 AMBIGUOUS）**：
- `ApprovalRouteServiceImpl.validateNoActiveOverlap`（L365-383）：create/update 在租户锁内校验「同品类 ACTIVE 区间不重叠」，违反即 409
- `ApprovalRouteResolver.evaluate`（L49-62）与 `ApprovalRoutePolicy.matching`（L147-157）只评估 ACTIVE
- 结论：不变式「同品类 ACTIVE 两两不重叠」由写入端维护 → AMBIGUOUS（resolver 注释：同一优先层级命中多条**脏数据**）从 API 黑盒不可达

**G2-3（删模型制造版本失效）**：
- `WorkflowModelServiceImpl.deleteModel`（L324-333）：存在 PUBLISHED 版本即拒绝删除（「请先归档」）
- 版本记录与模型 1:1：`publishModel` 发布 `currentDraftVersionId` 指向的同一记录（L160-260），二次发布归档条件 `currentPublishedVersionId.equals(draft.getId())` 恒 false；无独立归档/撤销发布端点
- 约束：禁止停止共享 workflow 容器 → WORKFLOW_UNAVAILABLE 从 API 黑盒不可达

**后端测试层既有闭环**（本次核实）：
- `ApprovalRouteResolverTest:76`（AMBIGUOUS outcome）
- `ApprovalRouteInsightServiceImplTest:111`（WORKFLOW_UNAVAILABLE）
- `ApprovalRouteCoverageAnalyzerTest:56`（AMBIGUOUS/GAP segments）

## 本批修改

- `omni-frontend/e2e/functional.spec.ts`：+品类 fixture（createCategoryFixture/deleteCategorySilently）、admin 页面 authenticate、G2-3 失效注入改版（第 3 轮形态，待决策后按方案调整）；lint 通过

## 决策结果（2026-09-01 用户裁决，已落地）

- **G2-2：方案 a 已落地** —— E2E 改验写入端不变式（409），分层验收见上表；`G2-2=PASS`
- **G2-3：DEFERRED_TO_ISOLATED_CI** —— 条件 skip 挂起，`G2-3=DEFERRED_TO_ISOLATED_CI`
- **`G2=PARTIAL`**（G2-3 需隔离 CI 故障注入设施后真实执行；不得在当前环境改写验收语义制造绿色）

Token 估算：本批 ~110K。

## 附二：menu-failure 闭环与白屏结论更正（2026-09-01 同日）

**更正**：本文件前批「菜单接口 500 → 白屏」结论**经诊断推翻**。诊断实证：菜单接口 500 时守卫正常重定向
`/menu-load-error` 降级页（`views/error/menu-load/index.vue`），页面完好渲染本地化标题/描述与「重新加载/返回首页」
恢复入口（zh/en 双语实测），**产品降级设计完好，无白屏缺陷**。首批 menu-failure 测试失败为
**TEST_DEFECT（测试断言设计错误）**：断言 `body contains 'admin'`，而降级页无 header 用户名，永假。

**修复与收口**：断言改为降级页真实要素（`.menu-error-page` + `errorPage.menuLoadTitle` 四语言 + 重试按钮 + 无 403）
→ menu-failure 四语言 PASS；新增 4 张正式图入 manifest（累计 66 条）；coverage `gaps: []` →
**`permissions-exceptions = covered`**；`docs:screenshots:check` 红灯 **12 → 11**；permissions.md 指南图 7 嵌入。
另：根级未知路径 `/e2e-nonexistent-404-probe` 触发 catch-all NotFound（`/admin/*` 内未知路径被动态 feature 路由吞掉，
已按根级路径语义验收）。

## 附三：scheduling-workspace 深度截图闭环（2026-09-02，根因定位后更正）

- **前批「KNOWN_DEVIATION / 白屏疑云」结论更正**：ja/ko 创建对话框交互失败根因为
  **TEST_DEFECT**——表单 label locator 误用 `common.userJobTypes`（'Job Types' 复数/日韩误译），
  真实 key 为 `userJob.jobType`（en/ja/ko = 'Job Type' 单数）；行编辑按钮 locator 漏了 ko「편집」。
  **产品无缺陷，降级与渲染正常**。
- **修复后**：scheduling targeted **12 passed / 0 failed / 0 skipped**（四语言全绿，zh/en 无回归）
- **已闭环**：job-type（四语言入口图）、lifecycle-results（创建→编辑改名→列表真实反映，
  喝水提醒 demo handler 无外部副作用）、detail-and-action-states（编辑对话框 + 行操作）、
  failure-states（deterministic API fault：创建接口 500 → 真实错误消息）
- **`scheduling-workspace = covered`**（gaps 清零）；manifest +12 条（累计 78 条）；scheduling.md 指南图 2-4 嵌入
- 清理：deleteMyJob 正式契约；E2E_MUTATIONS 自建自清理多轮实证

## 附四：G2-3 isolated CI facility（2026-09-02，READY_FOR_ISOLATED_CI_EXECUTION）

### 已验证（静态层）

- 方案：ISOLATED_DEPENDENCY_OUTAGE——独立 compose project（`-p omni-g2-outage`）内真实停止 `omni-workflow`，
  procurement Feign 超时（connect 3s/read 5s）快速失败 → 降级断言；不触碰任何共享环境
- `functional.spec.ts`：G2-3 拆为 `G2OUTAGE-PREPARE`（workflow 健康时创建唯一品类+模型+规则，
  落盘 routeId/routeName/categoryCode 至 `G2_OUTAGE_STATE_FILE`）与 `G2OUTAGE-ASSERT`
  （outage 下 match-preview → 断言 WORKFLOW_UNAVAILABLE + routeId/routeName 保留）
- `.github/workflows/quality.yml`：新增 `g2-workflow-unavailable-e2e` job（schedule/dispatch 触发，
  8 阶段编排：隔离栈启动→健康→PREPARE→停 workflow→ASSERT→always down -v）
- 静态验证：workflow YAML parse ✓ / eslint 0 warnings ✓ / playwright --list 发现两测试 ✓ /
  `docker compose -p <probe> config --quiet` ✓（未打印输出，零 secret 泄露）

### 尚未验证

- actual dependency outage E2E（需 GitHub Actions isolated runner 实际执行该 job）

### 前置条件

- `secrets.E2E_ADMIN_TOKEN`（复用现有 CI secret 机制，无新 secret 来源）

### 状态

**`G2-3 = PASS (LOCAL_ACCEPTANCE)`**（2026-09-02 验收方裁决）

## G2-3 Acceptance Decision（2026-09-02，验收方裁决）

当前验收采用**本地验证结果作为正式依据**：

- Service 层降级语义单测：`ApprovalRouteInsightServiceImplTest` 覆盖 Workflow client failure
  → `WORKFLOW_UNAVAILABLE` / routeId 保留 / routeName 保留
- G2 本地浏览器/API 主链路真实执行：G2-1/2/4/5/6 全部 PASS
- 产品当前真实 fallback 行为：Feign 快速失败（connect 3s/read 5s）+ matchedPreview catch FeignException → 降级
- isolated CI facility 已实现并发布（wiring commit `7879680`）

**限制（诚实登记）**：

`REMOTE_ISOLATED_OUTAGE_CI = NOT_EXECUTED`

远程 GitHub Actions isolated outage run 尚未执行。该 run 登记为
**`G2_REMOTE_ISOLATED_CI = FOLLOW_UP_NON_BLOCKING`**（regression hardening 任务），
**不是**当前 G2 验收决策的前置条件。`DISPATCH_SCOPE_NOT_ISOLATED`（Quality Gate 6 个无门槛 job）
同步登记为 `REMOTE_CI_DISPATCH_ISOLATION = FOLLOW_UP`。

**`G2 = CLOSED`**（G2-1/2/3/4/5/6 全部 PASS，G2-3 验收类型 LOCAL_ACCEPTANCE）

## 附：深度截图批次记录（2026-09-01 同日）

**前置发现与修复**：
1. 运行中的前端容器为**构建镜像快照**，不含本会话 ja/ko locale 翻译 → ja/ko 审批规则页渲染英文 fallback（旧图成因）。处置：重建 `omni-wp09-docs` 前端镜像并重启（不影响 workflow/数据库/网关）
2. 四场景断言文案用了菜单名而非页面主区域真实渲染文案 → 按 locale 反查修正（scheduling→`systemJob.cron` 四语言、workflow-models→`workflow.category`、srm→`srmOverviewPage.totalSuppliers`、asset→`assetOverviewPage.total`）

**最终执行**：docs 截图套件 **58 passed / 0 failed（59.9s）**，DOCS_EXIT=0，TOKEN_CLEANED=True；ja-JP/ko-KR 审批规则重拍完成（日文/韩文渲染实证，抽查 ja 图：标题/描述/按钮全日文）

**coverage 状态（诚实保持）**：`docs:screenshots:check` 12 项红灯 = 9 partial + 3 missing —— 各模块四语言入口/概览已齐且带核心文案断言，但深度状态（create-or-edit/key-action/success/failure-or-forbidden）尚未拍摄，不虚升 covered；permissions-exceptions 可用本批 G2 真实 403 场景补拍（待投入决策）；scaffold-development/operations 需 CLI/可观测栈截图环境

**已知瑕疵登记**：审批规则页 coverage 卡片为异步加载组件，部分截图存在 spinner 局部（四语言一致，非语言错误/混合语言）

**待清理登记**：G2 第 3 轮在 workflow-models 库中残留 1 条测试模型「G2 E2E 失效模型注入」（含 PUBLISHED 版本，API 删除被保护拒绝，无规则绑定无功能影响；清理需 DB 操作或模型归档能力，登记待处理）
