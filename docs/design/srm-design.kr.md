# SRM 공급업체 관리 아키텍처 및 구현 기준

> 기술 번역 초안이며 업무 용어는 사람의 검토가 필요합니다. code와 상태 값은 변경하지 않습니다.

## 1. 범위와 경계

`omni-srm`은 Supplier master, 입점, lifecycle, evaluation, risk, invitation과 portal-company association을 소유합니다. Auth는 user/role/tenant/DataScope, Workflow는 승인 runtime, Procurement는 RFQ/order, Asset은 자산 원장을 소유합니다. SRM은 다른 서비스 DB를 읽지 않고 Flowable을 포함하지 않습니다.

MVP는 초대, Portal 등록·입주, 승인, active/suspend/blacklist, 성과 평가, risk indicator, Supplier 360과 견적 입력 연계를 포함합니다. 계약, 정산과 고급 category 전략은 범위 밖입니다.

## 2. 도메인과 lifecycle

Supplier, Invitation, Portal Association, Evaluation, Risk Indicator/Record와 Inbox Event를 명시적으로 관리하고 모든 `srm_*` 테이블을 tenant로 분리합니다. 자식 리소스는 Supplier/Evaluation root에서 가시성을 상속하며 존재하지 않는 owner 열을 자식 테이블에 추가하면 안 됩니다.

상태 전이는 server command입니다. 입점과 활성화는 데이터와 승인 결과를 검증하고 중지·재개·blacklist는 감사 증거를 보존합니다. 평가 기준은 version 관리하며 가중 점수를 결정적으로 계산합니다. risk level은 활성 indicator에서 계산하고 임의 편집하지 않습니다.

## 3. Portal 입주와 Saga

기본 `USER`는 enroll만 할 수 있습니다. `srm:portal:profile` / `srm:portal:evaluation`에는 `SUPPLIER` role과 active `srm_supplier_portal_user` 연결이 필요합니다. enrollment에는 `inviteToken`과 client `requestId`가 필수이며 requestId는 멱등 key입니다.

SRM은 local association request와 Outbox를 먼저 commit하고 Auth가 role assignment를 멱등 처리하여 result event를 보내며 SRM이 Inbox를 통해 Saga를 확정합니다. 실패 시 검증되지 않은 active association을 만들지 않습니다. Portal user ID를 내부 `owner_user_id/owner_unit_id`에 기록하지 않습니다.

## 4. Security와 DataScope

Gateway identity → tenant filter → functional permission → permission-aware DataScope → tenant/scope interceptor → AccessGuard 순서로 fail closed합니다. TenantLine은 `srm_*`에만 적용하고 relay table은 제외합니다. 쓰기는 tenant, root visibility, lifecycle과 optimistic version을 확인합니다.

관리 role과 portal role은 분리합니다. Portal API는 인증 user의 연결 Supplier를 도출하고 임의 supplierId를 인가 근거로 사용하지 않습니다. PII는 명시 권한이 없으면 mask하며 로그와 event에는 최소 ID/state만 기록합니다.

## 5. 서비스 간 일관성

Auth API는 tenant 제한을 적용합니다. Workflow는 멱등 시작하고 결과 event를 소비합니다. Procurement는 active Supplier를 선택해 RFQ invitation을 공개하며 Portal은 requestId/version과 함께 견적을 Procurement에 제출합니다. Asset은 SRM DB를 읽지 않고 명시 snapshot/ID를 사용합니다.

Outbox는 tenantId를 명시하고 Consumer는 tenant 검증, Inbox와 업무 변경의 단일 transaction, 중복·역순 내성을 구현합니다. SRM DB transaction 안에서 network call을 하지 않습니다.

## 6. API, UI, 저장과 검증

Controller는 `R<T>` / `R<PageResult<T>>`, write는 `@PreAuthorize`를 사용합니다. 관리 화면은 `views/srm/`, Portal은 `views/supplier-portal/`, button은 같은 `v-permission`을 사용합니다.

구조는 `database/changelog/srm/`, seed는 `scripts/sql/seed/srm.sql`과 `scripts/sql/seed/auth.sql`에서 관리하고 manifest checksum/assertion을 함께 갱신합니다. 테스트는 lifecycle, tenant/DataScope, 자식 root 상속, Saga replay/failure, invite, 평가 계산, risk, quotation, PII/XSS/audit와 관리/Portal 브라우저 흐름을 포함합니다.

[SRM 시스템 문서](../srm.kr.md)와 [사용 흐름](../guides/srm-flow.kr.md)을 참조하세요.
