# Improvement directions: benefit, cost and verification first

Everything here is a suggestion, not a current capability. Each item states the problem, benefit, cost, verification method and current status. A suggestion without a verification method does not belong in the list; it is only a wish.

## Suggestion 1: add multi-organization industry cases

**Problem**: the industry-practice page has only one verifiable source (Anthropic, 2025-11-26), so it cannot compare trade-offs across organizations.

**Benefit**: distinguish "one vendor's implementation" from "a pattern multiple implementations converge on," reducing the chance of treating one experience as a rule.

**Cost**: network authorization, time and human reading; every source needs publication date, access date and supported/unsupported conclusions.

**Verification**: at least two first-hand sources from different independent organizations; every external claim traces to a specific source; the article no longer uses phrases such as "industry consensus."

**Status**: not done. External retrieval was blocked in this environment and recorded as pending verification; no substitute material was invented.

## Suggestion 2: broaden source-excerpt coverage

**Problem**: the `omni:source` mechanism covers Java/TypeScript excerpts in principle pages, but not every article with code uses the marker.

**Benefit**: reduce articles that still quote old code, especially after refactors.

**Cost**: each excerpt needs a unique source; upstream excerpts need version records; maintenance grows with coverage.

**Verification**: run `docs/scripts/source-excerpts.test.mjs` on newly covered pages; a one-line alteration must fail; missing source, missing language and missing upstream version must also fail.

**Status**: the mechanism exists; coverage is limited. Add it chapter by chapter rather than moving everything at once.

## Suggestion 3: prioritize the review queue by change impact

**Problem**: a source change can put many translations into review at once, and the queue does not distinguish a link fix from a changed behavioral conclusion.

**Benefit**: reviewers handle high-impact entries first, reducing waiting time.

**Cost**: impact levels must be defined, and the levels must not become an excuse to skip review.

**Verification**: construct two changes (link correction, conclusion change) and confirm the levels; low-impact entries remain pending and are never auto-signed.

**Status**: not implemented. The current queue lists by status and does not infer impact.

## Suggestion 4: make the checkpoint template reusable

**Problem**: when a long task interrupts, different executors leave different records, raising recovery cost.

**Benefit**: recovery quickly finds the current step, failure evidence, unfinished objects and next action.

**Cost**: the template must stay short or filling it becomes a burden; it also must not be treated as proof of completion.

**Verification**: run a real interruption-recovery drill: using only the template and repository, can someone continue without asking the original author? After recovery, check whether completed actions were repeated.

**Status**: the repository has `ai/prompts` and workflow assets, but a unified checkpoint template is not a product capability. Trial it in `ai/prompts` first; do not change execution rules directly.

## Suggestion 5: define a minimal measurement for the harness

**Problem**: without measurement, "adding a check helped" is only a feeling.

**Benefit**: compare rework, failure-detection time and recovery cost before and after a change.

**Cost**: measurement easily becomes a numbers game; small samples are unstable; collection needs extra records.

**Verification**: define three manually collected metrics (for example, rework count before first verification pass, steps from failure to detection, actions needed to recover after interruption), record several tasks, then decide whether to automate.

**Status**: not implemented, and there is no trustworthy baseline. No "percentage improvement" should be written now.

## What not to do

- Do not let an agent directly commit, publish or operate production just to be "more automatic"; permission and responsibility boundaries come first.
- Do not replace missing tools or verification with more prompt text; a prompt cannot produce real runtime evidence.
- Do not mark translations synchronized in bulk to turn the queue green; that breaks the link between facts and review.
- Do not present one vendor source as an industry standard; the number and independence of sources must match the strength of the conclusion.

## Order

If you can do only one thing, choose suggestion 4: a checkpoint template is cheapest and directly reduces repeated work. Suggestion 1 is constrained by external conditions; suggestions 2 and 3 need maintenance investment; suggestion 5 comes last — without a stable process, measurement records only noise.

Continue with [what a harness is](harness-what-and-why.en.md) or [the secondary-development task](harness-secondary-development.en.md).
