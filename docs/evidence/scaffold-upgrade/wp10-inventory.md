# WP-10 清理 Inventory（2026-09-01，HEAD efd651e）

> 只读清点与分类。所有删除动作必须等 G1～G7 关闭并完成替代物确认与引用扫描后，按批次执行。
> 引用扫描基线：对保留范围（排除 evidence/plan/handoff/patch 与候选目录自身）执行全仓 grep，无运行时引用即标记 ✅。

## A. 敏感环境备份（绝不提交；人工确认后移出仓库或删除）

| 路径 | 状态 | 处置 |
| --- | --- | --- |
| `.env.before-rebuild-20260828` | untracked，**未被 .gitignore 覆盖（误提交风险）** | 内容未读取；确认无用后安全移出仓库。G8 前临时保障：不 add、不提交 |

## B. 临时补丁（已应用，内容被仓库现状替代）✅ 无引用

| 路径 | 替代物 |
| --- | --- |
| `en-US.patch` / `ja-JP.patch` / `ko-KR.patch` | locale 文件已包含全部翻译（批次 2/3 补齐 supplierPortal、operLog、procurementApprovalRules 等） |
| `portal-en-US.patch` / `portal-ja-JP.patch` / `portal-ko-KR.patch` | 同上（supplierPortal/portal 命名空间已直接入库） |

## C. 测试产物（调试输出）

| 路径 | 说明 |
| --- | --- |
| `scripts/api-output.json`、`scripts/api-raw.json`、`scripts/api-result.json`、`scripts/api-raw-bytes.bin` | API 调试输出 ✅ 无引用 |
| `scripts/temp_procurement_bpmn.txt` | 临时 BPMN 文本 ✅ 无引用 |
| `omni-frontend/.artifacts/**` | 文档工具产物目录（untracked，未 ignore）✅ 无引用 |
| `omni-frontend/test-results/**` | 已被 `.gitignore:45` 覆盖，无需动作 |

## D. 一次性修复/调试脚本（场景已失效或被测试/CLI 替代）✅ 无运行引用

- 审批实例修复类：`fix-approval-comments.sql`、`fix-comment-charset.sql`、`fix-fullmsg.sql`、`fix-bpmn.ps1`、`fix-inst4.ps1`、`cleanup-inst4.ps1`、`cleanup-inst4.sql`、`verify-retry-fix.ps1`
- 实例调试类：`check-inst3.py`、`start-inst3.ps1`、`start-instances.ps1`、`verify-instances.ps1`、`verify-v2.ps1`、`verify-v3.ps1`、`scripts/sql/_check_wf.sql`
- API 调试类：`check-all.py`、`check-hex.py`、`check-raw.py`、`test-api.py`、`verify-fullmsg.py`、`approve-all.ps1`、`curl-api.ps1`、`curl-test.ps1`、`raw-json.ps1`
- 启动辅助（被 `docker compose` 与 omni-cli `dev up` 替代）：`deploy-and-start.ps1`、`start-only.ps1`
- BPMN 残留副本：`scripts/bpmn/leave-approval.bpmn20.xml`、`scripts/bpmn/procurement-approval.bpmn20.xml`（运行正本在 `omni-workflow/src/main/resources/bpmn/`，由 `ProcessDefinitionDeployer` 加载 classpath 资源）

## E. 被 Liquibase/种子替代的 SQL（G8 最终清理对象）

| 路径 | 替代物 |
| --- | --- |
| `init-all.sql`、`sp_init_tenant.sql` | `database/changelog/` + migrator |
| `migrate-asset-mvp.sql`、`migrate-crm-mvp.sql`、`migrate-procurement-mvp.sql`、`migrate-srm-mvp.sql`、`migrate-supplier-workflow.sql`、`migrate-workflow-process-start-idempotency.sql`、`migration-mqmessage.sql` | 对应模块顺序化 changeSet |
| `init-tenant-a.sql` | preset-aware 租户 fixture |
| `crm-sample-data.sql`、`procurement-sample-data.sql` | E2E fixture / demo profile |
| `seed-test-data.sql` | 隔离测试 fixture（严禁入生产 seed） |
| `init-nacos.sql`、`init-xxl-job.sql` | `scripts/sql/seed/nacos.sql`、`seed/xxl-job.sql`（migrator 应用）；README 已标注兼容期遗留。**待人工确认**容器初始化无未迁移路径后删除 |

## F. 正式保留

- 根目录：`start.bat`/`stop.bat`/`start.sh`/`stop.sh`、`compose*.yaml`、`.env.example`
- `scripts/sql/seed/*`（8 个幂等种子）+ `database/seed/manifest.yaml`
- `tools/omni-cli/**`、`docs/evidence/**`、`.github/workflows/quality.yml`

## G. 待人工确认

| 路径 | 建议 |
| --- | --- |
| `agent-progress.md` | 历史交接记录；有效结论已沉淀至 evidence/manifest，G8 时删除 |
| `docs/scaffold-upgrade-task-handoff-2026-08-27.md` | 历史证据文档；建议移动至 `docs/evidence/scaffold-upgrade/` 并更新引用 |

## 引用扫描结论

- 指导性引用（README/quick-start/crm 文档指导修改 init-all.sql 等）已于批次 4 清零；
- 剩余引用仅为：README 目录树对 init-nacos/init-xxl-job 的「兼容期遗留」标注（删除时同步移除）、evidence/inventory 文档自身、`docs/preset-dependency-matrix` 等 manifest 管理文档的预置表（生成器维护）；
- 无任何代码、compose、CLI、测试运行时引用指向 D/E 类候选。

## 批次执行建议（G1～G7 全部关闭后）

1. 批次 A：删除 C+D（测试产物与一次性脚本），跑悬空引用扫描；
2. 批次 B：删除 E 类 SQL + 移除 README 三行遗留标注，复扫 `.sql` 分布（只允许 `scripts/sql/seed`）；
3. 批次 C：处理 A（敏感备份移出）+ G（人工确认项）+ B（6 个 patch），输出 cleanup-report。
