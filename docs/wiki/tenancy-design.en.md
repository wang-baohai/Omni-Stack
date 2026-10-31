# 2. Design: separate different companies' data

Must Company A and Company B put their data on two machines to be isolated?

Not necessarily. They can use separate databases, separate spaces within a database, or shared tables with a company identifier on each record. Every arrangement still needs correct authorization. Physical separation does not automatically fix login, permissions, or caching.

## Three common arrangements

| Arrangement | Picture it as | Benefit | Responsibility |
|---|---|---|---|
| A database for each tenant | Each company has its own records room | Backups, relocation, and resources can be managed separately | More databases, migrations, connections, and operations |
| Separate schemas in databases supporting them | One building with separate rooms | Some separation of objects and management | Database support and migration tooling must cooperate; selecting the wrong space is still possible |
| Shared tables carrying tenant identifiers | Shared cabinets with an owner on each file | Centralized deployment and upgrades | Reads, writes, caches, and background tasks must retain company boundaries |

This compares choices; it does not ask you to create databases or change deployment now. Scale, compliance, recovery needs, and operational capability also matter.

A shared-table design requires more than an extra column: every later access must respect the ownership it represents.

## The project's arrangement

Business tables store the owning company identifier tenant_id. Server identity, explicit queries, tenant plugins, and domain data scope control access together.

The project also separates databases by microservice responsibility. Authentication data and domain data have their own owners. That is **separation by service**, not a new set of databases for each company.

Separate these two dimensions:

- Service asks who manages authentication, the platform, workflows, and other responsibilities.
- Tenant asks which company owns a particular business record.

One service database can hold several companies' data, and one company's operation can pass through several services.

## Why reuse MyBatis-Plus?

MyBatis maps Java database access. MyBatis-Plus adds common operations and plugin mechanisms, already used for many project tables.

A plugin inserts processing at a particular stage of database access. Later, common-service means shared support code used by several business services. A service must include and enable the relevant capability to use its chain. The library is not another business service to launch.

For applicable statements, the tenant plugin adds the company condition, reducing repeated manual work. The scope plugin connects domain rules for self and organization visibility.

This is a trade-off inferred from current dependencies and implementation, not an invented historical selection meeting. The project still owns permissions, valid state changes, child-record ownership, and message tenancy.

The next page uses real 3.5.16 source to show the mechanism rather than just list framework names.

## Three kinds of “shared” that need care

**Shared login still needs separate authorization.** Recognizing a person at one entry does not decide the tenant and operation at each business service.

**A common plugin does not mean identical service configuration.** Auth explicitly provides tenant conditions; common-service domains can enable tenant/scope plugins; Workflow handles business and engine tables separately.

**A shared cache still distinguishes ownership.** One Redis can hold several companies' data when keys, reads, and invalidation retain the right company.

When reading an implementation, identify which path it uses instead of relying on a general slogan.

## Why global content still needs protection

Tenant directories, global permission-code catalogs, personal-job types, and Nacos/XXL-JOB infrastructure are not all ordinary records owned by one company.

Shared content has a different management boundary. It does not grant anonymous or ordinary business requests unrestricted read/write access. A relay scanning multiple tenants' messages does not justify cross-tenant business queries.

The final page brings caches, background threads, and these exceptions together. First read [how the framework adds conditions](tenancy-framework.en.md).
