# What a harness is and why you need one

Start with an ordinary task: add a "budget account" field to a purchase requisition and ensure that only people with procurement permission can modify it. A model can write the entity field, API parameter and page control, but by default it does not know this project's table prefix, tenant interception rules, permission-code format, migration location, or which checks to run afterwards. The system that organizes these engineering conditions outside the model so that it can complete the task continuously and verifiably is what this chapter calls a harness.

## What the model alone lacks

A model is good at generating code from given context, but a real task also needs five kinds of information:

| Gap | How it shows up | What happens without it |
|---|---|---|
| Context | Table structure, module boundaries, existing APIs, error codes | Plausible code that does not connect |
| Constraints | `R<T>`, permission codes on writes, no hand-written tenant fields | Code compiles but violates project rules |
| Tools | Build, tests, generators, database migrations, browser | "Looks right" without ever running |
| Verification | Unit tests, structure checks, link checks, real login | Treating "changed" as "verified" |
| Feedback and checkpoints | Failure logs, unfinished items, next entry point | The next session guesses from zero and repeats work |

A harness is not another framework, and it is not a prompt that makes a model "smarter." It is an engineering arrangement: put the information above where the model can read it, act on it and be checked against it, and let failures leave a trace that work can continue from.

## A practical test

Do not judge a project's harness by which model it uses; ask three questions:

1. Can a new member or session find current facts inside the repository instead of relying on memory?
2. When something is wrong, does an automatic check fail instead of requiring a human to stare at it?
3. After an interruption, are there verifiable progress, evidence and next steps?

When all three answers are yes, model-assisted development has a stable base. A large model plus a system prompt usually handles only short tasks; as tasks get longer, lost context, drifting constraints and premature "done" appear together.

## What it does not solve

A harness cannot replace requirements judgement, permission design or human review, and it cannot guarantee that generated business logic is correct. Tool limits remain: automated tests may not cover a real external authorization page, and browser tooling may be constrained by the environment. In those cases the right move is to record what is unverified, not to hide it behind a longer prompt.

One vendor engineering post describes common failures of long-running agents as "trying to do too much at once" and "declaring the job done" prematurely, and describes practices such as an init script, a progress file, a feature list and commit summaries. That is one organization's experience, not an industry standard; this chapter treats it only as a verifiable case. See [the industry case](harness-industry-practice.en.md).

## Continue reading

- Composition and boundaries: [The five parts of a harness](harness-anatomy.en.md)
- What this project has implemented: [Omni-Stack's actual harness](harness-omni-stack-practice.en.md)
- Keeping facts valid: [Fact maintenance and invalidation](harness-fact-maintenance.en.md)
- Doing secondary development: [A complete small task](harness-secondary-development.en.md)
