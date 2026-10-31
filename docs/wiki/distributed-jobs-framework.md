# 3. 框架拆解：一张时间表怎样变成一次执行

把全过程想成值班室派单：先找即将到点的任务，再写派单记录，选择工作人员，把工作交出去，最后接收结果。本页引用固定版本 3.3.1 的连续短源码；每段前后都解释它解决的问题。

## 第一步：协调调度中心，再找快到点的任务

多个调度中心同时工作时，需要协调这轮扫描。下面先开启事务、取得调度锁，再查询接下来一小段时间内要处理的任务。这里的锁协调调度扫描，不能据此推导任意业务只会执行一次。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/schedule-lock.java.txt" -->
```java
                        transactionStatus = XxlJobAdminBootstrap.getInstance().getTransactionManager().getTransaction(new DefaultTransactionDefinition());
                        // 1、job lock
                        String lockedRecord = XxlJobAdminBootstrap.getInstance().getXxlJobLockMapper().scheduleLock();
                        long nowTime = System.currentTimeMillis();

                        // scan and process job
                        List<XxlJobInfo> scheduleList = XxlJobAdminBootstrap.getInstance().getXxlJobInfoMapper().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
```

后续代码根据是否已经错过时间、是否进入预读窗口来决定立即触发或放入时间轮，并更新下一次时间。错过时间采用配置的 misfire 策略，不是无限补跑所有历史次数。

## 第二步：把这一次执行装进信封

派发前先有调度日志 ID，再把任务、处理器、参数、超时和日志关联装进请求。参数随这次执行发送，不是让执行器凭任务名称猜要做什么。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/trigger-envelope.java.txt" -->
```java
        // 2、init trigger-param
        TriggerRequest triggerParam = new TriggerRequest();
        triggerParam.setJobId(jobInfo.getId());
        triggerParam.setExecutorHandler(jobInfo.getExecutorHandler());
        triggerParam.setExecutorParams(jobInfo.getExecutorParam());
        triggerParam.setExecutorBlockStrategy(jobInfo.getExecutorBlockStrategy());
        triggerParam.setExecutorTimeout(jobInfo.getExecutorTimeout());
        triggerParam.setLogId(jobLog.getId());
        triggerParam.setLogDateTime(jobLog.getTriggerTime().getTime());
        triggerParam.setGlueType(jobInfo.getGlueType());
        triggerParam.setGlueSource(jobInfo.getGlueSource());
        triggerParam.setGlueUpdatetime(jobInfo.getGlueUpdatetime().getTime());
        triggerParam.setBroadcastIndex(index);
        triggerParam.setBroadcastTotal(total);
```

随后按执行器组的在线地址和路由策略选择目标。没有地址就无法派发；选到了地址，也还要确认网络可达和处理器存在。

## 第三步：服务启动时登记“我能做的事”

Spring 执行器扫描带 @XxlJob 的方法，把方法和对象登记成可调用的处理器。以下是扫描完成后逐项注册的原文。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/handler-registration.java.txt" -->
```java
            // 2.3、scan + registry Jobhandler
            Object jobBean = applicationContext.getBean(beanName);
            for (Map.Entry<Method, XxlJob> jobMethodEntry : annotatedMethods.entrySet()) {
                Method jobMethod = jobMethodEntry.getKey();
                XxlJob xxlJob = jobMethodEntry.getValue();
                // regist
                registryJobHandler(xxlJob, jobBean, jobMethod);
            }
```

注册处理器和注册执行器地址是不同事情。服务在线却提示找不到 handler，要检查实际部署的处理器名称；不能只反复修改 Cron。

## 第四步：执行器真正调用业务方法

BEAN 方法最终通过反射调用已登记的对象方法。没有参数时直接调用；有参数时使用相应长度的参数数组，这不等于自动把任务 JSON 绑定成你的业务 DTO。本项目自己读取并解析任务参数。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/handler-invocation.java.txt" -->
```java
    @Override
    public void execute() throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0) {
            method.invoke(target, new Object[paramTypes.length]);       // method-param can not be primitive-types
        } else {
            method.invoke(target);
        }
    }
```

执行线程还管理阻塞、超时和处理结果。超时会尝试中断执行线程，但中断不保证外部请求或已经提交的数据库操作被撤销，所以超时后依然要核对业务副作用。

## 第五步：完成后回报结果

执行结果进入回调队列，由回调线程收集并发回调度中心。下面展示的是取出和合并回调的步骤，失败重试由后续逻辑处理。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/completion-callback.java.txt" -->
```java
                        CallbackRequest callback = getInstance().callBackQueue.take();
                        if (callback != null) {

                            // collect callback data
                            List<CallbackRequest> callbackParamList = new ArrayList<>();
                            callbackParamList.add(callback);                                            // add one element
                            int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);    // drainTo other all elements

                            // do callback, will retry if error
                            if (CollectionTool.isNotEmpty(callbackParamList)) {
                                doCallback(callbackParamList);
                            }
```

这就是网页分别显示“调度结果”和“执行结果”的原因。前者成功、后者尚未完成时，不能说业务失败，也不能说业务已经成功；先查执行日志和回调，再决定是否恢复。

## 来源与下一步

以上原文均来自官方仓库提交 7548d1a62b187684b698e05618947f26d3e4a54a，版权归 xuxueli。准确路径、行号和摘要在[来源登记](../reference/upstream/xxl-job-3.3.1/provenance.json)，本版本完整 [GPL v3 许可](../reference/upstream/xxl-job-3.3.1/LICENSE.txt)与[原始 NOTICE](../reference/upstream/xxl-job-3.3.1/NOTICE.txt)随摘录保留。无需离开本站即可继续看[项目怎样接入](distributed-jobs-project.md)。
