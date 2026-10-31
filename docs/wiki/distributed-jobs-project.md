# 4. 项目实践：系统维护和个人提醒怎样共用调度

现在把框架接到 Omni-Stack。先记住两个入口：管理员维护系统任务，普通用户在统一工作台维护自己的个人任务。它们共用执行器，但不共用同一套归属规则。

## 系统任务：一份说明，两类注解

系统任务既要让 XXL-JOB 知道怎样调用，也要让项目知道名称、默认周期和参数。因此工程扫描时同时查找 @XxlJob 和 @SystemJobMeta：

<!-- omni:source path="omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java" -->
```java
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                SystemJobMeta meta = AnnotationUtils.findAnnotation(method, SystemJobMeta.class);
                if (xxlJob != null && meta != null) {
                    String handlerName = xxlJob.value();
                    SystemJobInfo info = new SystemJobInfo();
```

只有执行注解不保证出现在项目的系统任务目录；只有说明注解也不会成为可执行 handler。自动注册只补不存在的任务，已有 Cron 和参数保留，避免服务重启覆盖管理员的修改。执行器开关关闭时，不能据目录存在推断后台正在执行。

MQ 中继和日志归档是系统维护任务。个人任务的共享执行入口只有执行注解，不应为了让它显示在系统目录里再加系统元数据。

## 个人任务：时间表分开，入口共用

每个个人任务在调度中心有自己的条目，却都调用 userJobExecuteHandler。随请求带来的消息包含本地任务 ID、租户、任务类型、名称和参数。共享入口先解析消息，再用任务类型找到处理器。

实际路由表的查询只有这么短：

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java" -->
```java
    public UserJobHandler getHandler(String jobType) {
        return handlers.get(jobType);
    }
```

Spring 注入的 Map 以 Bean 名为键。因此 Task-00001 必须找到同名 Bean，显示名称“喝水提醒”不是路由编号。仅在类型目录中增加一条记录，不会凭空部署处理器。

## 结果怎样回到“我的任务”

处理器完成后，共享入口写个人执行日志，明确使用消息中的租户；然后更新最后执行时间，并向 XXL-JOB 设置成功或失败。核心落库原文如下：

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

这段不能单独证明整个过程一定成功：参数解析失败会在写日志前返回；数据库写入也可能失败。个人日志与最后时间更新没有共同声明式事务，遇到缺日志要继续查原生执行日志，不能自动填一个成功时间。

## 为什么别人的任务不能用编号直接打开

工作台的修改、删除、启停、执行和日志入口，先读取父任务，同时核对当前租户和创建人：

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java" -->
```java
    private void verifyOwnership(Long jobId, Long tenantId, String username) {
        SysUserJob job = userJobService.getJobById(jobId);
        if (!tenantId.equals(job.getTenantId()) || !username.equals(job.getCreateBy())) {
            throw new BusinessException(403, "无权操作他人的任务");
        }
    }
```

这比“有一个任务菜单权限”更细。个人日志服务当前按已校验父任务的 jobId 查询，不能另写成它在所有 SQL 上额外加了租户条件。后台任务也不能依赖浏览器线程残留的租户上下文。

## 创建失败时，如何避免留两份或丢关联

正常创建依次保存本地任务、注册远端、回填远端 ID。注册报错可能发生在远端提交前，也可能是提交后响应丢了。工程按执行器组、handler 和包含租户/本地 ID 的参数查找，不按名称猜测。

只有已确认远端无残留，才回滚本地行。查询或清理仍失败，就保留本地恢复关联并报错。后续编辑周期、启停和删除则先完成远端动作，再更新本地；重启或重复创建不能代替对账。

接着看[原生网页图解](distributed-jobs-admin.md)，把这些后台阶段对应到看得见的列表和日志。
