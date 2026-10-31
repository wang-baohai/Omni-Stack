# Omni-Stack

A Spring Boot 4 + Vue 3 microservices scaffold for enterprise applications with tenant isolation, access control, and room for business extensions. Source code, engineering facts, illustrated tutorials, and AI collaboration rules live in one repository.

[简体中文](README.md) · [日本語](README.jp.md) · [한국어](README.kr.md)

## Highlights

- Read the Wiki from the public home page; find authorized applications in your workspace and open business modules in new tabs.
- Multiple tenants, functional permissions, and data scopes; CAPTCHA login, social login, and OAuth2 authorization.
<!-- omni:modules:workflow -->
- A standalone Workflow service for BPMN modeling, publishing, approvals, and countersigning.
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB manages system and personal tasks.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM covers leads, customers, opportunities, and follow-up.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset connects supplier, purchasing, and asset processes.
<!-- omni:modules:end -->
- Transactional Outbox, idempotent consumption, and dead-letter management; optional metrics, tracing, and centralized log collection.
- `tools/omni-cli` generates independent preset projects, services, and CRUD.
- Harness practices support ongoing development with concise engineering facts, shared AI workflows, and verification evidence.

Technology baseline: Spring Boot 4.0.6, JDK 25, Spring Cloud, Vue 3, TypeScript, Vite, and Element Plus.

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[Full Wiki: introduction, illustrated usage, startup, and development](docs/wiki/index.en.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
