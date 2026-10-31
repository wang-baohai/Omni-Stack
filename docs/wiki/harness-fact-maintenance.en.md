# Fact maintenance and invalidation: keeping documents with the code

"Facts" in a harness are not decorative prose; they are current information that both execution and verification depend on. Once a fact is stale, a model generates code from an old rule and checks pass on a wrong premise. This page describes how Omni-Stack records, refreshes and invalidates facts today, and what still needs human judgement.

## A fact needs at least four things

Take "purchase requisitions use `proc_*` tables and tenants are handled by an interceptor." A maintainable record should include:

1. **Scope**: which module, tables and identities it applies to.
2. **Current behavior**: what the code actually does, not what it should do.
3. **Evidence entry**: source, migration, test or run record.
4. **Invalidation condition**: what change makes it false, such as a table name, interceptor range or permission-code change.

Without any one of these, readers can only guess. The invalidation condition is especially important: it tells the next change who must update the fact.

## Three carriers of facts

| Carrier | Good for | Maintenance |
|---|---|---|
| Fact documents | Architecture boundaries, API contracts, module rules | Synchronize with code changes; run applicable quality gates |
| Documentation manifest | Source, translations, review status, evidence | Source change triggers digest refresh and pending review |
| Tests and checks | Constraints that can be judged automatically | Failure is feedback, not memory |

The three cannot replace one another. Good prose does not make manifest status true; a `synchronized` status does not prove the text still matches source; passing tests do not prove every fact is covered.

## What happens after a source change

`refresh-source-digests.mjs` computes the SHA-256 of a Chinese fact source and compares it with the value in `docs/docs-manifest.yaml`. On drift it:

1. updates the source digest;
2. sets affected translations back to `present-unverified`;
3. removes the old reviewed source digest while preserving historical review records;
4. lets `docs-review-queue.mjs` regenerate the review queue.

This means editing Chinese text cannot silently reuse an old translation sign-off. A real example: during this change, edits to the README and `docs/wiki/index.md` triggered two source-digest drifts; after refresh, the three introduction-chapter translations joined other affected entries in the review queue, which at that moment showed 9 pending, 129 complete and 3 Chinese-only (later chapters then changed the pending count again). Nothing was flipped directly to `synchronized`.

## Why source excerpts are checked

Principle articles often quote Java or TypeScript. If code is only copied into Markdown, a source change leaves the article unaware. `docs/scripts/source-excerpts.test.mjs` recursively finds principle pages with `omni:source` markers and compares local excerpts with the actual repository files. For upstream excerpts, it checks a `repository@commit` provenance marker. That marker check does not download or compare upstream source; its contents still need a separate review.

The test fails when:

- an excerpt is altered by one line;
- the source file is missing;
- an applicable language version is missing;
- an upstream excerpt has no traceable version.

Failure is not "the documentation test is too strict." It means the article is presenting old code as current. Fix the excerpt and recheck the conclusion rather than deleting the marker or loosening the comparison.

## When old evidence expires

The following invalidates old evidence even if the files remain:

- Article or translation text changes: the old review no longer covers the new wording.
- Source changes: quoted excerpts, behavior descriptions and screenshots may become inaccurate.
- Configuration or port changes: startup steps no longer match.
- Dependency or version upgrades: build commands and expected results may change.
- Environment changes: an old run record cannot prove the current environment works.

So reusing evidence starts with comparing input digests, not checking how recent the date is. An old acceptance record can say what happened then; it cannot replace verification of the current step.

## The maintenance loop

A complete fact-maintenance pass includes:

1. change code or text;
2. update affected fact documents;
3. refresh source digests and identify invalidated translations;
4. run link, content, build and affected runtime checks;
5. record results and unfinished items;
6. keep designated review pending when required, without fabricating sign-off.

If step 3 invalidates many translations, do not mark them synchronized in bulk to "clean the queue." The queue exists to keep unfinished review visible.

## What still needs human judgement

Automated checks find digest mismatches, altered excerpts and dead links, but cannot decide:

- whether new behavior matches business intent;
- whether a translation is natural and consistent;
- whether a screenshot still explains the current page;
- whether a suggestion is worth implementing.

These require a person, or a reviewer explicitly authorized to do so, with real identity and evidence recorded in the manifest. Tools cannot sign on their behalf.

Next: [improvement directions and boundaries](harness-improvement.en.md).
