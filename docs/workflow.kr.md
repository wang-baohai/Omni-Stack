# 워크플로우 엔진

> 본 문서는 Omni-Stack 워크플로우 엔진의 아키텍처, 핵심 플로우, 제약 사항 및 확장 가이드를 다룹니다.  
> 아키텍처 개요는 [architecture.kr.md](architecture.kr.md)를 참조하십시오. Docker 배포 구성은 [docker-deployment.kr.md](docker-deployment.kr.md)를 참조하십시오.

Omni-Stack은 **Flowable 8.x** 기반의 시각적 BPMN 워크플로우 엔진을 제공하며, 모델 설계, 이중 버전 관리, 다중 인스턴스 합의 승인 및 엔드투엔드 프로세스 추적을 지원합니다.

## 1. Architecture Overview

워크플로우 시스템은 독립 실행형 마이크로서비스와 공유 스타터 라이브러리로 구성됩니다:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Workflow Engine                                  │
├──────────────────────────────────────────────────────────────────────────┤
│                      omni-workflow (port 8103)                            │
│  ─────────────────────────────────────────────────────────────────────   │
│  Controllers (7): WorkflowModel · ProcessDefinition · ProcessInstance    │
│                   Approval · Task · WorkflowStats · WorkflowIdentity     │
│  Services (8): WorkflowModel · ProcessDefinition · ProcessInstance       │
│                WorkflowApproval · WorkflowTask · WorkflowStats            │
│                WorkflowIdentity · WorkflowTodoSync                       │
│  Delegates:  ScopedRoleAssignmentListener · CandidateResolverDelegate    │
│              CandidateResolverBean · CcNotifyDelegate                    │
│  Engine:     BpmnXmlBuilder · BpmnXmlValidator                           │
├──────────────────────────────────────────────────────────────────────────┤
│                  omni-common-workflow (shared starter)                    │
│  FlowableAutoConfiguration · ApprovalService(Impl) · UserGroupLookup     │
│  WorkflowNotificationService · TenantInfoFilter · TenantInfoHolder       │
├──────────────────────────────────────────────────────────────────────────┤
│                        Flowable BPMN Engine 7.x                           │
│  repositoryService · runtimeService · taskService · historyService       │
├──────────────────────────────────────────────────────────────────────────┤
│                       omni_workflow (MySQL)                               │
│  wf_process_model · wf_process_model_version · wf_process_instance_ext   │
│  wf_todo_task · wf_cc_record · wf_form_schema · wf_delegation_rule      │
└──────────────────────────────────────────────────────────────────────────┘
```

**모듈 의존성**:

- `omni-common-core` — POJOs (`R<T>`, `PageResult`), XSS SPI 인터페이스
- `omni-common-mybatis` — MyBatis-Plus + MySQL 드라이버 + 테넌트 인터셉터
- `omni-common-redis` — XSS 구성 및 세션 데이터용 Redis 캐시
- `omni-common-workflow` — Flowable 자동 구성, 승인 SPI, 테넌트 필터, 알림 SPI
- `omni-workflow` — 비즈니스 레이어: 컨트롤러, 서비스, 델리게이트, BPMN 엔진 도구

**주요 설계 결정**:

- **Flowable**을 BPMN 엔진으로 채택: 오픈소스, 성숙한 Spring Boot 통합, 네이티브 다중 인스턴스(MI) 지원
- **이중 버전 관리**: 비즈니스 버전은 `wf_process_model_version`에서 추적(DRAFT → PUBLISHED → ARCHIVED), 엔진 버전은 Flowable 배포에서 관리
- **비주얼 디자이너**: 프론트엔드 BPMN 모델러가 디자이너 JSON을 생성하고, `BpmnXmlBuilder`가 BPMN 2.0 XML로 변환
- **동적 후보자 해결**: `omni:assignment` JSON 확장 요소가 작업 시작 시 `ScopedRoleAssignmentListener`에 의해 파싱되며, 하드코딩된 담당자는 없습니다

### 데이터 모델

```mermaid
erDiagram
    wf_process_model ||--o{ wf_process_model_version : "1:N versions"
    wf_process_model_version ||--o{ wf_process_instance_ext : "1:N instances"
    wf_process_instance_ext ||--o{ wf_todo_task : "1:N todos"
    wf_process_instance_ext ||--o{ wf_cc_record : "1:N cc"
```

### 데이터베이스 테이블 (omni_workflow)

| 테이블 | 용도 |
|-------|---------|
| `wf_process_model` | 프로세스 모델 레지스트리, `model_key`는 테넌트별 고유 |
| `wf_process_model_version` | 버전 이력: BPMN XML, 디자이너 JSON, 배포 정보 |
| `wf_process_instance_ext` | 인스턴스 확장: Flowable 인스턴스와 모델 버전 연결 |
| `wf_todo_task` | 담당자 범위 고속 쿼리용 대기 작업 캐시 |
| `wf_cc_record` | 읽음 상태가 포함된 CC 알림 레코드 |
| `wf_form_schema` | JSON Schema 폼 정의 |
| `wf_delegation_rule` | 승인 위임 규칙(사용자 간, 선택적 프로세스 범위) |

---

## 2. Core Flow Walkthrough

### 2.1 모델 생성

```
POST /api/workflow/model  (workflow:model:create)
```

1. `WorkflowModelController.createModel(CreateModelRequest)` → `WorkflowModelService.createModel()`
2. `model_key`(테넌트별 고유)를 가진 `wf_process_model` 행 생성
3. `status = DRAFT`인 초기 `wf_process_model_version` 행 생성
4. `wf_process_model.current_draft_version_id`를 새 버전에 연결

### 2.2 초안 저장 (비주얼 디자이너)

```
PUT /api/workflow/model/{id}/draft  (workflow:model:update)
```

1. `WorkflowModelController.saveDraft(id, SaveDraftRequest)` → `WorkflowModelService.saveDraft()`
2. 초안 버전의 `designer_json`를 업데이트하고, `BpmnXmlBuilder.build()`로 `bpmn_xml`를 재생성
3. 변경 감지를 위해 `xml_sha256` 계산
4. 요청에서 모델명과 카테고리를 동기화

**BpmnXmlBuilder**는 디자이너 JSON 노드를 BPMN 2.0 XML 요소로 변환합니다:

| 디자이너 노드 유형 | BPMN 요소 | 확장 |
|---|---|---|
| `StartEvent` | `<startEvent>` | — |
| `EndEvent` | `<endEvent>` | — |
| `UserTask` | `<userTask>` | `<omni:assignment>` + `flowable:executionListener` |
| `ServiceTask` (CC) | `<serviceTask>` | `<omni:cc>` + `flowable:delegateExpression` |
| `ExclusiveGateway` | `<exclusiveGateway>` | `default` 속성 |
| `ParallelGateway` | `<parallelGateway>` | — |

### 2.3 모델 검증

```
POST /api/workflow/model/{id}/validate  (workflow:model:validate)
```

`BpmnXmlValidator.validate()` 검증 항목:
1. XML 정형성(XXE 보호 포함)
2. 실행 가능한 `<process>`가 정확히 하나이며, id가 `model_key`와 일치
3. 최소 하나의 `StartEvent`과 하나의 `EndEvent` 존재
4. 모든 `UserTask`에 `<omni:assignment>` 확장이 존재
5. CC `ServiceTask`에 `<omni:cc>` 확장이 존재
6. `ExclusiveGateway`에 `default` 플로우(`conditionExpression` 없음)가 존재
7. 모든 `SequenceFlow`가 유효한 source/target을 참조

### 2.4 모델 게시

```
POST /api/workflow/model/{id}/publish  (workflow:model:publish)
```

1. `SELECT FOR UPDATE`로 모델 행에 비관적 잠금 획득
2. `BpmnXmlValidator`로 BPMN XML 검증
3. `targetNamespace`를 모델 카테고리로 교체
4. Flowable에 배포: `repositoryService.createDeployment().addString(bpmnXml).deploy()`
5. 비즈니스 버전 번호 계산(`max(existing) + 1`)
6. 버전 레코드 업데이트: `status = PUBLISHED`, `deploymentId`, `processDefinitionId`, `engineVersion`
7. 이전 PUBLISHED 버전을 아카이브(`status = ARCHIVED`)
8. 모델의 `current_published_version_id` 업데이트

### 2.5 프로세스 인스턴스 시작

```
POST /api/workflow/process-instance/start  (workflow:instance:start)
```

1. `ProcessInstanceController.start(StartProcessRequest)` → `ProcessInstanceService.start()`
2. 최신 PUBLISHED 버전을 해결하여 `processDefinitionId` 획득
3. Flowable 인스턴스 시작: `runtimeService.startProcessInstanceById(processDefinitionId, businessKey, variables)`
4. 모델, 버전, Flowable 인스턴스를 연결하는 `wf_process_instance_ext` 행 생성
5. `ScopedRoleAssignmentListener`가 각 UserTask 시작 이벤트에서 발동하여 후보자를 해결

### 2.6 승인 완료

```
POST /api/workflow/approval/{taskId}/complete  (workflow:approval:complete)
```

1. `ApprovalController.complete(taskId, ApprovalRequest)` → `WorkflowApprovalService.complete()`
2. 프로세스 변수 설정: `approved = true/false`, `comment = "..."`
3. `taskService.complete(taskId, variables)` 호출
4. `ApprovalServiceImpl`이 MI 카운터를 업데이트(`approvedCount` / `rejectedCount`)
5. MI `completionCondition` 평가: `${rejectedCount > 0 || approvedCount >= requiredApprovals}`
6. 조건 충족 시 → 나머지 MI 인스턴스가 스킵됨(deleteReason = `MI_END`)

### 2.7 진행 상황 및 기록

**진행 상황** (`GET /{id}/progress`):
- 모든 활동에 대해 `HistoricActivityInstance` 쿼리
- `activityId`로 집계(MI 하위 인스턴스 중복 제거)
- 대기 중인 UserTask에 대해 `CandidateResolverBean`으로 후보자를 사전 해결
- 담당자별 상태를 포함한 `List<ActivityInfo>`를 가진 `ProcessProgressResponse` 반환

**승인 기록** (`GET /{id}/approval-records`):
- `HistoricTaskInstance` 쿼리(생성 시간 오름차순)
- 작업별 결과 판정: `approved` / `rejected` / `auto-approved`(MI_END) / `cancelled` / `pending`
- 승인 의견의 `Comment`와 승인/거부 구분을 위한 `approved` 변수 조회

---

## 3. Constraints & Pitfalls

### 3.1 MI DeleteReason

다중 인스턴스 `completionCondition`가 트리거되면, 나머지 작업은 Flowable에 의해 `deleteReason = "MI_END"`로 삭제됩니다 — `"deleted"`가 **아닙니다**. `"deleted"` 이유는 전체 프로세스 인스턴스가 종료되거나 거부된 경우에 사용됩니다.

**규칙**: 스킵과 취소 판정에는 반드시 `HistoricTaskInstance.getDeleteReason()`을 사용하십시오:

| `deleteReason` | 의미 | 결과 |
|---|---|---|
| `null` | 작업이 정상 완료됨 | `approved` 변수 확인 → 승인 / 거부 |
| `MI_END` | MI completionCondition에 의해 스킵됨 | 자동 승인 |
| `deleted` | 프로세스 종료 / 거부 | 취소됨 |

**주의사항**: `HistoricActivityInstance` 부모 조회에 의존하지 마십시오. 여러 행이 동일한 `ACT_ID_`를 공유할 수 있습니다(하나는 `NULL` deleteReason, 다른 하나는 `MI_END`). `putIfAbsent`가 잘못된 행을 저장할 수 있습니다. 작업 수준의 `deleteReason`을 직접 사용하십시오.

### 3.2 omni:assignment 확장 요소

`omni:assignment` JSON은 후보자 해결을 위한 **유일한 구성 항목**입니다:

```xml
<userTask id="dept-leader-approve" flowable:assignee="${userId}">
  <extensionElements>
    <flowable:executionListener event="start"
        delegateExpression="${scopedRoleAssignmentListener}" />
    <omni:assignment>{
      "roleCode": "DEPT_LEADER",
      "anchorType": "PARENT",
      "anchorParams": {},
      "scopeMode": "SAME_UNIT",
      "fallbackStrategy": "ERROR",
      "approvalMode": "ANY"
    }</omni:assignment>
  </extensionElements>
  <multiInstanceLoopCharacteristics isSequential="false"
      flowable:collection="candidateUserIds"
      flowable:elementVariable="userId">
    <completionCondition>${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
  </multiInstanceLoopCharacteristics>
</userTask>
```

**필드**:

| 필드 | 값 | 설명 |
|---|---|---|
| `roleCode` | 임의의 역할 코드(예: `TEAM_LEADER`, `DEPT_LEADER`) | 해결 대상 역할 |
| `anchorType` | `START_USER_PRIMARY_UNIT`, `PARENT`, `ABSOLUTE_UNIT`, `PARENT_BY_TYPE`, `CHILD_BY_CODE`, `SIBLING_BY_CODE`, `PARENT_CHILDREN`, `DEPT_BY_CODE`, `CHILD_UNIT`, `SIBLING_UNIT` | 앵커 조직 단위 찾는 방법 |
| `anchorParams` | JSON 객체(예: `{"unitIds": [200]}`) | 앵커 해결 파라미터 |
| `scopeMode` | `SAME_UNIT`, `UNIT_AND_BELOW`, `CHILDREN_ONLY` | 후보자 검색 범위 |
| `fallbackStrategy` | `ERROR`, `ASSIGN_ADMIN`, `ESCALATE_PARENT` | 후보자를 찾지 못한 경우의 동작 |
| `approvalMode` | `ALL`(기본값), `ANY` | MI 합의 모드 |

### 3.3 승인 모드

- **ALL**: 모든 후보자가 승인해야 합니다. `requiredApprovals = candidateUserIds.size()`. `approvedCount >= requiredApprovals`일 때 플로우가 진행됩니다.
- **ANY**: 단일 승인으로 충분합니다. `requiredApprovals = 1`. 첫 번째 승인 시 플로우가 진행되며, 나머지 작업은 `deleteReason = MI_END`로 자동 완료됩니다.

두 모드 모두 동일한 `completionCondition` 수식을 공유합니다: `${rejectedCount > 0 || approvedCount >= requiredApprovals}`. 차이는 `ScopedRoleAssignmentListener`가 설정하는 `requiredApprovals` 값에 있습니다.

**거부 단축**: 두 모드 모두 단일 거부(`rejectedCount > 0`)가 즉시 거부 분기를 트리거하여 나머지 승인자를 스킵합니다.

### 3.4 테넌트 격리

`omni-workflow`의 `MybatisPlusConfig`는 `TenantLineInnerInterceptor`를 등록하며:
- `TenantInfoHolder`에서 테넌트 ID를 읽음(`TenantInfoFilter`가 `X-Tenant-Id` 헤더에서 설정)
- Flowable 내부 테이블(`ACT_*` / `act_*` 접두사)을 테넌트 필터링에서 **제외**

Flowable 테이블은 MyBatis-Plus 인터셉션이 아닌 Flowable의 내장 `tenantId` 메커니즘을 통해 테넌트 격리됩니다.

### 3.5 XSS 통합

`omni-workflow`는 `XssConfigProviderImpl`을 통해 `XssConfigProvider` SPI를 구현합니다:
- Redis 캐시에서 XSS 구성을 읽음(`xss:enabled:{tenantId}`, `xss:rules:{tenantId}`)
- 캐시는 `omni-auth` 서비스에 의해 기록되며, 워크플로우 서비스는 **읽기 전용 소비자**입니다
- 캐시 미스 시 `enabled = false`를 반환(페일오픈)

### 3.6 후보자 해결 컴포넌트

| 컴포넌트 | Bean 이름 | 트리거 |
|---|---|---|
| `ScopedRoleAssignmentListener` | `scopedRoleAssignmentListener` | UserTask `start` 이벤트의 ExecutionListener |
| `CandidateResolverDelegate` | `candidateResolverDelegate` | UserTask 이전 ServiceTask의 JavaDelegate |
| `CandidateResolverBean` | `candidateResolver` | UEL 표현식 또는 오프라인 사전 해결 |

`CandidateResolverBean`은 `resolveCandidates(processDefinitionId, activityId, startUserId, tenantId)`를 오프라인 사용용으로 노출합니다(예: `getProgress()`에서 대기 중인 작업의 승인 예정자를 표시해야 하는 경우).

### 3.7 게시 잠금

`publishModel()`은 `wf_process_model`에 `SELECT FOR UPDATE` 비관적 잠금을 사용하여 동일한 모델의 동시 배포를 방지합니다. 이는 Flowable 배포가 원자적이지 않고 여러 엔진 API 호출을 수반하기 때문에 중요합니다.

---

## 4. Extension Guide

### 4.1 새 승인 프로세스 유형 추가

1. 각 UserTask에 `<omni:assignment>`를 설정한 BPMN XML 설계
2. `BpmnXmlValidator`로 검증(필수 확장 강제)
3. API를 통해 모델 생성: `POST /api/workflow/model`
4. BPMN XML 저장: `PUT /api/workflow/model/{id}/draft`
5. 게시: `POST /api/workflow/model/{id}/publish`

코드 변경이 필요 없습니다 — 프레임워크는 BPMN XML + `omni:assignment` 구성을 통한 데이터 기반입니다.

### 4.2 새 앵커 유형 추가

1. `ScopedRoleAssignmentListener`의 해결 로직에 새 앵커 유형 문자열 추가
2. 조직 단위 조회 쿼리 구현(예: 특정 조건으로 `sys_org_unit` 쿼리)
3. 검증이 필요한 경우 `BpmnXmlValidator`의 알려진 값에 앵커 유형 추가
4. 프론트엔드 `UserTaskPanel.vue`를 업데이트하여 속성 패널에 새 앵커 유형 노출

### 4.3 새 폴백 전략 추가

1. `ScopedRoleAssignmentListener`에 전략 상수 추가
2. 폴백 동작 구현(예: `ASSIGN_ADMIN` → 관리자 사용자 조회, `ESCALATE_PARENT` → 상위 단위 후보자 검색)
3. `omni:assignment` JSON 스키마 검증 업데이트

### 4.4 커스텀 알림 서비스

`omni-common-workflow`의 `WorkflowNotificationService` 인터페이스를 구현합니다:

```java
@Service
public class MyNotificationService implements WorkflowNotificationService {
    @Override
    public void notifyPendingTask(String assigneeId, String taskId, String title) { ... }

    @Override
    public void clearPendingTask(String taskId) { ... }
}
```

기본 `NoOpNotificationService`(`FlowableAutoConfiguration`에 의해 등록됨)는 `@ConditionalOnMissingBean`을 통해 사용자 구현으로 대체됩니다.

### 4.5 CC(참조) 알림

BPMN 디자이너에서 `ccNotifyDelegate` 델리게이트 표현식을 가진 `ServiceTask` 노드를 추가하십시오. `<omni:cc>` 확장 요소에 대상 사용자 ID 또는 역할 기반 해결을 구성하십시오. `CcNotifyDelegate`가 실행 시 `wf_cc_record` 항목을 생성합니다.

---

## 5. 기술 선정: Flowable 8.x를 선택한 이유

| 고려 사항 | Flowable | Camunda | Activiti |
|------|---------|---------|----------|
| **오픈소스 라이선스** | Apache 2.0(상업 친화적) | 상업 버전은 라이선스 필요(커뮤니티 버전 MIT) | Apache 2.0 |
| **Spring Boot 통합** | 네이티브 Spring Boot Starter, 자동 구성 | Spring Boot Starter 추가 구성 필요 | 유지보수 중단(Flowable은 이 포크) |
| **다중 인스턴스 지원** | 네이티브 MI(Multi-Instance) 지원, 유연한 completionCondition | 유사 기능 | 기본 MI 지원 |
| **CMMN/DMN** | BPMN + CMMN + DMN 지원 | BPMN + DMN 지원(CMMN은 상업 버전) | BPMN만 지원 |
| **커뮤니티 활성도** | 활발(GitHub 8k+ stars) | 활발(상업 지원) | 유지보수 중단 |
| **버전 7.x** | Jakarta EE 호환, Spring Boot 3/4 지원 | 버전 8은 대규모 아키텍처 변경 | 새 버전 없음 |

**결론**: Flowable 8.x는 오픈소스 라이선스, Spring Boot 네이티브 통합, 다중 인스턴스 지원 측면에서 명확한 우위를 가지며, Omni-Stack 워크플로우 엔진의 최적 선택입니다.

## 6. BPMN 모델링 모범 사례

### 명명 규칙

| 요소 | 명명 규칙 | 예시 |
|------|---------|------|
| Process ID | `model_key`와 일치 | `leave-request`, `expense-approval` |
| UserTask ID | kebab-case, 역할+동작 설명 | `dept-leader-approve`, `hr-review` |
| SequenceFlow ID | `flow-{source}-{target}` | `flow-start-submit` |
| Gateway ID | `{type}-gw-{purpose}` | `exclusive-gw-amount`, `parallel-gw-notify` |

### 모델링 원칙

1. **모든 UserTask에 `<omni:assignment>`를 구성해야 합니다**: 동적 후보자 해결, `assignee` 하드코딩 금지
2. **ExclusiveGateway에는 반드시 default flow를 설정하십시오**: 무조건 분기를 폴백으로 설정하여 프로세스 데드락 방지
3. **다중 인스턴스 합의에 통일된 completionCondition 사용**: `${rejectedCount > 0 \|\| approvedCount >= requiredApprovals}`
4. **CC 알림은 ServiceTask + `ccNotifyDelegate` 사용**: 비상쇄, 메인 플로우에 영향 없음
5. **모델 게시 전 반드시 `BpmnXmlValidator`를 통과하십시오**: XML 유효성과 확장 요소 완전성 보장

### 프로세스 디자이너 프론트엔드 아키텍처

```
bpmn-js Modeler (오픈소스 BPMN 2.0 모델링 도구)
    │
    ├── useBpmnModeler.ts      — Modeler 생성/파괴 라이프사이클
    ├── useBpmnExtension.ts    — omni:assignment 등 확장 요소 읽기/쓰기
    ├── bpmnContextPadI18n.ts  — 컨텍스트 메뉴 국제화
    └── bpmnContextPadProvider.ts — 커스텀 컨텍스트 메뉴 항목

속성 패널 (panels/)
    ├── UserTaskPanel.vue      — 역할 해결 구성(roleCode, anchorType, scopeMode)
    ├── ServiceTaskPanel.vue   — CC 알림 구성
    └── GatewayPanel.vue       — 게이트웨이 조건 구성
```

## 7. 문제 해결 가이드

| 문제 | 가능한 원인 | 문제 해결 방법 |
|------|---------|----------|
| **모델 게시 실패** | BPMN XML 검증 실패 | `POST /api/workflow/model/{id}/validate`를 호출하여 구체적인 오류 정보 확인 |
| **후보자 해결 실패** | `omni:assignment` 구성 오류 | `roleCode`, `anchorType`, `scopeMode` 값이 유효한지 확인; 서비스 로그에서 예외 확인 |
| **프로세스 인스턴스가 시작되지 않음** | 모델이 게시되지 않았거나 버전이 아카이브됨 | `wf_process_model_version` 테이블에서 `status=PUBLISHED` 버전이 있는지 확인 |
| **다중 인스턴스 작업이 스킵되지 않음** | completionCondition가 트리거되지 않음 | `approvedCount`, `rejectedCount`, `requiredApprovals` 변수 값 확인 |
| **deleteReason이 잘못 표시됨** | MI_END vs deleted 혼동 | §3.1 MI DeleteReason 테이블 참조; `MI_END` = 자동 완료, `deleted` = 프로세스 종료 |
| **테넌트 격리가 작동하지 않음** | TenantInfoHolder가 설정되지 않음 | Gateway가 `X-Tenant-Id` 요청 헤더를 주입했는지 확인; `TenantInfoFilter`가 정상 실행되는지 확인 |
| **BPMN 디자이너를 로드할 수 없음** | bpmn-js 버전 비호환 | `bpmn-js` 버전이 18.x인지 확인; 브라우저 콘솔 오류 확인 |
