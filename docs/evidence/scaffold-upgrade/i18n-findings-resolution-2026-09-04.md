# i18n findings 分类与处置台账（2026-09-04 · ZF-1）

输入：`scripts/.work/qoder-doc-parity.json` / `.txt`（2026-09-04 22:19 生成，HEAD `52b2c5c` 时点；documents=38, comparisons=102, with_findings=74, clean=28, ALIGNED=102）。
明细提取：`scripts/.work/qoder-doc-parity-findings-full.txt`。

分类口径（Flash 方案 ZF-1）：

- `EXPECTED_LOCALIZATION`：合理语言差异，不改动；逐项记录理由。
- `FIX_REQUIRED`：API 路径、权限码、字段/枚举、命令、代码块、数字、表格或流程步骤确有缺失/错误，本轮修复。
- `INDEPENDENT_REVIEW_REQUIRED`：语义自然度/领域表达/文化本地化，或中文源与代码冲突，只能由独立复核者判定。

客观事实核验（2026-09-04 实测）：

- 截图 locale 图片齐备：`docs/images/{en-US,ja-JP,ko-KR}/` 各 57 张、`zh-CN/` 59 张；`session-expired-dialog.png` 四语言均存在 → 译文引用本语种图片为既有约定，链接门禁已验证可解析。
- OAuth 回调端口：`omni-auth/src/main/resources/application.yml:108-116` 默认 `${*_REDIRECT_URI:http://localhost:8100/...}`（8100）；`docs/api-contract.md`、`docs/core-flows.md` 中文源写 `:8102`（网关）。**冲突记录见 §C-1**。
- 前端菜单实际文案（`omni-frontend/src/locales/*.ts`）：en `Monitoring`→`MQ Messages`、`System Management`→`XSS Protection`；ja `運用監視`→`メッセージ記録`、`システム管理`→`XSS 防御`；ko `운영 모니터링`→`메시지 기록`、`시스템 관리`→`XSS 보호`。

签核边界：本台账不含独立语义签核；`synchronized` / `reviewed_at` 保持不动，待独立复核者在 §4 指定位置签核。

## 1. FIX_REQUIRED（本轮修复队列）

| # | 源文档 | 语种 | 客观缺漏（parity 口径） | 处置 | 状态 |
| --- | --- | --- | --- | --- | --- |
| F1 | api-contract (P0) | en/ja/ko | 缺 §MQ runtime 相关：2 标题、2 代码块、10 表行、`/api/base/mq-message/runtime`、`/api/internal/`、`/api/**`、`/api/internal/**`、`DENY`、`FULL`、`OUTBOX_ONLY`、`InternalApiAuthFilter`、`X-Gateway-Forwarded`、`X-Trace-Id`、`base:mqmessage:list`、`GET /api/base/mq-message/runtime` | 按中文源补译缺失小节与 token | DONE |
| F2 | guide-authentication (P0) | en/ja/ko | 缺会话过期章节（1 标题 + `session-expired-dialog` 图片及锚点）；ja/ko 缺 `omni-auth` token | 按中文源补译章节，引用本语种图片（已验证存在） | DONE |
| F3 | srm | en/ja/ko | 缺 4 条内部报价 API 路径、`srm:portal:quotation` 权限码、`DRAFT/CLOSED/AWARDED/CANCELLED`、`status IN (INVITED, QUOTED)`、`status=SENT`、`{tenantId,supplierIds}`、`srm:portal:enroll/profile/evaluation/quotation`、`expiry_date - today <= 30`（译文写成 `≤ 30 days/30일/30日`） | 按 Stage A 后中文源补齐；`≤` 恢复为 `<=` | DONE |
| F4 | crm | en/ja/ko | 译文仍用旧 `@CrmDataScope` 族与 `GatewayPreAuthFilter`、`init-all.sql`；缺 `@ServiceDataScope` 族、`CrmDataPermissionHandler`、`CrmTenantTablePolicy`、`GatewayPreAuthenticationFilter`、`InternalFeignHeadersFactory`、`ServiceDataScopeContext`、`ServiceIdentityContext`、`ServiceIdentityFilter`、`omni-common-service`（ja/ko 另缺 `auth.sql`、`crm.sql`、`database/changelog/crm/`、`database/seed/manifest.yaml`、`scripts/sql/seed/`）；多余 `crm.md` 链接 | 按中文源 starter 迁移后口径对齐 | DONE |
| F5 | mq-reliability | en/ja/ko | 缺 `/api/internal/mq-message/**`、`MqRelayJobRegistrar`、`X-Internal-Token`、`omni-crm`、api 路径 `/api/internal/mq-message/`；en 菜单名 `Operations Monitor → Message Records` 与前端实际 `Monitoring → MQ Messages` 不符 | 补齐 token；en 菜单名改为实际文案 | DONE |
| F6 | architecture | en/ja/ko | ja 缺 `(username, tenant_id)`、`XxlJobAdminClient`、`XxlJobSpringExecutor`、`resource:action`；ko 另缺 `DataPermissionHandlerImpl`、`spring.cloud.gateway`；en 多余 `@ComponentScan`；三语缺 api 路径 `/api/crm/` | 按中文源对齐 | DONE |
| F7 | backend-patterns | en/ja/ko | 缺 1 表行、`AuthorizationFilter`、`DataScopeTablePolicy`、`TenantTablePolicy`、`omni-common-service`；译文多余 `@MapperScan("com.omni.xxx.mapper")` | 按中文源对齐；WHERE/TODO 注释等本地化为 EXPECTED_LOCALIZATION | DONE |
| F8 | workflow | en/ja/ko | 缺 2 表行、`omni-common-mqlog`、`(businessType, businessKey)` | 按中文源对齐 | DONE |
| F9 | docker-deployment | en/ja/ko | 缺 1 标题；端口/卷/命令陈旧：缺 `localhost:13306`、`localhost:8100`、`OMNI_*_HOST_PORT`、`docker compose up -d --scale omni-base=3`、`migrate`、`omni-db-migrator adopt-current`、`omni-stack-mysql-data`、`http://localhost:8100/api/auth/...`；多余 `localhost:3306`、`localhost:8102`、`omni-mysql-data`、`http://localhost:8102/api/auth/...` | 按中文源 reconcile | DONE |
| F10 | core-flows | en/ja/ko | XSS 菜单名与前端实际不符：en `…XSS Protection Config`→`XSS Protection`；ja `XSS 対策設定`→`XSS 防御`；ko `XSS 방어 구성`→`XSS 보호` | 按前端 locales 实际值修正 | DONE |
| F11 | guide-procurement-flow | en/ja/ko | 缺 `assetManaged=true`、`qualityStatus=PASS`、权限码 `procurement:overview:view` | 按中文源补齐 | DONE |
| F12 | guide-srm-flow | en/ja/ko | 缺 `APPROVED`、`owner_user_id`、`owner_unit_id` | 按中文源补齐 | DONE |
| F13 | guide-asset-flow | en/ja/ko | 缺 `omni-workflow`、`owner_user_id/owner_unit_id`、`qualityStatus=PASS` | 按中文源补齐 | DONE |
| F14 | guide-scheduling | en | 缺 `UserJobHandlerRegistry` | 按中文源补齐 | DONE |
| F15 | guide-crm-flow | en | 缺 `OPEN` | 按中文源补齐 | DONE |
| F16 | guide-permissions | en | 多余 `current_user_id`（中文源无此 token） | 按中文源对齐 | DONE |
| F17 | guide-troubleshooting | en | 缺 `@SystemJobMeta`、`@XxlJob`、`SUPPLIER`、`captchaKey`、`enum`、`options`、`state`、`type`、`zh-CN/en-US/ja-JP/ko-KR` | 按中文源对齐 | DONE |
| F18 | preset-maintenance | en | 缺 `.m2`、`conflicts`、`omni catalog validate`、`version`；schema 路径写成短形式 `module.schema.json`/`preset.schema.json` | 恢复完整路径 `scaffold/schemas/*.schema.json` | DONE |
| F19 | guide-scaffold-development | ja/ko | 缺 `database/seed/manifest.yaml`、`scripts/sql/seed/auth.sql`（写成短形式 `auth.sql`） | 恢复完整路径 | DONE |
| F20 | frontend-patterns | en | 多余 `redirect`（中文源无） | 按中文源对齐 | DONE |
| F21 | srm | ko | 多余链接文字 `종단 상태, 복구 불가`（中文源该处非链接/表述不同） | 按中文源对齐 | DONE |

## 2. EXPECTED_LOCALIZATION（不改，逐项理由）

| 组 | 涉及 | 内容 | 理由 |
| --- | --- | --- | --- |
| E1 | guide-procurement-flow / guide-authentication / guide-permissions / guide-scheduling / guide-crm-flow / guide-srm-flow / guide-asset-flow / guide-quick-start / guide-workflow-approval | 译文引用 `../images/<locale>/*.png` 及 `<name>-<locale>` 锚点，parity 记为"缺 zh-CN 图" | 仓库既有约定：各语种文档引用本语种截图；全部 locale 图片实测存在，`docs:links:check` exit 0 已验证可解析 |
| E2 | api-contract / core-flows / backend-patterns / scheduling | `ElMessage.error("权限不足")`→`"Insufficient permissions"`、`R.fail("服务器内部错误")`→`"Internal server error"`、`throw new BusinessException(404,"xxx不存在")`→目标语、`XxlJobHelper.handleFail("参数解析失败: ...")`→目标语 | 示例性文案（非接口字面量），为目标读者本地化；不改变 code/message 结构契约 |
| E3 | core-flows / backend-patterns / workflow / crm-design | `1=男, 2=女, 0=未知`→`1=Male…`、`WHERE sys_user.id = {当前用户ID}`→`{current user ID}` 族、`max(现有版本)+1`→`max(existing)+1`、`/admin/{最后一段}`→`/admin/{last segment}` | 伪代码/占位符中的说明性词语本地化，结构与语义等价 |
| E4 | backend-patterns | `// TODO: [模块] 描述`→`// TODO: [module] description`、`// FIXME: 描述`→`// FIXME: description` | 示例注释模板本地化；约束（`[module]` 前缀）在目标语中保留 |
| E5 | scheduling 三语 | `cupShape = 大杯`→`Large/大/대`、`喝水提醒`→`Drink Water Reminder/水分補給リマインダー/물 마시기 알림` | 教程演示中用户在本地化 UI 里输入的取值 |
| E6 | observability 三语 | `omni-procurement / 提交请购`→`submit requisition/購買申請の提出/구매 요청 제출` | 调用链 span 名称中操作步骤的本地化展示 |
| E7 | srm-design / asset-design 三语、asset-design ja | `credit_code（统一社会信用代码）`→`(Unified Social Credit Code)` 等 | 全角/半角括号 + 注释语本地化；字段名与枚举值（`DISCARD/SCRAP`、`2026-Q2`）逐字保留 |
| E8 | mq-reliability ja/ko | `運用監視 → メッセージ記録`、`운영 모니터링 → 메시지 기록` | 与前端 ja/ko locales 实际菜单文案一致（实测核对） |
| E9 | api-contract | 链接 `workflow.md`→`workflow.en.md/jp.md/kr.md` | 语种后缀链接约定，文件存在且链接门禁通过 |
| E10 | core-flows ja/ko 等 | `系统管理 → XSS防护配置` 译文中菜单名使用目标语 | 菜单名本地化为既有行为；本轮仅修正与前端 locales 实际值不符的名称（见 F10） |

## 3. INDEPENDENT_REVIEW_REQUIRED（移交独立复核者）

| # | 位置 | 疑问 |
| --- | --- | --- |
| R1 | api-contract/core-flows 回调 URL 端口 | 中文源写 `http://<宿主机IP>:8102/api/auth/oauth2/github/callback`，代码默认 `:8100`（`omni-auth application.yml:108-116`），三语译文写 `:8100`。译文与代码一致，中文源疑似过时；**中文源修订不在本批范围**，需独立复核者裁定后由后续批次改中文源 |
| R2 | api-contract §错误码示例 | `GET /user/list`、`/<resource>`、`/api/<service>`、`/xss-config/...`、`SAMEORIGIN` 等示例 token 中文源与译文取舍不一；本轮按中文源对齐后，自然度与示例选取请独立复核 |
| R3 | 全部本轮补译内容 | 语义自然度、领域表达（审批/报价/数据权限术语）未经独立母语复核；见 §4 签核区 |

## 4. 独立复核包（签核区）

- 逐源修改摘要：§1 表 F1–F21（每项已注明语种与 parity 口径缺漏）；实际差异以各译文文件 git diff 为准。
- 剩余语义疑问：§3 R1–R3。
- 不可翻译 token 清单（补译中保持原样）：API 路径（`/api/base/mq-message/runtime`、`/api/internal/**`、`/api/internal/procurement/rfq/*`、`/api/internal/quotation/batch`、`/api/internal/supplier/batch`、`/api/internal/mq-message/**`、`/api/crm/`）、权限码（`base:mqmessage:list`、`srm:portal:quotation`、`srm:portal:enroll/profile/evaluation/quotation`、`procurement:overview:view`）、类名/过滤器（`InternalApiAuthFilter`、`MqRelayJobRegistrar`、`AuthorizationFilter`、`DataScopeTablePolicy`、`TenantTablePolicy`、`GatewayPreAuthenticationFilter`、`InternalFeignHeadersFactory`、`ServiceIdentityContext/Filter`、`XxlJobAdminClient`、`XxlJobSpringExecutor`、`DataPermissionHandlerImpl`、`UserJobHandlerRegistry`）、枚举与状态（`DRAFT/CLOSED/AWARDED/CANCELLED`、`status IN (INVITED, QUOTED)`、`status=SENT`、`APPROVED`、`OPEN`、`DENY`、`FULL`、`OUTBOX_ONLY`）、协议头与列名（`X-Gateway-Forwarded`、`X-Trace-Id`、`X-Internal-Token`、`owner_user_id`、`owner_unit_id`）、命令与路径（`docker compose up -d --scale omni-base=3`、`omni-db-migrator adopt-current`、`omni-stack-mysql-data`、`OMNI_*_HOST_PORT`、`database/seed/manifest.yaml`、`scripts/sql/seed/auth.sql`、`scaffold/schemas/*.schema.json`、`omni catalog validate`、`qualityStatus=PASS`、`assetManaged=true`）。
- 复核签核位置（由独立复核者填写，本批禁填）：

```
reviewer:
review_date:
per-source results:  # F1..F21 / E1..E10 / R1..R3 逐项 ACCEPT 或 CHANGE_REQUEST
```

## 5. 批次与门禁执行记录

- Batch 1（8 源）：api-contract、guide-authentication、srm、crm、mq-reliability、architecture、backend-patterns、workflow。
- Batch 2（12 源）：docker-deployment、core-flows、guide-procurement-flow、guide-srm-flow、guide-asset-flow、guide-scheduling、guide-crm-flow、guide-permissions、guide-troubleshooting、preset-maintenance、guide-scaffold-development、frontend-patterns。
- 批末统一门禁（parity 复扫、links、sensitive、i18n --allow-draft）与退出码：见 §6。

## 6. 门禁实测

| 门禁 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| parity 复扫 | node scripts/.work/qoder-doc-parity.mjs | 0 | with_findings 74→63、clean 28→39；objective 类（api/perm/rows/heads/fences 缺失）= 0 |
| 链接 | npm run docs:links:check (omni-frontend) | 0 | 通过 |
| 敏感 | node tools/omni-cli/scripts/docs-quality.mjs --scope=sensitive --allow-draft | 0 | 通过 |
| i18n | node tools/omni-cli/scripts/docs-quality.mjs --scope=i18n --allow-draft | 0 | 通过 |

parity 复扫后剩余 63 组有 findings，全部为 EXPECTED_LOCALIZATION（E1 图链/E2-E7 本地化 token/E8 ja·ko 菜单名/E9 语种链接）与 §3 R1 记录的中文源-代码端口冲突（译文与代码一致，不回改中文源）。