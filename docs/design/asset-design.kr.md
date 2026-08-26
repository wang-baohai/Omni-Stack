# Asset 관리 모듈 아키텍처 및 구현 기준

> 기술 번역 초안이며 사람의 검토가 필요합니다. code, API 값과 상태 이름은 유지합니다.

## 1. 범위와 경계

`omni-asset`은 asset ledger, Procurement receipt ingestion, assignment/acceptance/return, transfer, disposal과 overview를 소유합니다. Procurement는 PO/receipt, Workflow는 승인 runtime, Auth는 identity/RBAC/DataScope, SRM은 Supplier를 소유합니다. Asset은 다른 서비스 DB를 읽지 않고 Flowable이나 `omni-common-workflow`를 포함하지 않습니다.

MVP는 물리 asset card와 custody를 관리합니다. 회계 감가상각, 유지보수 work order와 고급 창고 기능은 후속 범위입니다.

## 2. Domain과 lifecycle

주요 집계는 Asset, Transfer, Disposal과 Inbox Event입니다. 모든 `ast_*`를 tenant로 분리합니다. 관리 목록은 `owner_user_id/owner_unit_id`, My Assets·accept·return은 고정 `current_user_id`를 사용합니다. Transfer/Disposal 자식 행은 `ast_asset`에서 접근 범위를 상속합니다.

상태 변경은 available, pending acceptance, in use, transfer/disposal processing, returned, disposed server command이며 임의 status edit는 금지됩니다. 쓰기는 tenant, visibility, state, active operation과 version을 검증합니다.

## 3. Procurement receipt ingestion

`ProcurementGoodsReceiptConsumer`는 `qualityStatus=PASS`, `assetManaged=true`, `assetQuantity`가 정확한 양의 정수일 때만 card를 만듭니다. 소수를 반올림하지 않습니다.

Inbox event ID로 중복 event를 막고 source receipt-line + unit sequence로 다른 message ID의 동일 card 중복도 막습니다. `purchaseAmount`, `residualValue`와 event price는 JSON number가 아닌 decimal string입니다.

Backfill은 인증된 internal API, 명시 tenant, bounded page와 같은 validation/idempotency를 사용하며 일반 ingestion rule을 우회하지 않습니다.

## 4. Assignment, Transfer와 Disposal

Assignment는 custody와 acceptance action을 만들고 할당된 고정 current user만 accept/return할 수 있습니다. 관리 scope를 자기 서비스 인가로 재사용하지 않습니다.

Transfer와 Disposal은 `ast_asset.active_operation_type/active_operation_id`를 원자적으로 공유하여 한 번에 하나의 operation만 허용합니다. 시작 시 `previous_asset_status`와 occupancy를 저장하고 local transaction 밖에서 Workflow를 멱등 시작합니다. cancel/reject는 같은 transaction에서 previous status를 복원하고 occupancy를 clear합니다. 중복·역순 event는 추가 부작용이 없습니다.

## 5. Tenant, RBAC, DataScope와 integration

TenantLine은 `ast_*`에만 적용하고 `sys_mq_message`는 제외합니다. Gateway → tenant filter → `@PreAuthorize` → aggregate scope → MyBatis → AccessGuard 순서로 fail closed합니다. 보이지 않는 행은 404, write는 resource/action permission과 같은 `v-permission`을 사용합니다.

Workflow는 idempotent request + Inbox event로 연계하며 Asset table을 직접 수정하지 않습니다. Procurement event/backfill DTO는 version 계약입니다. Outbox는 tenantId를 명시하고 Consumer는 tenant context를 설정한 뒤 `finally`에서 제거합니다.

## 6. API, UI, 저장과 검증

Controller는 `R<T>` / `R<PageResult<T>>`입니다. `views/asset/`에 overview, ledger, My Assets, transfer와 disposal의 responsive 상태를 구현합니다. 구조는 `database/changelog/asset/`, RBAC seed는 `scripts/sql/seed/auth.sql`에서 관리합니다.

테스트는 lifecycle, tenant/DataScope, 고정 current user, receipt 조건·정확 수량, 이중 멱등, decimal string, backfill replay, accept/return, Transfer/Disposal 충돌, cancel/reject 복원, Workflow retry/duplicate/order, Outbox/Inbox, 권한, XSS/audit와 전체 browser flow를 포함합니다.

[Asset 사용 흐름](../guides/asset-flow.kr.md)을 참조하세요.
