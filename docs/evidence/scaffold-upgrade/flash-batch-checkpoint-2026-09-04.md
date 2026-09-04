# Flash 批次验收 checkpoint（2026-09-04 · ZCode GLM-5.3-Flash）

执行方案：`zcode-glm53-flash-execution-plan-2026-09-04.md`。批次基线 `52b2c5c` → 本批提交见 §5。

TOKEN_USAGE=UNKNOWN（平台无可信计数，按约束不编造）。

## 1. ZF 状态

| ZF | 状态 | 摘要 |
| --- | --- | --- |
| ZF-0 | DONE | 接管核验（LOCAL=GITEE=GITHUB=`52b2c5c`，无分叉；Docker `omni-wp09-docs` 15 容器 healthy）；current state 统一；四份方案/提示词纳入首个提交 `29379b5` |
| ZF-1 | DONE | findings 队列与独立复核包 `i18n-findings-resolution-2026-09-04.md`；objective 类缺漏清零（parity with_findings 74→63，剩余全为 EXPECTED_LOCALIZATION + R1 冲突记录）；四门禁通过；提交 `8b43263` |
| ZF-2 | PARTIAL | 三项只读能力核实完成（§3）；SRM 两组截图 E2E 经 5 轮有证据的定向修复仍未通过 → BLOCKED（§2）；数据残留 0 |
| ZF-3 | DONE | §4 验证台账 |
| ZF-4 | DONE | 精确提交、fast-forward 推送、本 checkpoint |

**FLASH_BATCH=PARTIAL_CHECKPOINT**（ZF-2 的 SRM 截图项未闭环；其余全部满足）。

## 2. SRM 截图场景 BLOCKED 记录（→ STRONG_MODEL_QUEUE）

### 2.1 admission-lifecycle（BLOCKED，外部/强模型）

两个子路径均被硬约束阻断：

1. **门户注册 Saga**（inviteToken + requestId + REGISTERING）：
   - 证据：公开注册 `POST /api/auth/register` 走 `captchaService.validate`（`AuthController.java:128`）；后端无测试 hook/CaptchaService 无 dev bypass（实查 `CaptchaService.java`）。
   - 本批约束禁止 CAPTCHA bypass 与 Redis 验证码读取（`scripts/.work/get-token.ps1` 的 Redis 读取模式因此未复用）。
2. **准入审批至 APPROVED**：`scripts/sql/seed/workflow.sql:289` 的 `supplier-onboarding` 模型为 3 级会签（SRM_MANAGER→SRM_COMPLIANCE→高管，`omni:assignment` 候选角色）；种子中 admin 仅绑定 SUPER_ADMIN/SRM_ADMIN/PROCUREMENT_MANAGER（`auth.sql:366`）。多身份会签属本批明确禁止项，且与既有 BLOCKED 登记一致。

**恢复入口**：授权 2 个测试身份（SRM_MANAGER/SRM_COMPLIANCE 角色）+ 公开注册验证码的受控测试路径（如测试环境验证码白名单，需用户决策）→ 之后即可按 §2.3 的 spec 骨架续跑。

### 2.2 detail-and-action-states（BLOCKED，技术）

- 已设计并落地 spec 骨架：`omni-frontend/e2e-docs/flows/srm-supplier-detail.flows.spec.ts`（**untracked，未提交**——验证未通过，按「半成品不入库」处理；lint/tsc 均 0 错误）。
- 流程：admin UI 建供应商（PENDING_REVIEW 可删）→ 校验失败态 → 详情弹层（编辑对话框）→ 列表真实准入状态 → afterAll 正式 DELETE 契约 + 回查 0 残留。
- 5 轮定向修复（均有新证据）：404 路由 → 定位器（prop 不渲染 DOM）→ 创建成功但对话框未关（首启 >8s）→ 点击重复触发（产出 8 行重复，已全部删除）→ 单击+60s 轮询 → 仍未创建。根因证据：el-select 选项选择后表单提交链路在 headless 下不确定（保存点击后 validate 静默失败或请求未发出），与 `system-dictionary` 用例同型交互但本页表单更复杂（12 字段 + 3 个 el-select）。
- 风险提示：管理页创建接口内部同步 Feign 启动工作流，慢响应与超时是常态；spec 内已内置 150s 用例超时与 60s 完成轮询。

**恢复入口**：先人工在浏览器跑一遍该 spec 的交互序列定位哪一步吞掉提交（trace.zip 已留存于 `omni-frontend/.artifacts/docs-playwright/`，excluded）；或改用 API 直建 fixture + UI 只读截图（详情弹层/列表状态各一张，省去对话框交互）。

### 2.3 本批 SRM 数据收口（实测）

- 失败运行产生的供应商全部经正式 API 删除（PENDING_REVIEW 可删）：ids 2-6、7-11、12-13、28-35 等逐批删除，`keyword=E2ESD/E2EPROBE` 回查非种子残留 **0**。
- 凭证收口：`destroy-e2e-credentials.ps1` 已执行（ENV_CLEARED=process-scope）；本批签发的 9 个 `tokens-20260905-*.json`（TTL 1200s 已过期）已逐一定向删除，`%TEMP%/omni-e2e-tokens` 仅余历史会话文件（不属本批，未触碰）。
- 未登记半成品截图（create-validation ×4，manifest 无对应条目）已删除；untracked spec 骨架保留于 `omni-frontend/e2e-docs/flows/srm-supplier-detail.flows.spec.ts`（不提交，见 §2.2）。
- `keyword` 搜索对种子 id=1（APPROVED，历史数据）任意关键字均命中，疑似搜索实现怪癖，与本批无关，未触碰。

## 3. 只读能力核实（ZF-2 三项，全部 CLOSED）

| 项 | 结论 | 证据 |
| --- | --- | --- |
| system-management `config`（参数配置） | **PRODUCT_GAP**（页面不存在） | `omni-frontend/src/views/system/` 仅 auditlog/authrecord/online/org/permission/role/tenant/user/xssconfig；无 config 路由与视图 |
| system-management `login-record`（登录日志） | **PRODUCT_GAP**（后端有数据、前端无页面） | `sys_login_log` 由后端留存（AGENTS.md 认证约束），前端无对应视图；`system/authrecord` 菜单名为「授权记录」（auth.sql:59），非登录日志 |
| scaffold-development（CLI 能力） | 能力真实存在；证据形态待决策 → **EVIDENCE_FORMAT_DECISION** | `tools/omni-cli/src/`（cli.ts/catalog.ts/development.ts/doctor.ts 等）；建议证据：`omni catalog validate`、`dev up --preset` 真实终端输出登记（非 UI 截图） |
| operations（可观测基础设施） | 能力真实存在；证据形态待决策 → **EVIDENCE_FORMAT_DECISION** | `compose.observability.yaml`（prometheus v3.14.0 等）；建议证据：仪表盘截图或 SLO 文档引用，需独立决策 |

> EVIDENCE_FORMAT_DECISION 两项：本批不自行 exempt、不改 coverage 门槛，仅登记建议（真实命令输出/仪表盘证据）。

## 4. 验证台账（ZF-3，均实跑）

| 验证 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| parity 复扫（ZF-1 批末） | `node scripts/.work/qoder-doc-parity.mjs` | 0 | with_findings 74→63；objective 类 = 0 |
| 链接 | `npm run docs:links:check` | 0 | 通过 |
| 敏感 | `docs-quality.mjs --scope=sensitive --allow-draft` | 0 | 通过 |
| i18n | `docs-quality.mjs --scope=i18n --allow-draft` | 0 | 通过 |
| spec lint/tsc | `eslint --max-warnings 0` + `tsc --strict` | 0 | 通过（spec 未提交） |
| SRM E2E 套件 | `playwright test srm-supplier-detail.flows` ×6 | 1 | 4 failed（BLOCKED，见 §2） |
| strict 截图门禁 | `npm run docs:screenshots:check` | 1 | 与冻结基线一致：8 个模块覆盖失败（SRM 仍 partial、gaps 不变），无新增错误 |
| frontend build | `npm run build` | 0 | vue-tsc + vite 构建成功（1.89s） |
| frontend lint（tracked 范围） | `npx eslint src e2e-docs --max-warnings 0` | 0 | 通过 |
| frontend lint（npm run lint） | `npm run lint` | 1 | 38 errors 全部来自存量 excluded untracked 临时脚本（omni-frontend/scripts/.work/*.mjs），非本批产物，按约束不修改不删除 |

> strict exit 1 为既有基线的真实呈现，未隐藏未改写；lint 失败根因在 excluded untracked 项，tracked 代码 0 错误。

## 5. 提交与远端

| 提交 | 内容 |
| --- | --- |
| `29379b5` | ZF-0：current state 统一 + 四份方案/提示词 |
| `8b43263` | ZF-1：i18n objective 缺漏整改（49 文件）+ findings 台账 |
| 本批末提交 | ZF-4：checkpoint + current state 更新 |

推送：一次性 fast-forward 核验后推送双远端（结果见本文件提交后记录）。

## 6. 遗留与下一恢复入口

1. SRM 截图场景：按 §2.1/§2.2 恢复入口（需授权输入或人工定位交互问题）。
2. EVIDENCE_FORMAT_DECISION 两项：等待独立决策（真实命令输出 vs 仪表盘证据）。
3. R1（api-contract/core-flows 回调端口 8102 vs 代码 8100）：等待独立复核者修订中文源。
4. 独立翻译复核：`i18n-findings-resolution-2026-09-04.md` §4 签核区待填。
5. 高风险队列（本批禁止项，见 Flash 方案 §4）：采购 bootstrap 数据恢复（tenant=1 ids 1-13）、workflow 多身份会签、MQ 故障注入、G1/G7/WP-10/G8 等，维持原状。
