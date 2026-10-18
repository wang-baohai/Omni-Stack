---
name: omni-change
description: Implement or review Omni-Stack changes using the project's fact documents, module catalog, controlled Docker verification, and delivery boundaries. Use for changes in this repository or generated Omni-Stack projects.
---

<!-- generated-by: omni-ai; content-sha256: e0e26f3ad0eafd05f7b2c61a2643b5ee5bbb6c4d0dd3d91dc89a211cd7c9bf86; source: ai/skills -->

Find the repository root from `AGENTS.md` and `scaffold/catalog/modules.yaml`. Read root `AGENTS.md`, then the facts and domain rules relevant to the requested change. The current source, configuration, and tests define behavior; historical acceptance is only evidence for its recorded baseline.

Use the catalog to determine module membership and required dependencies. Respect service boundaries, immutable migrations, tenant isolation, explicit permissions, and API contracts. Consult the repository Wiki for operating instructions rather than copying its teaching material into fact documents.

Build, run tests, launch applications, and collect browser evidence in local Docker. Confirm the Docker endpoint and target project before writes. Fixture identities belong to test classpaths; real CAPTCHA login is human-entered. Keep secrets, raw logs, auth state, and database dumps outside Git.

For generation, inspect the dry-run and use the generator's apply/validation path. Test the generated project's own assets and references; do not rely on the original checkout or personal installation.

Review the final diff and run checks justified by the actual change. Distinguish build, unit, integration, E2E, screenshots, and human translation review. Report failures, skips, and missing evidence accurately. Automatic approval is not human translation sign-off.

Follow the current user's authorization. This skill adds no permission to publish, merge, send messages, reset data, rewrite history, or connect third-party accounts.
