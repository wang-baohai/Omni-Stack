# 1. Multi-tenancy: how one application serves several companies

Company A and Company B both want to manage their work in Omni-Stack. They can open the same application, but Company A's employees should not thereby see Company B's customers, suppliers, or requests.

Multi-tenancy addresses this: **when software is shared, keep track of who owns each piece of data and respect that boundary.** Think of a tenant as a company's business space in the system. Employee accounts belong to the relevant space.

This chapter explains concepts before framework and project source. The first two pages require no Java or database-query knowledge.

## Start with two people who share a name

Both companies may have an employee named Li. The name does not make them one account. Selecting the company before entering the account lets the system look in the correct space.

Both companies may also have a “Sales manager” role without sharing one authorization record. Check which company owns the role, who receives it, and which records it covers.

This story explains rules. These are not supplied test accounts or instructions to create business data with these names.

| Person | Company space | Department | Responsibility |
|---|---|---|---|
| Li at Company A | Company A | East sales | Maintain his own customers |
| Manager Wang at Company A | Company A | East sales | See customers in the department's responsibility |
| Li at Company B | Company B | Customer service | Handle Company B's records |

Wang having more permission than Li does not automatically make Wang a manager at Company B. Department, role, and company space are separate levels.

## Ask four separate questions

| Question | Concept | Story |
|---|---|---|
| Which company owns this record? | Tenant | The customer belongs to Company A |
| In which department does this person work? | Organization | Li works in East sales |
| What operation may this person perform? | Role and functional permission | A salesperson maintains customers; a manager may assign them |
| Once the operation is allowed, which records may be handled? | Data scope | Own, department, or company records |

Organizations arrange people inside a company space. Adding a department does not mean creating another tenant. Two managers do not automatically gain access to each other's records.

Functional permission and data scope work together. Permission to “view customers” answers whether the operation is allowed, not which customer can be viewed.

## Visibility is not the only protection

Permission-based menus and buttons help people find their work. The server must still check identity, tenant, operation, data scope, and business state when data is requested.

Otherwise, knowing an address or record number could bypass the screen. Treating those values as secrets cannot replace authorization.

This follows the authentication chapter: login establishes who you are; tenancy and data permissions establish the company and part of the business involved.

## Keep this relationship in mind

The tenant establishes the company boundary; organizations and scope organize work inside it. Some interfaces have dedicated management across tenants, so services cannot all be treated as one automatic rule.

For example, domain tenant plugins retain company conditions, while online-user management has explicit cross-tenant ALL handling. Later pages distinguish these rather than assuming “super administrator” means the same thing everywhere.

## Continue in order

| Page | What it explains |
|---|---|
| [2. Separate the data](tenancy-design.en.md) | Why one database per company is not compulsory, and what this project chooses |
| [3. Framework conditions](tenancy-framework.en.md) | How MyBatis-Plus rewrites queries, and how tenant and scope plugins differ |
| [4. A request carries its tenant](tenancy-request.en.md) | How trusted identity passes through login, gateway, services, and storage |
| [5. Roles, organizations, and visible data](tenancy-permissions.en.md) | How self, department, descendants, tenant, and exceptions interact |
| [6. Caches, background work, and extension](tenancy-background.en.md) | How to carry tenancy beyond a request and check a new feature |

Source appears where it is needed, with plain explanations. You do not need to jump between repository files to follow one request.
