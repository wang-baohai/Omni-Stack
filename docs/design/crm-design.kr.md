# CRM 모듈 아키텍처 및 구현 기준

> 번역 상태: `docs/docs-manifest.yaml`의 중국어 원본 해시를 기준으로 만든 기술 초안이며 업무 용어는 사람의 검토가 필요합니다. 식별자, 권한 코드와 상태 값은 번역하지 않습니다.

## 1. 설계 결론과 범위

`omni-crm`은 Lead, Customer, Contact, Opportunity, Activity와 영업 개요를 담당하는 독립 테넌트 서비스입니다. Auth가 신원, 테넌트 소속, RBAC와 permission-aware DataScope를 소유하며 CRM은 Auth DB를 직접 조회하지 않습니다. Flowable은 `omni-workflow`, RocketMQ는 at-least-once 전송, XXL-JOB은 레코드별 작업이 아닌 일괄 알림을 담당합니다.

MVP는 리드 퍼널, 기한 초과 후속 작업, Customer 360, 기회 파이프라인, 전환율·수주율과 감사를 제공합니다. 공용 풀, 대량 입출력, 태그, 병합, 계약과 고급 승인은 후속 단계입니다.

## 2. 도메인과 데이터

집계 루트는 Lead, Customer, Contact, Opportunity, Activity입니다. 모든 `crm_*` 테이블에 `tenant_id`가 있고 인가 대상 루트에는 owner user/unit, version, 논리 삭제와 감사 열이 있습니다. 인덱스는 tenant를 선두로 owner, 상태, 다음 후속 시간과 중복 검색을 구성합니다.

금액은 `DECIMAL(18,2)` / `BigDecimal`, 통화는 ISO 4217 code입니다. MVP는 테넌트별 단일 통화를 사용하고 서로 다른 통화를 합산하지 않습니다. 업무 번호는 테넌트 내 유일하며 `MAX + 1`은 금지됩니다.

Lead 전환은 행 잠금, 가시성과 상태 검증, 같은 CRM 트랜잭션에서 Customer/Contact/Opportunity 생성 또는 연결, 변경 불가 conversion 증거, Lead 갱신과 Outbox event 기록을 수행합니다. 유일 conversion 레코드가 재시도를 멱등화합니다.

## 3. 상태 머신

- Lead: new/working/qualified에서 converted 또는 disqualified로 전환합니다.
- Customer: active/inactive/blacklisted이며 전용 command로만 변경합니다.
- Opportunity: 테넌트 pipeline stage를 이동하고 closed-won/closed-lost가 종결 상태이며 모든 이동 이력을 저장합니다.
- Activity: planned/completed/cancelled이며 완료와 취소는 임의 필드 수정이 아닌 command입니다.

서버가 합법·불법 전환을 검증하고 테스트합니다.

## 4. 테넌트, RBAC와 DataScope

신뢰 경로는 Gateway identity → tenant filter → `@PreAuthorize` → permission-aware DataScope → MyBatis tenant/scope → `CrmRecordAccessGuard`입니다. tenant, scope, internal token 누락, timeout이나 불일치는 403/503으로 fail closed하며 무필터 조회로 저하되지 않습니다.

`@PreAuthorize`와 `@CrmDataScope`는 동일한 완전 permission code를 사용하고 ThreadLocal은 `finally`에서 제거합니다. TenantLine은 `crm_*`에만 적용하고 전체 테넌트 relay를 위한 `sys_mq_message`는 제외합니다. 보이지 않는 행은 ID 추측을 막기 위해 404, 갱신은 tenant + id + scope + version으로 수행합니다.

Customer 360의 Customer, Contact, Opportunity, Activity는 각각 권한과 scope를 해석합니다. 기본 `USER`에는 CRM 권한이 없습니다. `v-permission`은 표시 보조이며 backend가 최종 경계입니다.

## 5. API와 서비스 간 일관성

Controller는 `R<T>` / `R<PageResult<T>>`를 반환하고 페이지 최대 크기는 100입니다. 쓰기 API에는 resource/action 권한이 필요하며 owner 지정은 tenant 제한 Auth API로 검증합니다.

수명주기와 권한은 고정 enum이고 사전은 표시 code만 제공합니다. Workflow는 tenant/business key로 멱등 시작하고 Inbox event로 완료합니다. Flowable은 CRM DB를 직접 수정하지 않으며 CRM 트랜잭션 안에서 Feign/MQ 네트워크 호출을 하지 않습니다.

event는 `ReliableMessageRelay.send(bindingName, envelope, tenantId, eventId)`로 전송하고 ID, 상태와 최소 snapshot만 담습니다. Consumer는 tenant를 검증하고 system context를 설정·해제하며 `crm_inbox_event`와 업무 변경을 같은 트랜잭션에 기록하고 event 유일성과 aggregate version으로 중복·역순을 막습니다.

## 6. 개인정보, XSS, 감사와 UI

작업 로그는 PII를 마스킹하고 snapshot 제외, Trace/event ID, 동일 tenant/scope의 snapshot 조회를 보장합니다. CRM은 `XssConfigProvider`를 구현하고 Redis DB 0의 tenant XSS key를 읽습니다. 로그, Outbox, backup과 dead letter는 PII 관리 대상입니다.

화면은 `views/crm/**/index.vue`, API는 `src/api/crm.ts`에 둡니다. 모든 동작에 동일한 `v-permission`을 적용하고 API 값과 번역 표시를 분리합니다.

## 7. 비기능 요구와 인수

모든 목록은 페이지 처리하고 낙관적 잠금, 전환, Outbox/Inbox와 정기 claim은 멱등화합니다. MQ 중단 시에도 업무와 Outbox를 commit하고 나중에 전송합니다. 테스트는 상태 머신, 동시 전환, tenant/DataScope, fail closed, version 충돌, Outbox rollback, Inbox 중복·순서, PII, XSS, 권한과 주요 브라우저 흐름을 포함합니다.

구현 위치: `omni-backend/omni-crm/`, `omni-frontend/src/views/crm/`, `database/changelog/crm/`, `scripts/sql/seed/crm.sql`, `scripts/sql/seed/auth.sql`. [CRM 문서](../crm.kr.md)와 [사용 흐름](../guides/crm-flow.kr.md)을 참조하세요.
