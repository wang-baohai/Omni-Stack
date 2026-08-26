# Procurement 실행 모듈 아키텍처 및 구현 기준

> 기술 번역 초안이며 사람의 검토가 필요합니다. API 값, 권한 code와 상태 이름은 번역하지 않습니다.

## 1. 범위와 경계

`omni-procurement`는 material category/material, requisition, approval rule, RFQ, quotation comparison, purchase order, goods receipt와 overview를 소유합니다. Workflow는 Flowable runtime, SRM은 supplier/portal, Asset은 asset card를 소유합니다. Procurement는 Flowable이나 `omni-common-workflow`에 의존하지 않습니다.

MVP 흐름은 Material → Requisition → Approval → RFQ → Quotation → Comparison → Purchase Order → Goods Receipt입니다. 계약, 청구, 정산, 고급 sourcing과 재고 관리는 범위 밖입니다.

## 2. 데이터와 상태 머신

집계 루트는 Material/Category, ApprovalRoute, Requisition, RFQ, PurchaseOrder와 GoodsReceipt입니다. 모든 `proc_*`를 tenant로 분리하고 자식 행은 root에서 접근 범위를 상속합니다. material/category/approval route/config는 tenant 공유이며 owner DataScope를 사용하지 않습니다.

Requisition, RFQ, PO와 GR은 server command, optimistic version과 변경 불가 업무 증거로 전이합니다. 금액·수량은 정밀도를 선언한 `BigDecimal`, 승인 금액 구간은 `[minAmount,maxAmount)`이며 상한 없음을 허용합니다.

## 3. 업무 친화 구매 요청 승인 규칙

화면 이름은 “구매 요청 승인 규칙”이며 routeCode나 modelVersionId를 직접 편집하지 않습니다. 사용자는 rule name, material category, 포함 하한, 제외 상한과 현재 배포된 procurement Workflow를 선택합니다. `routeCode`는 server가 `APR-{ULID}`로 생성하며 수정할 수 없습니다. exact category가 default category보다 우선합니다.

새 rule은 `category=purchase`의 current published model만 선택합니다. legacy 참조는 자동 변경하지 않고 보고합니다. 실제 match test, gap/overlap/default 분석, workflow invalid/unavailable 표시, 안전 node preview와 중지·삭제 impact를 제공합니다.

## 4. Tenant, RBAC와 DataScope

TenantLine은 `proc_*`에만 적용하고 `sys_mq_message`는 relay를 위해 제외합니다. Requisition은 requester, RFQ/PO/GR은 owner, child는 root를 통해 scope를 상속합니다. Gateway → tenant filter → `@PreAuthorize` → DataScope → MyBatis → AccessGuard 순서로 fail closed하며 write는 tenant, visibility, state, version과 permission을 검증합니다.

## 5. Workflow와 서비스 간 일관성

`RequisitionWorkflowCoordinator`는 rule 해석과 local start request를 저장하고 업무 transaction 밖에서 Workflow를 멱등 시작합니다. completion event는 Inbox를 통해 local state를 원자적으로 변경하며 Flowable은 Procurement table을 직접 수정하지 않습니다.

SRM은 active Supplier를 제공하고 RFQ invitation을 받습니다. Portal quotation은 requestId/expectedVersion을 사용해 중복을 멱등 처리합니다. Goods Receipt는 Asset event를 게시하며 Asset 생성 실패로 receipt를 rollback하지 않습니다. Outbox는 tenantId를 명시하고 Consumer는 tenant 검증과 Inbox+업무 갱신의 단일 transaction을 보장합니다.

## 6. API, UI, 저장과 검증

Controller는 `R<T>` / `R<PageResult<T>>`, write는 `@PreAuthorize`입니다. 화면은 `views/procurement/`, button은 같은 `v-permission`, state/code와 번역 표시를 분리합니다. 승인 rule은 1440×900, 1024×768, 390×844에서 페이지 수평 scroll이 없어야 합니다.

구조는 `database/changelog/procurement/`, seed는 `scripts/sql/seed/procurement.sql`과 `scripts/sql/seed/auth.sql`입니다. 테스트는 상태, rule 경계/충돌/gap, legacy model, Workflow retry, tenant/DataScope, quotation concurrency, PO/GR 멱등, Outbox/Inbox, Asset event, 권한, XSS/audit, responsive UI와 전체 browser flow를 포함합니다.

[Procurement 사용 흐름](../guides/procurement-flow.kr.md)을 참조하세요.
