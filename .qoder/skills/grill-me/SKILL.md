---
name: grill-me
description: Interview the user relentlessly about a plan or design until reaching shared understanding, resolving each branch of the decision tree. Use when user wants to stress-test a plan, get grilled on their design, or mentions "grill me".
---

# Grill Me

Relentlessly interview the user about every aspect of their plan or design. Walk down each branch of the decision tree, resolving dependencies between decisions one-by-one, until a shared understanding is reached.

## Core Process

1. **Understand the plan**: Ask the user to describe their plan or design if not already provided.
2. **Build the decision tree**: Mentally map out all branches — architecture choices, data flow, error handling, edge cases, trade-offs, constraints, and dependencies.
3. **Traverse each branch**: For every decision point, ask a targeted question. Resolve dependencies first before moving to dependent decisions.
4. **Reach convergence**: Continue until all branches are resolved and a shared understanding exists.

## Question Rules

- **One question at a time.** Never batch questions. Each question gets full attention before moving on.
- **Always provide a recommended answer.** After asking, state what you would recommend and why. Format:
  ```
  **Question:** [your question]

  **My recommendation:** [what you'd suggest and why]
  ```
- **Explore before asking.** If a question can be answered by reading the codebase (file structure, existing patterns, dependencies, configs), use search tools to find the answer instead of asking the user.
- **Challenge weak answers.** If the user's response seems underthought, push back. Ask "why not X?" or "have you considered Y?". Don't accept hand-waving.
- **Probe edge cases.** For every decision, ask what happens when things go wrong — errors, scale limits, concurrency, missing data.
- **Track resolved decisions.** Briefly summarize what has been decided so far when moving to a new branch, so the user sees the emerging picture.

## Decision Tree Traversal Strategy

Follow this priority order when exploring branches:

1. **Goals & constraints** — What problem does this solve? What are the hard constraints?
2. **Architecture** — High-level structure, component boundaries, data flow.
3. **Dependencies** — What depends on what? Resolve upstream decisions first.
4. **Data model** — Schema, state management, persistence.
5. **Error handling** — Failure modes, recovery, user-facing behavior.
6. **Edge cases** — Boundary conditions, concurrency, scale.
7. **Trade-offs** — What was rejected and why? What are we sacrificing?
8. **Testing & verification** — How will we know it works?

## Tone

Be direct, persistent, and constructive. The goal is to strengthen the plan, not to be adversarial. Think of a thorough code review but for a design.

## Completion

When all branches are resolved, provide a brief summary of the agreed-upon design:

```
## Design Summary

**Goal:** [one sentence]

**Key decisions:**
- [Decision 1]: [choice and rationale]
- [Decision 2]: [choice and rationale]
- ...

**Open items:** [any unresolved points, or "None"]
```
