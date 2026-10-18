# 워크플로 개발과 모델링

`workflow`, `full` 또는 `omni-workflow`를 포함하는 프리셋이 대상입니다. 먼저 [워크플로 사용 안내](../guides/workflow-approval.kr.md)와 Docker 환경을 준비합니다. 현재 상태 머신, 테넌트, 게시 잠금과 실패 의미는 [워크플로 사실 문서](../workflow.kr.md)와 소스를 따릅니다.

모델 작업에는 작업별 `workflow:model:list`, `workflow:model:create`, `workflow:model:update`, `workflow:model:validate`, `workflow:model:publish` 권한이 필요합니다. 로그인만으로 모델을 관리할 수는 없습니다. 테넌트 내 고유한 `modelKey`를 선택해 BPMN `<process id>`와 맞추고, 직접 만든 연습 데이터만 참조가 없음을 확인한 뒤 정리합니다.

## 4. 확장 안내

### 4.1 승인 프로세스 유형 추가

1. 각 UserTask에 `<omni:assignment>`를 설정하고 지원되는 역할, 앵커와 범위로 BPMN XML을 설계합니다.
2. 모델 생성: `POST /api/workflow/model`.
3. XML 저장: `PUT /api/workflow/model/{id}/draft`.
4. 현재 초안 검증: `POST /api/workflow/model/{id}/validate`. 오류와 경고를 확인합니다.
5. 게시: `POST /api/workflow/model/{id}/publish`. 게시 내부에서도 다시 검증합니다.

[BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java)는 XML, 프로세스 ID, 노드와 필수 확장 존재를 검사하지만 assignment JSON 필드 의미를 완전히 검증하지는 않습니다. 검증 후에도 사용 안내에 따라 실제로 시작·승인하고 후보자, 실패 분기와 결과를 확인합니다. 기존 기능을 조합할 때는 보통 코드 수정이 필요 없지만 새 앵커나 전략에는 다음 확장이 필요합니다.

### 4.2 앵커 유형 추가

1. [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java)의 `doResolveCandidates()/resolveAnchorUnitId()`에서 새 앵커와 테넌트 조건이 있는 조직 쿼리를 구현합니다.
2. [ScopedRoleAssignmentListener](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java), `CandidateResolverDelegate`, `CandidateResolverBean`의 호출 경계를 확인합니다. 리스너만 고치면 공유 후보자 해석을 놓칩니다.
3. [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue)의 선택값, 매개변수, XML 저장·재읽기를 수정하고 [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts)를 확인합니다.
4. 추가 필드 검증은 명시적으로 구현하고 테스트합니다. [BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java)에 완전한 앵커 허용 목록이나 JSON Schema 검증이 있다고 가정하지 않습니다.

### 4.3 폴백 전략 추가

1. [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java)의 `handleFallback()`, `escalateToParentForCandidates()`를 확인한 다음 엔진 진입점에 통합합니다.
2. 현재 `ASSIGN_ADMIN`은 현재 테넌트의 조직 단위 `1`에서 `SUPER_ADMIN` 후보자를 찾고 없으면 실패합니다. `ESCALATE_PARENT`는 상위 조직에서 같은 역할과 범위로 다시 찾으며 끝까지 비어 있으면 실패해야 합니다.
3. 상위 탐색 분기는 ExecutionListener와 `CandidateResolverBean`에 있습니다. 현재 `CandidateResolverDelegate`는 `handleFallback()`을 직접 호출하며 바깥 catch에서는 예외를 로그에만 남깁니다. 세 경로를 같게 취급하지 말고 사용하는 진입점의 실제 실패 결과를 확인합니다.
4. 프런트엔드 전략 선택값을 수정하고 세 진입점 각각의 후보자 존재/부재, 테넌트 격리, 의존 서비스 실패를 테스트합니다.

### 4.4 알림 서비스 구현

현재 [WorkflowNotificationService](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java)의 대기 작업 알림 메서드는 `processInstanceId`를 포함한 네 개의 매개변수를 받습니다.

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java" -->
```java
void notifyPendingTask(String userId, String taskId, String processInstanceId, String title);
```

다음 구현을 `omni-workflow`의 `com.omni.workflow.notification` 패키지에 두면 컴포넌트 스캔으로 등록됩니다. 이벤트만 기록하며 외부 알림을 보내지 않습니다. 실제 전달, 실패 처리와 재시도는 업무 구현으로 추가합니다.

```java
package com.omni.workflow.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.common.workflow.notification.WorkflowNotificationService;

/** 演示通知 SPI 的完整签名；只记录事件，不发送外部通知。 */
@Slf4j
@Service
public class MyNotificationService implements WorkflowNotificationService {

    /** 记录待办通知事件，真实投递由业务实现。 */
    @Override
    public void notifyPendingTask(String userId, String taskId,
                                  String processInstanceId, String title) {
        log.debug("待办通知事件：taskId={}, processInstanceId={}", taskId, processInstanceId);
    }

    /** 记录任务通知清理事件。 */
    @Override
    public void clearPendingTask(String taskId) {
        log.debug("清理待办通知事件：taskId={}", taskId);
    }
}
```

[FlowableAutoConfiguration](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/config/FlowableAutoConfiguration.java)은 `@ConditionalOnMissingBean`으로 `NoOpNotificationService`를 제공합니다. 직접 `WorkflowNotificationService` Bean을 등록한 뒤 조건을 확인합니다. 빈 구현이나 로그만으로 전달 성공을 판단하지 않습니다.

### 4.5 CC 알림

ServiceTask에 `flowable:delegateExpression="${ccNotifyDelegate}"`와 `<omni:cc>`를 설정합니다. [CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java)는 사용자 ID 또는 역할·조직으로 수신자를 찾고 `wf_cc_record`에 저장합니다. CC는 승인 대기 목록에 들어가지 않으며 대기 알림 SPI도 호출하지 않습니다.

이 JavaDelegate는 동기적으로 실행됩니다. 현재 구현은 예외를 잡아 로그로 남깁니다. CC 기록이 없으면 설정, SQL 로그와 트랜잭션 결과를 확인합니다. 프로세스가 계속된다는 사실만으로 CC 성공이나 비동기 전달이 보장되지는 않습니다.

## 5. 현재 엔진과 설계 경계

엔진 의존성은 [Maven POM](../../omni-backend/pom.xml)의 `flowable.version`과 [공유 Starter POM](../../omni-backend/omni-common-workflow/pom.xml)에 정의됩니다. 자동 구성, 테넌트 연결과 승인 SPI는 [워크플로 사실 문서](../workflow.kr.md), 디자이너 의존성은 [프런트엔드 package.json](../../omni-frontend/package.json)을 참조합니다. 업그레이드나 엔진 교체 시 통합 지점, 게시 모델과 다중 인스턴스 회귀를 확인하고 설계 선택은 ADR에 기록합니다.

## 6. BPMN 모델링 규칙

### 명명 규칙

| 요소 | 규칙 | 예시 |
|---|---|---|
| Process ID | `model_key`와 일치 | `leave-request`, `expense-approval` |
| UserTask ID | 역할·동작을 kebab-case로 표시 | `dept-leader-approve`, `hr-review` |
| SequenceFlow ID | `flow-{source}-{target}` | `flow-start-submit` |
| Gateway ID | `{type}-gw-{purpose}` | `exclusive-gw-amount`, `parallel-gw-notify` |

### 모델링 원칙

1. 각 UserTask에 `<omni:assignment>`를 설정해 후보자를 동적으로 찾고 개인 계정을 고정하지 않습니다.
2. ExclusiveGateway에 조건 없는 default flow를 두어 다른 조건이 맞지 않을 때 사용합니다.
3. 다중 인스턴스 완료 조건은 다음 식을 사용합니다. Markdown 이스케이프 없이 `||`를 그대로 복사할 수 있습니다. `${rejectedCount > 0 || approvedCount >= requiredApprovals}`
4. CC는 앞서 설명한 ServiceTask 델리게이트를 사용하고 실제 기록으로 결과를 확인합니다.
5. 게시 전에 `BpmnXmlValidator` 결과를 확인하고 승인, 거절, 후보자 부재 분기를 실제로 검증합니다.

### 디자이너 소스 진입점

| 파일 | 역할 |
|---|---|
| [useBpmnModeler.ts](../../omni-frontend/src/composables/useBpmnModeler.ts) | Modeler 생성과 해제 |
| [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts) | BPMN 확장 읽기·쓰기 |
| [bpmnContextPadI18n.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadI18n.ts) | 컨텍스트 메뉴 다국어 처리 |
| [bpmnContextPadProvider.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadProvider.ts) | 컨텍스트 메뉴 확장 |
| [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue) | 역할·앵커·범위·폴백 매개변수 |
| [ServiceTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/ServiceTaskPanel.vue) | CC 매개변수 |
| [GatewayPanel.vue](../../omni-frontend/src/components/workflow/panels/GatewayPanel.vue) | 게이트웨이 조건 |

## 7. 문제 해결

| 증상 | 확인할 진입점과 결과 |
|---|---|
| 모델 게시 실패 | validate 오류, [BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java)와 현재 초안을 확인합니다. |
| 후보자 부재 | [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java)에서 roleCode, 앵커, scopeMode, 테넌트와 활성 상태를 추적하고 실제 폴백 분기를 확인합니다. |
| 인스턴스 시작 실패 | 사용 안내에 따라 게시 버전, 권한, 응답을 확인하고 `wf_process_model_version.status=PUBLISHED`를 대조합니다. |
| 다중 승인 미완료 | 프로세스 변수만 보지 말고 해당 MI 활동 루트의 `approvedCount/rejectedCount/requiredApprovals`와 completionCondition을 확인합니다. |
| 예상과 다른 deleteReason | [워크플로 사실 문서](../workflow.kr.md) §3.1을 참조합니다. `MI_END` 자동 종료와 `deleted` 종료 처리는 다릅니다. |
| 테넌트 격리 이상 | 신뢰하는 게이트웨이에서 인증된 요청과 `TenantInfoFilter/TenantInfoHolder` 설정·정리를 확인합니다. 클라이언트가 직접 넣은 헤더만으로 신원을 신뢰하지 않습니다. |
| CC 기록 부재 | [CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java)의 설정 해석, SQL과 예외 로그를 확인합니다. 프로세스 진행만으로 CC 성공을 증명하지 않습니다. |
| 디자이너 로드 실패 | package.json과 잠금 파일에 맞춰 의존성을 설치하고 컨테이너 프런트엔드와 브라우저 콘솔을 확인합니다. |

소스를 확장한 뒤 로컬 Docker의 Maven Wrapper로 백엔드를 검증합니다. 디자이너를 수정했다면 프런트엔드 build/lint도 실행합니다. 전체 작업, 예상 결과와 연습 정리는 [워크플로 사용 안내](../guides/workflow-approval.kr.md)를 따릅니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
```
