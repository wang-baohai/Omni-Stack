# 프리셋, 경량 모드, 서비스 생성과 CRUD 생성

새 프로젝트는 CLI와 모듈 카탈로그를 사용하고 기존 서비스를 복사해 전체 문자열을 바꾸지 않습니다.

## 1. 프리셋 선택

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

프리셋은 백엔드, 프론트엔드, seed, migration, Compose profile, 외부 의존을 선택합니다. `supply-chain`은 SRM, Procurement, Asset을 포함합니다. [프리셋 선택](../preset-quick-selection.kr.md)과 [의존성 매트릭스](../preset-dependency-matrix.kr.md)를 참고하세요.

## 2. 경량 개발

```bash
npm --prefix tools/omni-cli run dev -- doctor
npm --prefix tools/omni-cli run dev -- dev plan --preset crm
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

계획 출력을 실행의 사실원으로 사용합니다. 선택하지 않은 MQ, Workflow, 스케줄러 통합은 명시적으로 끕니다.

## 3. 서비스 생성

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

Maven 모듈, 앱, 설정, health, Docker, 통합 계획, 잠금 정보를 생성합니다. 계획을 검토하고 JDK 25로 검증합니다.

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

`@RequestBody`가 있는 새 서비스는 `XssConfigProvider`를 구현합니다. Servlet은 `omni-common-service`를 사용하고 Gateway reactive 보안을 복사하지 않습니다.

## 4. CRUD 생성

```bash
npm --prefix tools/omni-cli run dev -- crud plan path/to/spec.yaml
npm --prefix tools/omni-cli run dev -- crud generate path/to/spec.yaml
```

타입, API, 서비스, 영속성, 권한, migration, 테스트, i18n 작업을 생성합니다. 상태 머신, 데이터 범위, 멱등성, 업무 검증은 개발자가 추가합니다.

## 5. DB와 권한

forward-only Liquibase changeSet, 안정 seed, `auth.sql` 권한, seed manifest, fresh/upgrade 두 경로를 갱신합니다. `migrate-*.sql`이나 임시 복구 스크립트를 공식 산출물로 추가하지 않습니다.

## 6. 사용자 프리셋

의존 폐쇄, 충돌, 전후단 선택, migration, seed를 선언하고 5개 프리셋 골든 매트릭스와 잠금을 갱신합니다. [사용자 프리셋](../custom-preset-tutorial.kr.md)과 [유지보수](../preset-maintenance.kr.md)를 참고하세요.

## 7. 완료 조건

JDK 25 Reactor, lint 0 warning, production build, 권한 부정 테스트, fresh/upgrade/대상 프리셋, 4개 언어 문서와 이미지, 임시 산출물 0을 만족합니다.

