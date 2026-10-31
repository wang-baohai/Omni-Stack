# 3. Inside the framework: from timetable to execution

Think of a duty desk assigning work: find jobs that are almost due, record the assignment, select a worker, send the work and receive a result. The continuous short excerpts below come from version 3.3.1. Each explanation stays next to its source.

## Step 1: coordinate the center and find upcoming jobs

Several center instances must coordinate scanning. This code starts a transaction, acquires the scheduling lock and queries a short upcoming window. The lock coordinates scheduling scans; it does not prove that arbitrary business work executes exactly once.

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/schedule-lock.java.txt" -->
```java
                        transactionStatus = XxlJobAdminBootstrap.getInstance().getTransactionManager().getTransaction(new DefaultTransactionDefinition());
                        // 1、job lock
                        String lockedRecord = XxlJobAdminBootstrap.getInstance().getXxlJobLockMapper().scheduleLock();
                        long nowTime = System.currentTimeMillis();

                        // scan and process job
                        List<XxlJobInfo> scheduleList = XxlJobAdminBootstrap.getInstance().getXxlJobInfoMapper().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
```

Later code distinguishes missed times and the pre-read window, triggers immediately or places the job in a time ring, and updates its next time. The configured misfire policy applies to missed runs; it does not replay every historical occurrence indefinitely.

## Step 2: package this execution

The center first creates a scheduling log ID, then includes the job, handler, arguments, timeout and log association in the request. The executor receives the inputs for this run instead of guessing from a description.

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

The center then chooses an online address in the executor group using its routing policy. No address means no dispatch. A selected address still needs a working connection and the correct handler.

## Step 3: register the methods a service can run

The Spring executor scans methods carrying @XxlJob and registers each method with its owning object. This excerpt is the registration loop after discovery.

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

Registering a handler and registering an executor address are different operations. If the service is online but a handler is missing, check its deployed name rather than repeatedly changing Cron.

## Step 4: invoke the business method

A BEAN handler eventually invokes the registered method through reflection. With no parameters it calls directly; otherwise it supplies an array of the required length. This is not automatic conversion of the job JSON into your DTO. The project explicitly reads and parses its arguments.

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

The execution thread also manages blocking, timeouts and the reported outcome. A timeout attempts to interrupt the execution thread; it cannot guarantee reversal of an external request or an already committed database operation. Check side effects before recovery.

## Step 5: report completion

Results enter a callback queue. A separate thread collects them and sends them to the center. This excerpt shows queue retrieval and batching; subsequent logic handles callback failures and retries.

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

This explains the separate dispatch and execution results in the UI. When dispatch succeeds but completion is pending, neither business success nor business failure is established. Inspect execution logs and callbacks first.

## Provenance and next article

These excerpts come from official commit 7548d1a62b187684b698e05618947f26d3e4a54a, copyright xuxueli. Exact paths, lines and digests are in the [provenance record](../reference/upstream/xxl-job-3.3.1/provenance.json). The unchanged [GPL v3 license](../reference/upstream/xxl-job-3.3.1/LICENSE.txt) and [NOTICE](../reference/upstream/xxl-job-3.3.1/NOTICE.txt) accompany this version. Continue directly to [project integration](distributed-jobs-project.en.md).
