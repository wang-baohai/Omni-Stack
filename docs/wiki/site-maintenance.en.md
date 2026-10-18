# Independent documentation site and four-language maintenance

Scope: repository Markdown and the site; no business service is required. `docs/docs-manifest.yaml` registers canonical content. `docs/.vitepress/source` is a generated projection: do not edit or commit it.

## 1. Build, start, stop

Run at repository root with local Docker available and port 4173 free:

```bash
docker compose -p omni-stack-docs -f compose.docs.yaml up -d --build
docker compose -p omni-stack-docs -f compose.docs.yaml ps
docker compose -p omni-stack-docs -f compose.docs.yaml logs wiki
```

Open `http://localhost:4173`, select a language, and search a project term. All four languages have content and navigation. Missing language files fail the build; there is no Chinese fallback. Stop with:

```bash
docker compose -p omni-stack-docs -f compose.docs.yaml down
```

The `omni-stack-docs` project runs one Nginx container at fixed `127.0.0.1:4173` and has no business data volume. Resolve port conflicts explicitly. Node builds the image; runtime serves static files only.

## 2. One content source, two reading entry points

Read `docs/wiki/index.en.md` and relative links directly on GitHub/Gitee. The same Markdown generates `/zh-CN/`, `/en-US/`, `/ja-JP/`, and `/ko-KR/`; generated directories and caches are ignored.

Relative source and rule links become hosted repository links in the site. GitHub is the default; set `DOCS_REPOSITORY_URL=https://gitee.com/wang-baohai/Omni-Stack` at build time for Gitee. Generated projects should use their own repository URL. Historical acceptance keeps its applicable commit and does not describe current behavior automatically.

Images in `docs/images` are copied into the site. Explanations and code remain repository-readable. The site bundles Mermaid at build time and renders diagram source as SVG in the browser, updating it after page or light/dark theme changes; source remains visible if rendering fails. Large diagrams scroll within their own area without an external rendering service.

## 3. Changes and quality gates

1. Update the Chinese source and all language content, navigation, and captions. Capture textual UI screenshots in each actual language.
2. Register new pages and refresh digests after updates. Changes to the source or translation invalidate previous review records.
3. Check content completeness, links, screenshots, secrets, derived preset docs, and the site build. Content checks are separate from review acceptance.
4. Complete an actual review under the manifest policy and record `reviewer`, `reviewed_at`, `review_type`, `reviewed_source_sha256`, and `translation_sha256`. Human reviewers use `HUMAN_REVIEW`. Under [ADR-0003](../adr/0003-authorized-ai-translation-review.md), the owner has authorized Codex to use `AI_REVIEW`; it also requires the per-document review evidence path and digest before setting `synchronized`.
5. Regenerate the review queue. Strict `docs:i18n:check` remains the release gate; draft flags do not satisfy it.

AI review must examine complete meaning, technical literals, security and failure conditions, target language, images, and links for each document. It is recorded as AI rather than human review. Translation generation, platform approval, and content checks alone do not issue sign-off. Pending pages display a notice. Version, date, and evidence must reflect actual execution.

Collect business UI evidence with Playwright in local Docker. Select `E2E_RUNTIME_MODE=external`, connect to the verified Docker frontend, and explicitly set `E2E_BASE_URL`, `E2E_TENANT_ID`, a unique `E2E_EXECUTION`, `E2E_REGISTRY_PATH`, and an `E2E_ARTIFACT_DIR` outside the repository. Set `E2E_DOCS_IMAGE_DIR` to an external directory for images awaiting review. Add only necessary images to formal assets after visual review, masking, and provenance registration. A human enters real login CAPTCHAs; controlled test-classpath fixtures issue short-lived tokens for test identities.

`E2E_RUNTIME_MODE=display-fixtures` is for UI demonstrations: it substitutes CAPTCHA, tenant, and device-code responses and fixes the clock. Label these results as simulated displays; they cannot prove real authentication or business flows. Formal `external` mode uses real interfaces and time, including the real countdown. Label any deliberate fault injection and its test scope separately. `npm run docs:screenshots:test` checks the boundary between modes and also runs in the Docker frontend tooling container.

## 4. Static delivery

The build stage of `docker/docs/Dockerfile` writes `/workspace/docs/.vitepress/dist`. The runtime image contains only static output. Export it from a built container to another web server. Set `DOCS_BASE` at build time for subpath hosting and verify navigation, search, and images.

Business images retain the root `.dockerignore` and exclude docs and ai. The documentation image uses its own Dockerfile ignore rules. Strict language and screenshot gates are required before release. Local preview does not publish to GitHub Pages, Gitee, or production.

## 5. Troubleshooting

| Symptom | Check |
|---|---|
| Missing page or language | Source and translation paths in the manifest; registration of new pages |
| Broken site link | Repository relative path and rebuilt projection |
| Empty search | Active language and page inclusion in manifest/index |
| Missing image | Original path, build context, screenshot manifest, actual locale |
| Review gate failure | Reviewer, date, source and translation digests; AI review also requires authorization and evidence digests, with actual renewed review after change |
| Stale build | Rebuild and compare source digests and image identity |
