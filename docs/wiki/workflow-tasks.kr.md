# 6. 프로젝트 구현: 승인·반려·여러 사람의 표를 집계하기

후보는 규칙으로 찾은 사람들의 집합, 담당자는 작업의 assignee, 승인 기록의 사람은 실제 처리한 사람입니다. 설계 화면이 만드는 다중 인스턴스 작업은 직접 배정됩니다. 별도의 미인수 작업 풀은 없습니다.

## 먼저 지금 자신의 작업인지 확인한다

완료·승인자 추가·제거·위임에는 각각 workflow:approval:complete, add-signer, remove-signer, delegate 기능 권한이 필요합니다. 서비스는 현재 테넌트의 작업을 찾아 현재 사용자와 실제 담당자가 정확히 같은지도 확인합니다.

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImpl.java" -->
```java
    private Task requireCurrentAssigneeTask(String taskId, Long userId, Long tenantId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskTenantId(String.valueOf(tenantId))
                .singleResult();
        if (task == null) {
            throw new BusinessException(409, "审批任务已被处理或不存在，请刷新待办列表");
        }
        if (!String.valueOf(userId).equals(task.getAssignee())) {
            throw new BusinessException(403, "无权操作该审批任务");
        }
        return task;
    }
```

존재하지 않거나 다른 테넌트라 조회되지 않으면 업무 코드 409이므로 목록을 새로 확인합니다. 같은 테넌트라도 남의 작업이면 403입니다. 반복 클릭이나 관리자 역할만으로 대리 승인 권한을 얻지 않습니다.

## 승인이나 반려는 먼저 한 사람의 한 표다

approved는 이번 판단, comment는 의견입니다. 의견·작업 로컬 결과와 함께 프로세스 루트의 approved도 씁니다. 루트 값은 다음 표로 덮어쓰므로 여러 사람의 합계를 이 값만으로 판단하면 안 됩니다.

반려도 같은 완료 입구에서 approved=false로 처리합니다. 이전 노드로 자동 복귀하지 않으며 종료할지 다른 경로로 갈지는 모델의 조건·연결이 결정합니다.

## 다중 승인에는 공통 집계 장소가 필요하다

다중 인스턴스 루트는 이번 회의의 집계장, 각 작업은 개인 투표지라고 생각하세요. 프로젝트는 최대 32단계 위로 올라가 루트 로컬 nrOfInstances를 찾습니다. 없으면 이전 정의의 호환 갱신을 유지합니다.

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        if (miRoot != null) {
            runtimeService.setVariableLocal(miRoot, counterKey, currentCount + 1);
        } else {
            runtimeService.setVariable(execId, counterKey, currentCount + 1);
        }
```

찬성은 approvedCount, 반려는 rejectedCount를 늘리고 **그 후** taskService.complete를 호출합니다. 설계 화면의 완료 조건은 다음과 같습니다.

```text
${rejectedCount > 0 || approvedCount >= requiredApprovals}
```

기본 ALL의 초기 기준은 후보 전체 인원이며 ANY는 1입니다. 이 식에서는 반려 한 표도 노드 종료 조건이 됩니다. 직접 작성한 BPMN에는 맞는 완료 조건과 반려 경로가 필요합니다. ALL·ANY만 적어서는 업무 규칙 전체가 전달되지 않습니다.

추가 variables는 이 갱신 후 작업·프로세스에 쓰며 집계·경로 예약 키를 걸러내지 않습니다. 중요한 변수를 클라이언트가 절대 덮어쓸 수 없다고 보장할 수 없습니다. 확장 시 허용 목록과 거부 테스트를 설계해야 합니다.

## 승인자 추가·제거·위임이 바꾸는 것

| 동작 | 현재 구현 | 제한 |
|---|---|---|
| 추가 | 현재 다중 인스턴스에 실행을 추가하고 userId 전달 | 현재 담당자가 호출. 모델 변수 일치가 필요하며 일반 단독 노드에도 쓸 수 있는 기능은 아님 |
| 제거 | 같은 인스턴스·노드에서 대상자의 미처리 작업을 찾아 실행 제거 | 이미 투표한 표는 철회 불가. 같은 사용자 작업이 여러 개면 singleResult 실패 |
| 위임 | 작업 assignee 변경 | 직접 재배정이며 완료 후 원래 담당자에게 돌아오는 과정이 없음 |

변경 후 ALL 기준은 “이미 찬성한 표 + 남은 대기 작업 수”로 다시 계산합니다. ANY와 루트를 찾지 못한 경우는 재계산하지 않습니다. 승인자 추가 자체는 별도 승인 의견을 쓰지 않으며 새 작업은 이미 투표했다는 뜻이 아닙니다.

제거는 deleteMultiInstanceExecution(..., false)를 호출합니다. **인수 이름은 executionIsCompleted이며 false는 제거된 실행을 상위 다중 인스턴스의 완료 수에 넣지 않는다는 뜻입니다. 연쇄 삭제 스위치가 아닙니다.** 중첩 다중 인스턴스의 실제 동작은 별도 검증해야 합니다.

위임의 핵심 원문은 한 줄입니다.

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        taskService.setAssignee(taskId, targetUserId);
```

Flowable의 delegateTask/resolveTask 반환 절차를 사용하지 않습니다. 추가·제거·위임 대상은 현재 숫자 ID로 해석할 뿐 존재·활성 상태·테넌트를 완전하게 검사하지 않습니다. 버튼과 기능 권한이 있다고 대상 신원 검증까지 끝난 것은 아닙니다.

## 본인이 누르지 않았는데 작업이 끝난 이유

ANY의 찬성이나 다른 조기 종료 조건이 만족되면 남은 작업이 MI_END로 끝날 수 있습니다. 기록은 MI로 시작하는 삭제 사유를 이전 표시인 auto-approved로, 나머지를 cancelled로 처리합니다. **앞의 경우는 시스템이 건너뛴 것이며 그 사람의 찬성표가 아닙니다.**

정상 완료는 작업 로컬 approved로 판단하고 값이 없으면 호환 처리로 승인 취급합니다. 의견은 첫 항목이므로 최신이라는 보장도 없습니다. 읽기 규칙과 실제 사람의 투표를 구분하세요.

## 종료와 아직 제공하지 않는 동작

일반 종료에는 workflow:instance:terminate 권한 외에 동일 테넌트·신청자 본인·진행 중 상태라는 조건이 필요합니다. 실행 인스턴스를 삭제하며 나중에 재개할 일시 중지가 아닙니다. 내부 업무 종료는 별도의 신뢰된 서비스 입구이고 해당 서비스 간 경로는 CANCELLED를 기록합니다.

독립된 인수·인수 취소, 워크플로 철회, 별도 이관, 위임 후 반환, 지정 노드로 반려, 실행 중 버전 이전, 작업 중지·활성화, 관리자 대리 승인·대리 종료 입구는 없습니다. 정의 중지는 개별 작업 중지와 다릅니다. 업무 화면의 철회가 내부 종료를 써도 범용 워크플로 철회 API가 생기는 것은 아닙니다.

마지막 [결과와 문제 해결](workflow-events.kr.md)에서 전체를 연결합니다.
