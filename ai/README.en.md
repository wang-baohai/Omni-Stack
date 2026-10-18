# Shared AI engineering assets

`ai/` is the canonical source. Root [AGENTS](../AGENTS.md) and [engineering facts](../docs/architecture.en.md) still define constraints and current behavior. The [manifest](manifest.yaml) records origin, version, license, dependencies, and tools.

## Contents

- `skills/`: existing grill-me and the project change workflow omni-change.
- `prompts/`: change review and documentation synchronization templates.
- `knowledge/`: project lessons with origin, date, and invalidation conditions; not another contract.
- `adapters/`: native discovery entries for Codex/Qoder, with protection against overwriting user edits.

No project-maintained plugin source was found. External tools list official sources without copying installed caches. On 2026-10-12, read-only Git checks in Docker confirmed grill-me has the same complete-file SHA-256 as the [pinned upstream commit](https://github.com/mattpocock/skills/blob/694fa30311e02c2639942308513555e61ee84a6f/skills/productivity/grill-me/SKILL.md); its [MIT license](skills/grill-me/LICENSE) is included. The historical import process is not independently proven. Personal global plugins, accounts, and memory are outside this directory.

## Install and check

Build container tools and install CLI dependencies at repository root:

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --check
```

Preview does not write. `--apply` derives `.agents/skills/<name>/SKILL.md` or `.qoder/skills/<name>/SKILL.md` and protects local edits. Reinstall after canonical changes; do not edit generated entries. Reloading the workspace or a new turn may be required for tool discovery. Directory checks alone do not prove actual tool loading.

The [native discovery verification dated 2026-10-18](../docs/reference/evidence/qoder-native-skill-discovery-2026-10-18.json) used official Qoder CLI (npm compatibility distribution) `1.1.73` in isolated Docker containers. It ran `qodercli skills list` in all five generated and moved presets. Both `grill-me` and `omni-change` were `Enabled`; names and descriptions matched the canonical source, and locations pointed to each project’s derived entries. Every preset used a fresh, separate configuration with networking disabled. No account or global state was copied and no model request was sent. This verifies CLI project Skill discovery, not the IDE UI or Skill task execution.

## Maintenance and presets

Edit canonical assets, update manifest versions, and synchronize adapters. Generated presets contain applicable AI assets and install from their own adapter, without original checkout or personal absolute paths.

Shared configuration contains public templates only. Never commit tokens, cookies, OAuth keys, private addresses, auth state, logs, or personal memory. Automation identities and quota monitoring belong to the chat, not shared assets.

See the [Wiki maintenance guide](../docs/wiki/site-maintenance.en.md) for four-language tutorials and the currently authorized review policy. Human review uses `HUMAN_REVIEW`; Codex review explicitly authorized by this project's owner uses `AI_REVIEW`, bound to the content and per-document review evidence. Current user authorization and platform rules govern actions; a Skill grants no extra external write permission.
