# Omni-Stack documentation

This documentation explains what Omni-Stack is, how to use and start it, and how to develop it further. The sidebar starts with the project and its use, then development methods and key technologies. Engineering reference follows startup instructions so precise rules remain easy to find.

[简体中文](index.md) · [日本語](index.jp.md) · [한국어](index.kr.md) · [Project overview and highlights](../../README.en.md)

## Start reading

| Chapter | Main content |
|---|---|
| [Project introduction](../../README.en.md) | Project purpose and highlights; directory responsibilities appear below |
| [Illustrated feature tutorials](usage-platform.en.md) | Screenshots explaining the home page, workspace, and business modules |
| [Harness concepts and engineering practice](harness-what-and-why.en.md) | What Harness means and how engineering facts support AI development |
| [Choosing, building, and starting a preset](presets-overview.en.md) | Understand presets, then follow the startup instructions for your choice |
| [Engineering reference](../architecture.en.md) | Check boundaries, interfaces, development patterns and current domain rules |
| [Further development with Harness](harness-secondary-development.en.md) | Development and verification through one concrete task |
| [Shared common capabilities](common-core.en.md) | Why the shared tools exist and how to use and extend them |
| [Multiple tenants](tenancy-principles.en.md) | From separating companies’ data to framework interception, permissions, and background tasks |
| [Workflow](workflow-principles.en.md) | Follow an application through approval, then explore the engine, models, candidates, tasks, and messages |
| [Centralized log collection](logging-principles.en.md) | Distinguish runtime, operation, and security logs, then learn collection, queries, and extension |
| [Unified MQ management](mq-principles.en.md) | From asynchronous messages to Outbox, retries, idempotency, and management recovery |
| [Distributed scheduled jobs (XXL-JOB)](distributed-jobs-principles.en.md) | From timetables and idempotency to framework internals, the two project tracks and real Admin screens |
| [Authentication and authorization](authentication-principles.en.md) | Start with identity and permissions, then explore design choices, framework source, and login implementation |
| [Observability: metrics, logs, traces, and alerts](observability-principles.en.md) | Start with a slow system, then learn the tools, actual configuration, and troubleshooting steps |

Start with the illustrated tutorials to explore the interface, or with preset selection to run the project locally. Screenshots and business numbers in tutorials are examples only; use the IDs returned by your own run.

## Directory responsibilities

| Directory | Responsibility | When to enter |
|---|---|---|
| `omni-backend/` | Spring Boot microservices and common modules | Backend implementation, authentication, tenants, workflow, and business domains |
| `omni-frontend/` | Vue single application (site, workspace, console, portal) | UI, routing, permissions, and frontend tests |
| `scaffold/` | Single source of truth for the module catalog and five presets | Choosing or trimming a preset, confirming the module dependency set |
| `database/` | Liquibase migrations and the seed assertion manifest | Checking schema, seeds, and migration state |
| `docker/` | Build files for application, tool, and documentation images | Building or deploying images |
| `docs/` | Fact documents, guides, the Wiki, and documentation quality scripts | Looking up contracts, tutorials, and publication status |
| `ai/` | Normative sources and derived adapters for shared AI assets | Using or maintaining AI workflows |
| `tools/` | CLI, preset generation, documentation and quality scripts | Generating presets, running quality gates or documentation checks |
| `observability/` | Metrics, tracing, logging, and alerting configuration | Configuring the local observability stack |
| `scripts/` | Formal SQL seeds, fixtures, and operations scripts | Initializing data or running controlled fixtures |

The directory table only describes responsibilities and when to enter; concrete classes, interfaces, and data flows follow the matching fact documents and source.

## Repository relationships

Source code uses [Gitee](https://gitee.com/wang-baohai/Omni-Stack) as `origin` (fetch/push), and [GitHub](https://github.com/wang-baohai/Omni-Stack) as the push remote. Wiki content comes from `docs/` in this repository; the site is generated from the manifest, with no second copy of the content. Chinese is the content baseline; review status for other languages follows the [documentation manifest](../docs-manifest.yaml). A site that builds does not mean its translations have been reviewed.

## Engineering reference

Read [System architecture](../architecture.en.md), [Core flows](../core-flows.en.md), and the [API contract](../api-contract.en.md) to check precise system boundaries, request flows, or interface formats. These documents maintain engineering rules; use the matching illustrated tutorial for operating instructions. Historical acceptance records cannot replace current runtime verification.
