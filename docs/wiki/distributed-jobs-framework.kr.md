# 3. 내부 원리: 시간표가 한 번의 실행이 되기까지

당직실이 일을 배정한다고 생각하세요. 시간이 가까운 작업을 찾고, 배정을 기록하고, 담당자를 골라 보내고, 결과를 받습니다. 아래는 3.3.1의 연속된 짧은 원문이며 각 단계 옆에 설명을 붙였습니다.

## 첫째: 센터를 조정하고 예정 작업 찾기

센터가 여러 개면 스캔을 조정해야 합니다. 이 코드는 트랜잭션을 시작하고 스케줄링 잠금을 얻은 뒤 가까운 시간의 작업을 조회합니다. 이 잠금은 스캔을 조정할 뿐 임의의 업무가 정확히 한 번 실행된다는 보장은 아닙니다.

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/schedule-lock.java.txt" -->
```java
                        transactionStatus = XxlJobAdminBootstrap.getInstance().getTransactionManager().getTransaction(new DefaultTransactionDefinition());
                        // 1、job lock
                        String lockedRecord = XxlJobAdminBootstrap.getInstance().getXxlJobLockMapper().scheduleLock();
                        long nowTime = System.currentTimeMillis();

                        // scan and process job
                        List<XxlJobInfo> scheduleList = XxlJobAdminBootstrap.getInstance().getXxlJobInfoMapper().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
```

뒤에서는 놓친 시각과 미리 읽는 구간을 구분해 즉시 실행하거나 시간 링에 넣고 다음 시간을 갱신합니다. 놓친 실행에는 설정한 misfire 정책을 적용하며, 과거의 모든 회차를 무제한으로 다시 실행하지 않습니다.

## 둘째: 이번 실행 정보를 담기

배정 전에 스케줄링 로그 ID를 만들고 작업, 처리기, 인수, 제한 시간, 로그 연결을 요청에 담습니다. 실행기는 이름을 보고 추측하지 않고 이번 입력을 받습니다.

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

이후 그룹의 온라인 주소와 라우팅 정책으로 대상을 선택합니다. 주소가 없으면 보낼 수 없습니다. 주소를 골랐어도 연결 가능 여부와 처리기 존재는 따로 확인해야 합니다.

## 셋째: 서비스가 할 수 있는 일 등록하기

Spring 실행기는 @XxlJob가 붙은 메서드를 찾고 객체와 메서드를 등록합니다. 다음은 발견 이후의 등록 반복문입니다.

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

처리기 등록과 실행기 주소 등록은 다릅니다. 온라인인데 handler가 없다고 나오면 배포된 이름을 확인합니다. Cron을 반복해서 수정해도 해결되지 않습니다.

## 넷째: 업무 메서드 호출하기

BEAN 방식은 등록된 객체의 메서드를 리플렉션으로 호출합니다. 인수가 없으면 직접 호출하고, 있으면 해당 길이의 배열을 전달합니다. 작업 JSON을 업무 DTO로 자동 변환한다는 뜻이 아니며, 프로젝트가 직접 인수를 읽고 해석합니다.

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

실행 스레드는 중복 요청 처리, 제한 시간, 결과도 관리합니다. 시간 초과 시 인터럽트를 시도하지만 외부 요청이나 이미 커밋한 DB 작업의 취소를 보장하지는 않습니다. 복구 전에 업무 부수 효과를 확인해야 합니다.

## 다섯째: 완료 보고하기

결과는 콜백 큐에 들어가고 별도 스레드가 모아서 센터로 보냅니다. 다음은 꺼내기와 묶기 단계입니다. 콜백 오류와 재시도는 후속 로직에서 처리합니다.

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

그래서 화면에 배정 결과와 실행 결과가 따로 표시됩니다. 배정이 성공해도 완료를 기다리는 중이면 업무 성공이나 실패를 단정하지 말고 실행 로그와 콜백을 확인합니다.

## 출처와 다음 글

공식 커밋 7548d1a62b187684b698e05618947f26d3e4a54a의 원문이며 저작권자는 xuxueli입니다. 정확한 경로, 행, 해시는 [출처 기록](../reference/upstream/xxl-job-3.3.1/provenance.json)에 있습니다. 이 버전의 전체 [GPL v3 라이선스](../reference/upstream/xxl-job-3.3.1/LICENSE.txt)와 변경하지 않은 [NOTICE](../reference/upstream/xxl-job-3.3.1/NOTICE.txt)를 함께 제공합니다. 바로 [프로젝트 적용](distributed-jobs-project.kr.md)으로 이어집니다.
