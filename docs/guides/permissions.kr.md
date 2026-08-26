# 메뉴, 역할, 기능 권한과 데이터 권한

Omni-Stack은 “할 수 있는 기능”과 “볼 수 있는 데이터”를 분리합니다. 버튼 숨김은 보안 경계가 아니며 모든 쓰기 API는 백엔드에서 다시 인가합니다.

## 1. 관계 모델

```text
사용자 → 사용자 역할 범위 → 역할 → 권한 트리
                          ↘ 데이터 범위
```

권한은 `DIRECTORY`, `MENU`, `BUTTON/API` 노드이며 `resource:action` 형식을 사용합니다.

## 2. 동적 메뉴

로그인 후 `GET /api/auth/menus`를 호출합니다. Auth가 권한으로 필터링한 트리를 반환하고 프론트엔드는 `MENU`만 동적 라우트로 만들며 공유 맵으로 번역합니다.

메뉴가 없으면 프리셋, `sys_permission`, `sys_role_permission`, JWT authorities, `v-permission`을 순서대로 확인하고 권한 변경 후 다시 로그인합니다. 정적 라우트로 우회하지 않습니다.

## 3. 기능 권한

쓰기 Controller는 `@PreAuthorize`, 프론트엔드 작업은 같은 코드의 `v-permission`을 사용합니다. 지시자는 Vue 반응성을 위해 `display:none`을 사용하지만 백엔드 인가를 대체하지 않습니다.

개인 작업은 `createBy` 행 소유권을 검증하는 예외입니다. Supplier Portal은 권한 외에 `SUPPLIER` 역할과 활성 연결이 필요합니다.

## 4. 데이터 권한

전체, 테넌트, 조직, 조직과 하위, 본인, 사용자 지정 조직 범위를 지원합니다. Servlet 서비스는 Gateway의 신뢰 ID를 사용하고 MyBatis가 도메인별 조건을 추가합니다. DataPermission은 Pagination보다 앞에 두고 ThreadLocal은 `finally`에서 지웁니다.

CRM, SRM, Procurement, Asset은 서로 다른 집계 매핑을 사용합니다. 자식은 집계 루트를 통해 상속하고 존재하지 않는 owner 열을 추가하지 않습니다. 구매 요청은 요청자, RFQ/주문/입고는 소유자, 자산 셀프서비스는 `current_user_id`를 사용합니다.

## 5. 역할 유지보수

역할 선택, 기능 권한, 데이터 범위, 조직 내 사용자 할당 순으로 설정하고 대상 사용자로 다시 로그인해 메뉴, 버튼, API, 데이터 수, 403을 확인합니다. 슈퍼 관리자만으로 검증하지 않습니다.

## 6. 새 권한

`@PreAuthorize`, `scripts/sql/seed/auth.sql`, seed manifest, 프론트엔드 진입점, `v-permission`, 부정 테스트, 문서와 이미지를 함께 갱신합니다.

[백엔드 패턴](../backend-patterns.kr.md), [프론트엔드 패턴](../frontend-patterns.kr.md), [핵심 흐름](../core-flows.kr.md)을 참고하세요.

