# 共享 AI 工程资产

`ai/` 是规范源，根 [AGENTS](../AGENTS.md) 和 [工程事实](../docs/architecture.md) 仍定义执行约束与当前行为。[清单](manifest.yaml)登记来源、版本、许可、依赖和适用工具。

## 内容

- `skills/`：现有 grill-me 与项目变更工作流 omni-change。
- `prompts/`：变更审阅、文档同步模板。
- `knowledge/`：有来源、日期和失效条件的项目经验，不是第二套契约。
- `adapters/`：为 Codex 和 Qoder 生成原生发现入口，检测用户修改并拒绝覆盖。

项目未发现自维护插件源码；外部工具只登记官方来源，不复制安装缓存。grill-me 在2026-10-12经容器只读 Git 核对，与[固定上游提交](https://github.com/mattpocock/skills/blob/694fa30311e02c2639942308513555e61ee84a6f/skills/productivity/grill-me/SKILL.md)全文 SHA-256 一致，并保留其 [MIT 许可](skills/grill-me/LICENSE)；历史导入过程未独立证实。个人全局插件、账号和记忆不在本目录中。

## 安装与核对

在仓库根目录构建容器工具并安装 CLI 依赖：

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --check
```

预览不写入；`--apply` 才派生 `.agents/skills/<name>/SKILL.md` 或 `.qoder/skills/<name>/SKILL.md`。工具会保护已修改的入口。规范源变化后重新安装并检查；不要编辑派生文件。Codex/Qoder 加载新入口可能需要重新打开工作区或新轮次，目录检查不能代替工具实际加载验证。

2026-10-18 的[原生发现验证](../docs/reference/evidence/qoder-native-skill-discovery-2026-10-18.json)在独立 Docker 容器中使用官方 Qoder CLI（npm 兼容发行版）`1.1.73`，对五种已生成并移动的预设运行 `qodercli skills list`。`grill-me`、`omni-change` 都为 `Enabled`；名称和说明匹配规范源，位置指向各自项目的派生入口。每个预设使用全新独立配置且断网，未复制账号或全局状态，也未发起模型请求。本验证覆盖 CLI 项目 Skill 发现，不包含 IDE 界面或 Skill 任务执行。

## 维护与裁剪

只修改规范源，并同步 manifest 版本和派生入口。预设生成物带走适用 AI 资产，原生入口由生成物自己的 adapter 创建；它不能依赖原仓库或个人绝对路径。

共享配置只包含公开模板。token、Cookie、OAuth 密钥、真实地址、认证状态、运行日志和个人记忆禁止入库。自动化身份与额度监控属于当前对话，不写入共享资产。

四语言教程和当前授权的复核规则见 [Wiki 维护指南](../docs/wiki/site-maintenance.md)。真人记录 `HUMAN_REVIEW`；本项目经所有者明确授权的 Codex 审校记录 `AI_REVIEW`，须绑定正文与逐项审校证据。操作权限以当前用户与平台规则为准，Skill 不授予额外外部写入权限。
