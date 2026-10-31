# 1. Scheduled jobs from the beginning: who works at the right time?

Suppose you want a daily reminder to drink water, or need to hand pending messages to a broker at regular intervals. Someone should not have to keep a page open and click a button. A scheduled job records what to do and when to do it, then lets a program run it.

Scheduling does not automatically solve failures, duplicates or authorization. This chapter starts with those ideas, explains the XXL-JOB version used here, and then walks through real screens. You do not need to read Java first.

## Separate three questions

| Question | Responsible component | A useful analogy |
|---|---|---|
| When should work start? | Scheduling center | A duty desk with a timetable |
| Which service instance receives this run? | Routing and executor registration | Assigning work to an available worker |
| What happened, and did it succeed? | Business handler inside the executor | Doing the work and reporting back |

An executor is not another independent business system. In this project, executors run inside the business services; the native XXL-JOB Admin is the shared scheduling management website. Accepting an assignment does not prove business completion: check execution records and the actual business outcome.

## What does “distributed” mean?

A service may have several instances, and different services may perform different work. The scheduling center discovers available executors and selects addresses according to a policy. Central management helps operators inspect and recover jobs.

It does not mean every job is automatically divided into pieces. Ordinary routing may choose one address; a sharding broadcast may run work on several addresses. Partitioning data and preventing duplicate effects remain business responsibilities.

## Cron is a timetable

Cron describes seconds, minutes, hours and calendar fields. Start with the page's schedule selector to understand “at this time every day” or “every so often”, then check the expected run times. Do not mix a five-field operating-system Cron expression with the seconds-based expressions used here.

Also check the time zone, execution duration and missed-run policy. Scheduling every ten seconds while each run takes a minute requires a deliberate blocking policy, such as queuing, discarding later runs or replacing an earlier run. More triggers do not make a handler faster.

## Why duplicates happen, and why idempotency matters

A service can finish its work while the success callback is lost. From the center's perspective there is no confirmation, so another assignment may repeat the work. A manual run can also overlap a scheduled run.

Idempotency means handling the same work again without repeating an effect that should happen once. A stable business identifier, a uniqueness constraint and an appropriate transaction can help. A scheduling lock, retry policy or single selected address cannot replace business idempotency.

## Two tracks in this project

System jobs ship with the application, such as operation-log archiving and MQ relay, and are managed by authorized administrators. Personal jobs, such as water reminders, are created in My Workspace and belong to their creator in the current tenant.

Both tracks use XXL-JOB, but their entry points, ownership checks and logs differ. Do not edit personal jobs in native Admin to bypass Workspace ownership checks.

## Reading order

Continue with [tool choice and boundaries](distributed-jobs-design.en.md), [framework internals](distributed-jobs-framework.en.md), [the project's two job chains](distributed-jobs-project.en.md), [the native Admin screens](distributed-jobs-admin.en.md), [Workspace operations](distributed-jobs-usage.en.md), and [recovery and extension](distributed-jobs-recovery.en.md). Relevant source appears directly in the technical articles; the operation articles require no code.
