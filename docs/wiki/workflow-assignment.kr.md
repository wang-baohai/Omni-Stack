# 5. 프로젝트 구현: 신청자, 중복 방지, 다음 승인자 찾기

구매 요청이 업무 검사를 통과했어도 누구의 신청인지, 어느 규칙을 쓰는지, “부서장”이 실제로 누구인지 결정해야 합니다.

## 두 시작 입구를 구분한다

| 입구 | 호출자 | 신원과 반복 요청 |
|---|---|---|
| 일반 시작 | 게이트웨이를 통한 process-instance/start | 현재 신원과 workflow:instance:start 검사. 내부 예약 중복 방지가 자동 적용되지는 않음 |
| 내부 시작 | 업무 서비스의 internal/workflow/process-instance/start | 공유 토큰·테넌트 일치 검사. 호출자가 신뢰할 신청자를 주고 요청·업무 키를 예약 |

내부 API는 ID만 알면 타인 대신 시작해도 되는 장치가 아닙니다. 공유 토큰은 서비스 호출을 보호하지만 업무 서비스도 사용자 권한을 확인해야 합니다. startUserId/startUserName을 최종 사용자가 임의로 고르는 값으로 취급하면 안 됩니다.

일반 시작은 다른 사람으로 위장하는 시뮬레이션을 거부하고 입력 변수에서 신청자 예약 키를 지웁니다. ProcessInstanceServiceImpl 원문입니다.

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java" -->
```java
        safeVariables.remove("initiator");
        safeVariables.remove("startUserId");
        safeVariables.remove("authenticatedUserId");
        safeVariables.remove("START_USER_ID");
        // 启动会同步执行首个节点，候选人解析必须在此时已能读取可信发起人。
        safeVariables.put("initiator", String.valueOf(effectiveUserId));
```

첫 노드에서 사람을 찾을 때 신뢰할 신청자를 읽을 수 있습니다. Flowable 인증 문맥은 finally에서 지워 같은 스레드의 다음 요청으로 신원이 이어지지 않게 합니다. 이는 시작 단계의 정리이며 이후 승인 변수의 모든 예약 필드까지 필터링했다는 뜻은 아닙니다.

## 이름만 기억하지 말고 명확한 버전을 쓴다

modelVersionId를 지정하면 같은 테넌트의 게시된 버전과 엔진 정의를 요구합니다. 일반 입구에는 processKey와 테넌트로 시작하는 호환 경로도 남아 있습니다. 이 경로가 모델 목록의 현재 게시 참조와 일치한다는 보장은 없습니다.

내부 업무 시작은 게시 상태를 다시 검사합니다. 승인 경로 저장 시 검사했다는 사실만으로 충분하지 않습니다. 자산 이전·자산 처분·공급업체 진입 승인에는 모델 분류와 업무 유형의 정확한 일치도 필요합니다.

## 멱등성: 네트워크 재시도도 같은 신청으로 인식한다

여기서 멱등성은 같은 업무 의도의 재전송이 다른 프로세스를 만들지 않는다는 뜻입니다. 내부 예약은 테넌트 안의 requestId, 그리고 businessType + businessKey를 각각 유일하게 제한합니다. 먼저 삽입하고 충돌하면 내용을 판단합니다.

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowProcessStartRequestServiceImpl.java" -->
```java
        if (requestMapper.insertIgnore(candidate) == 1) {
            return new Reservation(candidate, true, true);
        }
        return resolveConflict(candidate);
    }
```

중복이면 무조건 성공을 반환하는 것은 아닙니다. 업무 식별자·모델 버전·신청자를 비교하고 다르면 거부합니다. 제목·표시 이름·전체 변수의 완전한 해시는 비교하지 않습니다.

STARTED면 원래 인스턴스와 replayed=true를 반환하며 처리 중이면 409입니다. 같은 requestId의 FAILED는 다시 예약할 수 있습니다. requestId만 바꿔 같은 실패 업무 키를 만나도 재시도 권한이 자동으로 생기지 않습니다. 시간 초과 뒤에는 정식 조회로 상태를 확인하고 같은 의도·같은 requestId로 복구해야 합니다.

## “부서장”을 실제 사용자로 바꾸기

역할은 자격이고 조직은 찾을 범위이며, 기준 조직인 앵커는 이번에 어디서 찾기 시작할지를 정합니다. BPMN의 omni:assignment 확장에 규칙을 저장합니다. 예를 들면 다음 모양입니다.

```json
{
  "assignmentType": "SCOPED_ROLE",
  "roleCode": "SUPER_ADMIN",
  "anchorType": "START_USER_PRIMARY_UNIT",
  "anchorParams": {},
  "scopeMode": "SAME_UNIT",
  "fallbackStrategy": "ERROR",
  "approvalMode": "ALL"
}
```

모든 승인을 관리자에게 맡기라는 권장이나 계정·역할·조직을 만드는 예제가 아닙니다. 대상 테넌트에 실제 역할과 조직을 구성해야 합니다.

| 앵커 | 현재 검색 시작점 |
|---|---|
| START_USER_PRIMARY_UNIT | 신청자의 활성 주 조직 |
| PARENT_BY_TYPE, PARENT_CHILDREN | targetType으로 상위 검색. 유형이 없으면 주 조직 |
| CHILD_BY_CODE, SIBLING_BY_CODE | 코드로 직계 하위·같은 단계 조직 검색 |
| DEPT_BY_CODE | 테넌트 내 활성 조직을 코드로 검색 |
| ABSOLUTE_UNIT | 일반 해석은 unitIds 또는 unitId 사용 |
| PARENT | 주 조직의 상위 조직 |
| CHILD_UNIT, SIBLING_UNIT | 설정한 unitId를 직접 사용하며 신청자와의 관계를 자동 검증하지 않음 |

SAME_UNIT은 같은 조직, UNIT_AND_BELOW는 해당 조직과 하위, CHILDREN_ONLY는 직계 하위입니다. 알 수 없는 scopeMode는 현재 같은 조직 검색으로 돌아갑니다. 후보자 검색용 조직 범위이며 여섯 dataScope 데이터 권한 모드와 같지 않습니다.

## 사람 목록을 읽기 전에 해석한다

설계 화면은 노드의 start 실행 리스너를 등록하고 **다중 인스턴스가 후보 집합을 읽기 전에** 사람을 찾습니다. ScopedRoleAssignmentListener 원문입니다.

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java" -->
```java
            // 3. 将候选人列表写入流程变量，供多实例 collection 使用
            delegateExecution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));

            candidateResolutionService.initApprovalCounters(
                    delegateExecution, approvalMode, candidateUserIds.size());
```

집합 이름은 candidateUserIds, 각 사용자 변수는 userId이며 담당자도 userId를 참조합니다. 현재 활동의 다중 인스턴스 루트에서 집계를 초기화하고 루트 로컬 표식으로 반복 초기화나 이전 노드의 표 혼입을 막습니다.

이전 TaskListener 입구는 로그만 남기며 시작 리스너를 대신할 수 없습니다. 호환 집합 표현식은 해석 서비스를 공유하지만 이전 호환 처리기의 오류·대체 처리는 완전히 같지 않습니다. assignment나 roleCode가 없으면 바로 반환할 수도 있으므로 모든 이전 모델의 실패 방식이 같다고 보장하면 안 됩니다.

## 사람이 없다고 자동 승인하지 않는다

| 대체 전략 | 현재 동작 |
|---|---|
| ERROR, 기본값, 알 수 없는 전략 | 예외 발생 |
| ASSIGN_ADMIN | 현재 테넌트의 조직 ID 1에서 SUPER_ADMIN 후보 검색. 사용자 1 고정이 아니며 없으면 실패 |
| ESCALATE_PARENT | 기준 조직의 상위부터 최대 20단계 검색. 첫 결과를 쓰고 끝까지 없으면 실패 |

상위 검색의 ABSOLUTE_UNIT은 단일 unitId만 읽고 unitIds의 첫 항목을 자동 선택하지 않습니다. 조직 누락·비활성 사용자·역할과 범위 불일치·조회 오류로 후보가 비어질 수 있습니다. 올바른 설정과 정상 데이터를 보완한 뒤 업무 재시도 규칙에 따라 복구하고 자동 승인으로 우회하지 않습니다.

설정을 바꾸면 새 모델 버전을 게시합니다. 별도 실행 시 후보 설정 테이블로 모든 진행 중 신청을 자동 변경하는 기능은 없습니다. 다음은 [작업 동작과 다중 승인](workflow-tasks.kr.md)입니다.
