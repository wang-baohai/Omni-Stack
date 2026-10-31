# 4. Project integration: maintenance and personal reminders

There are two starting points: administrators manage system jobs, while users manage personal jobs in the unified Workspace. Both use the executor, but their ownership rules differ.

## System jobs: two annotations with different purposes

XXL-JOB needs a callable handler; the project also needs its name, default schedule and parameter definitions. The registry therefore requires both @XxlJob and @SystemJobMeta:

<!-- omni:source path="omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java" -->
```java
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                SystemJobMeta meta = AnnotationUtils.findAnnotation(method, SystemJobMeta.class);
                if (xxlJob != null && meta != null) {
                    String handlerName = xxlJob.value();
                    SystemJobInfo info = new SystemJobInfo();
```

An execution annotation alone does not put a handler in the project's system-job catalog. Metadata alone does not make it executable. Auto-registration adds missing jobs and preserves existing Cron and parameters, so restarting a service does not overwrite an operator's changes. A catalog entry does not prove execution when the executor is disabled.

MQ relay and log archiving are maintenance jobs. The shared personal-job entry has only the execution annotation; do not add system metadata merely to show it in that catalog.

## Personal jobs: separate schedules, one shared entry

Each personal job has a separate center entry, but all call userJobExecuteHandler. The message carries its local job ID, tenant, type, name and arguments. The shared entry parses the message and locates the handler by type.

The routing lookup is this small:

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java" -->
```java
    public UserJobHandler getHandler(String jobType) {
        return handlers.get(jobType);
    }
```

Spring keys the injected Map by Bean name. Task-00001 therefore needs that exact Bean name; the display name “water reminder” is not the routing code. Adding a type record does not deploy a handler.

## How results reach My Jobs

After the handler, the shared entry writes a personal execution log using the message's explicit tenant, updates the last execution time and reports success or failure to XXL-JOB. The core log write is:

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java" -->
```java
        SysUserJobLog logEntity = new SysUserJobLog();
        logEntity.setJobId(jobId);
        logEntity.setTenantId(message.getTenantId());
        logEntity.setJobName(message.getJobName());
        logEntity.setJobType(jobType);
        logEntity.setFireTime(fireTime);
        logEntity.setExecuteTimeMs(executeTimeMs);
        logEntity.setStatus(status);
        logEntity.setErrorMessage(errorMsg);
        logEntity.setResultMessage(resultMessage);
        sysUserJobLogMapper.insert(logEntity);
```

This does not guarantee completion of the whole chain. Argument parsing can fail before logging, and a database write can fail. The personal log and last-time update do not share one declarative transaction. If a log is missing, inspect the native execution log; do not invent a successful timestamp.

## Why guessing another job's ID does not grant access

Workspace checks the parent job's tenant and creator before changes, deletion, status changes, execution and log access:

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java" -->
```java
    private void verifyOwnership(Long jobId, Long tenantId, String username) {
        SysUserJob job = userJobService.getJobById(jobId);
        if (!tenantId.equals(job.getTenantId()) || !username.equals(job.getCreateBy())) {
            throw new BusinessException(403, "无权操作他人的任务");
        }
    }
```

This is finer than access to a job menu. The log service currently queries by the already checked parent's jobId; it does not independently add a tenant condition to every SQL statement. Background handlers must not rely on a browser thread's tenant context either.

## Preserving recovery associations after creation failures

Normal creation saves the local job, registers remotely and stores the remote ID. A registration error can happen before a remote commit or after a commit whose response was lost. Reconciliation uses the executor group, handler and arguments containing tenant and local ID, rather than guessing from a name.

The local row is removed only after confirming no remote residue remains. If lookup or cleanup also fails, the association is retained and an error is reported. Later schedule edits, status changes and deletion act remotely before updating local data. Restarting or creating again cannot replace reconciliation.

Continue to the [native screens](distributed-jobs-admin.en.md) to connect these stages with visible records.
