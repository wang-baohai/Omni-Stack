# 5. 데이터 범위: 같은 회사 안에서 누가 무엇을 보는가

테넌트는 회사를 구분하지만 회사 안에도 분담이 있습니다. 리 씨는 자신의 고객을, 왕 관리자는 부서를 담당하며 일부 역할은 하위 부서까지 조회해야 합니다.

이를 데이터 범위로 표현합니다. 작업 자격과 조회 기록 조건을 모두 충족해야 합니다. 역할이 여러 개라고 매 작업에 가장 넓은 역할을 마음대로 적용할 수는 없습니다.

## 여섯 범위 값 이해하기

| 값 | 먼저 이해할 의미 |
|---|---|
| SELF | 본인 담당 기록 |
| DEPT | 현재 주 조직 담당 기록 |
| DEPT_AND_BELOW | 주 조직과 조건에 맞는 하위 조직 |
| CUSTOM | 역할에서 설정한 조직 |
| TENANT | 테넌트 안에서 본인·조직으로 더 좁히지 않음 |
| ALL | 범위 계층에서 추가 행 조건 없음. 교차 테넌트 여부는 API와 테넌트 규칙에 따름 |

SELF의 실제 열은 업무마다 다릅니다. 담당자·신청자·고정된 현재 자산 사용자일 수 있으며 임의의 user_id 열을 의미하지 않습니다.

일부 처리기가 ALL과 TENANT 모두 추가 조건을 만들지 않아도 모든 API에서 같은 의미는 아닙니다.

## 작업 자격과 범위 결합

common-service의 @ServiceDataScope 메서드는 현재 사용자·테넌트·permissionCode로 Auth에 범위를 요청하고 반환 신원의 일치를 검증한 뒤 메서드 실행 중 바인딩합니다.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

신뢰할 신원을 얻고 범위를 해석·검증한 뒤 원래 업무를 실행하고 마지막에 정리합니다. 연속된 핵심 부분이며 모든 메서드와 서비스가 같은 어노테이션을 쓴다는 주장은 아닙니다.

permissionCode가 있는 해석은 해당 전체 권한 코드를 실제 부여한 활성 역할만 고려합니다. 일반 Auth 요청의 관련 역할 범위 합성과는 다른 경로입니다.

예를 들어 사용자 관리 범위가 넓은 역할이 고객 작업까지 자동으로 넓히지는 않습니다. 그 역할이 고객 작업 권한도 주는지 먼저 확인합니다.

<!-- omni:modules:crm -->
## 고객 기록의 분기 읽기

CRM은 등록된 업무 테이블을 먼저 한정한 다음 이번 작업의 범위를 읽습니다. 조건 선택의 연속 원문입니다.

<!-- omni:source path="omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java" -->
```java
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        ServiceDataScopeContext.ScopeInfo info = ServiceDataScopeContext.get();
        if (info == null || info.effectiveScope() == null) {
            return deny(alias);
        }
        return switch (info.effectiveScope()) {
            case "ALL", "TENANT" -> null;
            case "SELF" -> equals(alias, "owner_user_id", info.userId());
            case "DEPT" -> equals(alias, "owner_unit_id", info.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, "owner_unit_id", info.accessibleUnitIds());
            default -> deny(alias);
        };
```

유효한 범위가 없으면 일치하지 않는 조건을 반환합니다. SELF는 owner_user_id, DEPT는 owner_unit_id, 하위·사용자 정의 범위는 조회 조직 집합을 사용합니다. ALL/TENANT는 owner 조건을 추가하지 않지만 별도 테넌트 플러그인이 회사 조건을 유지합니다.

영업 담당자는 “본인 담당이면서 A사 소속” 고객을, 관리자는 “부서 범위이면서 A사 소속” 고객을 봅니다. 담당 범위를 넓혔다고 테넌트 조건을 없애지 않습니다.

현재 소스가 만드는 경계 설명이며 모든 조합을 현재 환경에서 다시 실행했다고 주장하지 않습니다.
<!-- omni:modules:end -->

## 하위 테이블에 담당자 조건을 무조건 넣으면 안 되는 이유

상세 행에 owner_user_id가 없을 수 있습니다. 공급업체 연락처·평가 상세·구매 행이 부모 기록에서 조회 범위를 상속하면 그 관계를 따라 검사합니다.

부모는 폴더, 상세는 페이지라고 생각하세요. 페이지를 읽을 수 있는지는 폴더 접근으로 판단합니다. 없는 담당자 칸을 각 페이지에 요구할 수 없습니다.

전체 저장소의 도메인별 매핑은 다릅니다.

| 대상 | 범위 기준 |
|---|---|
| CRM의 등록된 고객 등 업무 테이블 | 각 담당자와 조직 열 |
| SRM 공급업체·평가 하위 리소스 | 공급업체·평가 관계 상속, 없는 owner 열 추가 금지 |
| 구매 요청서 | 신청자와 신청 조직 |
| 견적 요청·구매 주문·입고 및 상세 | 담당자·조직, 상세는 부모를 통해 상속 |
| 자산 관리·내 자산·인수/반납 | 관리는 owner, 본인 작업은 고정 current_user_id, 하위 기록은 자산을 통해 상속 |

전체 저장소의 분담이며 생성 프로젝트는 선택한 프리셋의 능력만 포함합니다. 없는 모듈의 메뉴나 데이터를 기대해서는 안 됩니다.

## 조직 트리에도 테넌트가 필요한 이유

번호가 큰 조직을 모두 하위 조직으로 취급하지 않습니다. 현재 테넌트의 관계와 경로로 범위를 펼칩니다.

DEPT는 활성 주 조직, DEPT_AND_BELOW는 적합한 하위 조직, CUSTOM은 역할에 설정한 루트에서 펼칩니다. Auth는 CUSTOM 루트와 하위 조직의 활성 상태를 완전히 동일하게 검사하지 않습니다. 하위 쿼리의 status=1만 보고 모든 루트도 같은 검사라고 추론할 수 없습니다.

SAME_UNIT/UNIT_AND_BELOW는 역할 할당이 같은 조직만 또는 하위까지 유효한지 설명하며 승인 담당자 탐색에도 사용됩니다. 본인·부서·테넌트 조회 범위와 별도 설정입니다.

## 예외와 실패 경계

온라인 사용자 관리는 Redis를 읽고 사용자 DB로 회사·조직을 확인합니다. 현재 ALL은 교차 테넌트를 허용하며 다른 범위는 먼저 회사를 제한합니다. 도메인 업무 목록도 같은 교차 접근이 된다는 증거는 아닙니다.

범위가 없을 때도 다릅니다. CRM 등 매핑된 도메인 테이블은 일치를 거부합니다. Auth 자체 처리기는 컨텍스트 누락·알 수 없는 범위·미등록 테이블이면 조건을 추가하지 않으므로 API와 쿼리의 다른 검사에 의존합니다. 플러그인 설치가 모든 경로의 자동 fail closed를 뜻하지는 않습니다.

범위 해석 실패·작업 권한 부족·업무 상태 거부는 다른 문제입니다. 헤더 위조·관리자 공유·조회 조건 제거로 우회하지 말고 멈춘 조건부터 조사하세요.

다음은 [캐시와 백그라운드](tenancy-background.kr.md)에서 HTTP 밖의 경로를 설명합니다.
