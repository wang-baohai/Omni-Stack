# Omni-Stack's actual harness

This page discusses only what can be pointed to and run in this repository. It is not a capability vision, and it does not present an industry concept as implemented here. The test is simple: every statement should trace to a file or a real check.

## Context: fact documents and entry points

Omni-Stack writes current behavior into the repository instead of leaving it in a conversation:

- The System Truth table in root [AGENTS.md](../../AGENTS.md) lists architecture, API contract, backend/frontend patterns, core flows and module documents; Hard Constraints and the Completion Checklist define execution boundaries.
- [Architecture and engineering facts](../architecture.en.md), the [API contract](../api-contract.en.md) and module documents are the maintainer baseline.
- The [source entry index](../reference/source-map.en.md) locates classes and methods instead of blind directory searches.
- The [documentation manifest](../docs-manifest.yaml) records each document's source, translations, review evidence and status.

These files answer "what is true now," not "what was true once." Historical acceptance records cannot replace verification of the current step.

## Tools: actions that can run

| Action | Entry point | Result |
|---|---|---|
| Backend build | `omni-backend/mvnw` (JDK 25 in the container) | Compile, test, install |
| Frontend build and checks | `omni-frontend` `npm run build`, `npm run lint` | Types, build, ESLint |
| Generate services and CRUD | `tools/omni-cli` | New module skeleton or trimmed project |
| Preset structure validation | CLI preset generation and structure assertions | Module, Compose, gateway and seed matrices agree |
| Documentation checks | `tools/omni-cli` docs scripts | Links, README, i18n content, sensitive data, screenshot coverage |
| Site build | `docs` `npm test`, `npm run build` | Documentation tests and VitePress build |

More automation is not always better. A successful documentation build means pages were generated, not that translations were reviewed. The exit code and the range it covers must be recorded separately.

## Constraints: hard rules in the repository

These come from `AGENTS.md` and the fact documents; they are current constraints, not suggestions:

- Controllers return `R<T>`; pagination returns `R<PageResult<T>>`.
- No `@Autowired` field injection; services use an interface plus implementation.
- Write operations declare `resource:action` permission codes, synchronized with permission seeds and frontend directives.
- Gateway 5.x uses the `spring.cloud.gateway.server.webflux` prefix.
- WebFlux services depend only on the reactive Redis module.
- The data-permission interceptor comes before the pagination interceptor; `DataScopeContext` must be cleared in `finally`.
- Four-language, tenant, permission and module boundaries follow each module's documentation.

Constraints matter only when checked. The repository connects them to verification through tests, structure assertions, manifest checks and review rather than documentation alone.

## Verification: layers, not a single point

The repository currently has at least these layers:

1. **Unit and structure**: backend module tests, frontend navigation tests, preset residue and matrix assertions.
2. **Build**: Maven reactor, frontend `vue-tsc` + Vite, documentation site build.
3. **Documentation truth**: `docs/scripts/source-excerpts.test.mjs` compares local excerpts in principle pages with repository files; altered local excerpts or missing files fail. Upstream excerpts are checked for a version-provenance marker, which does not prove their contents were compared with upstream source.
4. **Content quality**: links, README responsibilities, i18n content, sensitive data and screenshot coverage are checked by `docs-quality.mjs` scopes.
5. **Runtime and delivery**: service health, real login and page actions in local Docker, plus generated-project build and trimming.

Each layer proves only its own range. Unit tests do not prove real login works; a site build does not prove translations were reviewed; correct generated structure does not prove the generated project starts independently.

## Feedback and checkpoints: make invalidation visible

Three mechanisms turn change into visible state:

- **Source-digest refresh**: `refresh-source-digests.mjs` detects SHA-256 drift in Chinese fact sources, sets affected translations back to `present-unverified`, and removes the old reviewed digest. Old sign-offs cannot keep pretending to be valid.
- **Documentation manifest**: `docs/docs-manifest.yaml` records translation status, review evidence and release policy; `docs-review-queue.mjs` generates the review queue.
- **Source-excerpt checks**: Java/TypeScript excerpts in articles must match current source, preventing "the article remains, the code has moved."

These mechanisms cover only what they can read. Real page screenshots, external authorization flows and runtime behavior still need separate evidence; when there is none, record it in the unfinished list instead of substituting document status.

## AI assets: canonical sources and derived entry points

[ai/README.md](../../ai/README.en.md) states that `ai/` is the canonical source while root `AGENTS.md` and engineering facts still define constraints. `ai/skills`, `ai/prompts`, `ai/knowledge` and `ai/adapters` carry skills, prompt templates, sourced project experience and tool discovery entries. Adapters protect user modifications and refuse to overwrite differences.

AI assets are therefore part of the harness: they have sources, versions, licenses and invalidation conditions rather than being scattered prompts. The repository contains no personal accounts, tokens or memory, and provides no automatic web-research platform.

## What is not implemented

- No persistent autonomous agent runtime, and no channel for a model to auto-commit, publish or operate production.
- No automatic external-research platform; external sources still require a human to open, date and judge them.
- No mechanism that equates a successful build with completed functional verification; the two are recorded separately.
- No screenshot or real external-authorization evidence for every page; gaps remain in the unfinished list.

Stating boundaries is what makes a harness maintainable. Next: [fact maintenance and invalidation](harness-fact-maintenance.en.md), or walk through the [secondary-development task](harness-secondary-development.en.md).
