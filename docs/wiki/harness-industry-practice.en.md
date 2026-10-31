# Industry case: initialization, progress and commits for long-running agents

This page discusses one industry source that was actually opened and dated, to show what a harness looks like in engineering practice. It is not an industry survey and cannot prove that one practice beats another. Read every conclusion as "this organization did this," not "the industry does this."

## Source information

| Item | Content |
|---|---|
| Organization | Anthropic |
| Type | Vendor engineering blog (first-hand for its own practice, not an independent evaluation) |
| Title | Effective harnesses for long-running agents |
| Published | 2025-11-26 |
| Accessed | 2026-10-20 |
| URL | <https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents> |

## The problem it describes

The post groups common failures of long-running agents into two: trying to do too much at once, and declaring the job done before it is. That helps explain why a longer prompt is not enough: the problem is not single-response quality but state, progress and acceptance across sessions.

## The practices it describes

The described flow can be split into four observable actions:

1. **Initializer agent**: prepare `init.sh`, a progress file and an initial commit so later sessions have a stable starting point.
2. **Incremental progress**: later sessions are asked to advance in small steps and leave clear artifacts for the next one.
3. **Feature list**: record features in a JSON list and update status only through a `passes` field.
4. **Commits and summaries**: require the agent to commit progress and write summaries so history is traceable.

The common idea is to move "where we are, what remains, how to continue" out of the conversation and into verifiable objects in the repository. This matches the feedback/checkpoint part of a harness, but the file names and exact flow belong to that organization and should not be transplanted unchanged into every project.

## Limits it acknowledges

The post explicitly notes remaining tool limits, such as vision and browser automation. That acknowledgement matters: a harness can organize a process but cannot invent tool capability. When a real page or authorization flow is outside tool coverage, mark it unverified rather than replacing evidence with "the agent finished."

## Relationship to this project

Omni-Stack shares the goal but implements it differently:

| Dimension | The case | This project today |
|---|---|---|
| Progress carrier | Progress file, feature list | Manifest status, evidence files, unfinished list (process records are not in the product repository) |
| Constraint carrier | Prompts and repository artifacts | `AGENTS.md`, architecture/API/pattern documents, hard constraints |
| Verification carrier | Commits, summaries, tool execution | Builds, tests, quality gates, real Docker runs |
| Fact invalidation | Not expanded | Source-digest refresh invalidates translations; source-excerpt tests compare originals |

The "this project" column describes only current, locatable implementation. Similarity helps explain the concept; it does not claim equivalence.

## How to cite this source

- You may say: "In a 2025-11-26 engineering post, Anthropic described an init script, a progress file, a feature list and commit summaries."
- Do not say: "The industry generally uses a four-step flow," "this is the latest best practice," or "adopting it improves efficiency by X."
- If quoting a short passage, include the link, publication date and access date, and state what it supports and does not support.

## Pending verification

This writing environment opened only this one independent-organization source; two other searches and one other vendor fetch were blocked. This page therefore does not compare vendors or summarize a trend. Once a network-authorized environment supplies a second and third independent source, the page can become a multi-case comparison.

Continue with [this project's actual harness](harness-omni-stack-practice.en.md) or [fact maintenance and invalidation](harness-fact-maintenance.en.md).
