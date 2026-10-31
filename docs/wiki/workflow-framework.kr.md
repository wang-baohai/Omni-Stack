# 3. 프레임워크 원리: 완료를 누르면 Flowable은 어떻게 다음으로 갈까

프로젝트가 사용하는 Flowable 8.0.0을 설명합니다. 아래는 공식 소스 배포본에서 연속된 짧은 구간을 원문 그대로 발췌한 것입니다. Java 타입을 모두 외우기보다 각 코드 앞뒤의 설명을 먼저 읽으세요.

## 첫 단계: 동작을 공통 실행 입구에 전달한다

TaskServiceImpl의 234–237행입니다.

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-command.java.txt" -->
```java
    @Override
    public void complete(String taskId) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, (Map<String, Object>) null));
    }
```

호출자가 작업 ID를 주면 프레임워크가 완료 동작을 CompleteTaskCmd로 감싸 commandExecutor에 넘깁니다. 공통 접수 창구에 처리 요청서를 내는 것과 같습니다. 이것만으로 프로젝트의 구매 승인 규칙을 판단하지는 않습니다.

CompleteTaskCmd는 이어서 TaskHelper.completeTask를 호출합니다. 변수·완료 리스너·이력·이벤트 등을 처리하지만, 현재 사용자가 승인할 권한이 있는지 프로젝트가 검사했다는 뜻은 아닙니다. 별도의 접근 검사가 필요합니다.

## 두 번째: 현재 작업을 끝내고 다음 실행을 예약한다

TaskHelper의 178–184행입니다.

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-advance.java.txt" -->
```java
        completeTask(taskEntity, userId);

        // Continue process (if not a standalone task)
        if (taskEntity.getExecutionId() != null && !bpmnErrorPropagated) {
            ExecutionEntity executionEntity = processEngineConfiguration.getExecutionEntityManager().findById(taskEntity.getExecutionId());
            CommandContextUtil.getAgenda(commandContext).planTriggerExecutionOperation(executionEntity);
        }
```

첫 호출이 작업을 완료합니다. 프로세스 실행에 속하고 해당 BPMN 오류가 전파되지 않은 경우, 실행을 계속하는 동작을 예약합니다.

완료는 상태 한 줄만 바꾸는 일이 아닙니다. 게이트웨이로 가거나 새 작업을 만들거나 프로세스를 끝낼 수도 있습니다. 프로젝트가 complete보다 먼저 의견과 집계를 쓰는 것은 다음 실행이 이번 결과를 읽도록 하기 위해서입니다. 계속 진행하도록 예약했다는 사실이 모든 후속 일이 같은 요청에서 끝남을 뜻하지는 않습니다.

## 세 번째: 여러 사람의 승인이 언제 끝날지 판단한다

같은 노드에서 여러 사람에게 별도 작업을 만드는 것이 다중 인스턴스입니다. ParallelMultiInstanceBehavior의 231–234행 중 이탈 호출까지입니다.

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/parallel-completion.java.txt" -->
```java
            boolean isCompletionConditionSatisfied = completionConditionSatisfied(execution.getParent());
            if (nrOfCompletedInstances >= nrOfInstances || isCompletionConditionSatisfied) {
                leave(executionEntity, nrOfInstances, nrOfCompletedInstances, isCompletionConditionSatisfied);
```

모든 인스턴스가 끝났거나 모델의 완료 조건이 성립했는지 확인합니다. 두 조건은 “또는”입니다. 프로젝트의 “반려 표가 있거나 승인 표가 기준에 도달” 조건이면 전원이 처리하기 전에 노드를 떠날 수 있습니다.

조건식은 참 또는 거짓을 내야 합니다. 프레임워크가 회사의 필요 승인 수를 알아서 정하지 않습니다. ALL·ANY와 프로젝트 집계의 관계는 작업 편에서 설명합니다.

## 네 번째: 아직 처리하지 않은 작업이 사라지는 이유

MultiInstanceActivityBehavior의 266–269행입니다.

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/multi-instance-cleanup.java.txt" -->
```java
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();
        Collection<String> executionIdsNotToSendCancelledEventsFor = execution.isMultiInstanceRoot() ? null : Collections.singletonList(execution.getId());
        executionEntityManager.deleteChildExecutions(multiInstanceRootExecution, null, executionIdsNotToSendCancelledEventsFor, DELETE_REASON_END, true, flowElement);
        executionEntityManager.deleteRelatedDataForExecution(multiInstanceRootExecution, DELETE_REASON_END, false);
```

다중 인스턴스 실행과 관련 데이터를 정리하면서 DELETE_REASON_END를 전달합니다. 같은 클래스에서 이 상수는 MI_END로 정의됩니다. 조기 종료 조건을 만족하면 아직 투표하지 않은 사람의 작업도 이 이유로 끝날 수 있습니다.

**규칙에 따라 남은 대기를 종료한 것이며, 그 사람이 찬성한 것은 아닙니다.** 기존 표시 이름 auto-approved도 이 사실을 바꾸지 못합니다.

## 배포도 명령을 거친다

RepositoryServiceImpl의 98–100행입니다.

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/deployment-command.java.txt" -->
```java
    public Deployment deploy(DeploymentBuilderImpl deploymentBuilder) {
        return commandExecutor.execute(new DeployCmd<Deployment>(deploymentBuilder));
    }
```

배포는 모델 리소스를 엔진에 전달합니다. 프로젝트의 게시에는 모델 잠금·초안 검증·업무 버전 기록·게시 참조 갱신도 필요합니다. 배포만으로 게시 전체나 다른 업무 서비스의 문서 생성까지 완료된 것은 아닙니다.

## 이 발췌가 증명하는 범위

명령 접수·실행 진행·다중 인스턴스 처리를 설명합니다. 프로젝트에서 제공하지 않는 작업 인수·지정 노드로 되돌리기·인스턴스 이전 화면이나 로컬 승인 성공을 증명하지 않습니다.

소스 배포본을 공식 SHA-256과 대조했습니다. 원래 경로·행·해시·저작권은 [출처 기록](../reference/upstream/flowable-8.0.0/provenance.json), 전체 라이선스는 [Apache 2.0](../reference/upstream/flowable-8.0.0/LICENSE.txt)에 있습니다. [Flowable 공식 BPMN 안내](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/)는 선택적으로 읽는 자료이며 본문 이해에 외부 이동이 필요하지 않습니다.

다음은 [프로젝트의 모델 게시](workflow-models.kr.md)입니다.
