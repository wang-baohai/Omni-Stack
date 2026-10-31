# 2. Why XXL-JOB: decide what needs to be managed

There is more than one way to run work periodically. First ask whether you need assignments across machines, a shared overview, manual triggers, execution records and recovery. Additional tooling also adds deployment, authentication and consistency work.

## Three common approaches

| Approach | Suitable needs | Responsibilities you still own |
|---|---|---|
| Operating-system scheduler | Fixed scripts and maintenance on one machine | Machine management, application permissions, logs and duplicate execution |
| In-process scheduler | A few periodic tasks that start with a service | Coordination across replicas, shared management and recovery |
| Scheduling center with executors | Multiple services, remote assignments and shared records | Center availability, registration, networking and business idempotency |

XXL-JOB belongs to the third category. This project already needs maintenance across services and jobs created by users, so it uses the existing center and executors. This explains the current decision; it neither rules out other tools nor introduces another scheduling platform.

## Current version and deployment roles

The project pins XXL-JOB core and Admin to 3.3.1. Admin owns its scheduling records. Business services keep their business data and personal-job ownership. Executors run inside services, each with a different AppName so the center can distinguish destinations.

The same handler name can exist in different executor groups. Several services have an MQ relay, for example, but each processes its own records. Always check both the group and the handler; the name alone can identify the wrong object.

## Two records do not make one transaction

When a user creates a personal job, Base stores its owner and asks the scheduling center to register its timing. A local database transaction does not automatically include an HTTP write to another service.

The project therefore stores the remote ID and handles registration failures, lost responses and failed local ID updates. Delete the local record only after confirming that no remote job remains. If the remote outcome is unknown, retain the association for reconciliation; an error response does not prove that nothing was created.

This is more complicated than writing one timer method, but it makes recovery responsibilities explicit. The later articles show the actual recovery code inline.

## Responsibilities that stay in the services

Personal ownership depends on both tenant and creator. Business permissions, state machines, transactions and idempotency remain service responsibilities. Native Admin users are not the same authorization system as Omni-Stack roles.

Workflow answers who approves and what happens next. Scheduling answers when to attempt an execution. MQ answers how to hand off asynchronous messages. Scheduling may wake a relay, but delivery still needs Outbox, broker and consumer evidence. These tools serve different purposes.

## The operational cost

An unavailable center affects registration, updates and triggers. An unreachable executor address prevents dispatch. Slow work can block later work, while careless retries can repeat business effects. Keep logs, choose sensible intervals, restrict management permissions and provide recovery paths.

Continue with [framework internals](distributed-jobs-framework.en.md). The excerpts use the exact current version and explain the flow before asking you to remember class names.
