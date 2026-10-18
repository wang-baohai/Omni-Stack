# Omni-Stack 학습 및 유지보수 Wiki

적용 기준: 주 브랜치 `fc165e3`와 문서 정리 브랜치. Java/Vue 기초를 알고 이 프로젝트를 처음 접하는 독자를 대상으로 합니다. 중국어가 내용 기준이며 [简体中文](index.md), [English](index.en.md), [日本語](index.jp.md)와 동기화합니다. 실제 번역 검토 상태(HUMAN_REVIEW와 소유자가 승인한 AI_REVIEW를 구분)는 [문서 매니페스트](../docs-manifest.yaml)에 기록합니다.

## 1. 처음부터 시작하기

처음 네 단계를 순서대로 완료한 다음 역할에 맞는 업무 경로를 선택합니다. 각 단계의 예상 결과를 로컬 Docker에서 확인합니다.

| 단계 | 읽고 실행할 내용 | 예상 결과 |
|---|---|---|
| 1 | [코드 받기, 구조 이해, 첫 실행](../guides/quick-start.kr.md) | migrator 정상 종료, 선택 서비스 정상, 프런트엔드 접근 가능 |
| 2 | [로그인과 테넌트](../guides/authentication.kr.md) | 실제 CAPTCHA로 올바른 테넌트와 사용자로 로그인 |
| 3 | [역할, 메뉴, 버튼, 데이터 범위](../guides/permissions.kr.md) | 관리자와 직원의 기능이 다르고 권한 밖 요청은 거부됨 |
| 4 | [시드, 교육 데이터, 테스트 fixture](data-and-fixtures.kr.md) | 빈 환경, 기본 시드, 업무 데모 기록을 구분 |
| 5 | [프리셋 선택](../preset-quick-selection.kr.md), [프로젝트 생성](../guides/scaffold-development.kr.md) | 일부 서비스 실행과 잘라낸 프로젝트 생성의 차이를 설명 |

## 2. 역할별 업무 학습

| 역할 또는 목표 | 경로 | 전제 조건 |
|---|---|---|
| 플랫폼 관리자 | [시스템, 보안, 감사](../guides/system-security-audit.kr.md) | core, 테넌트 관리자 역할 |
| 직원 및 승인자 | [Workflow](../guides/workflow-approval.kr.md), [개인 및 시스템 작업](../guides/scheduling.kr.md) | workflow, 후보자 할당과 작업 소유권 |
| 영업 담당자 | [CRM 리드에서 기회로](../guides/crm-flow.kr.md) | crm, 영업 역할과 데이터 범위 |
| 공급업체 관리자 및 공급업체 | [SRM 가입과 포털](../guides/srm-flow.kr.md) | supply-chain, 초대, SUPPLIER 역할, 유효한 연결 |
| 요청자, 구매자, 공급업체 | [구매 요청에서 입고까지](../guides/procurement-flow.kr.md) | supply-chain, 승인 모델, 유효한 공급업체, 구매 권한 |
| 자산 관리자, 사용자, 승인자 | [자산 생성, 할당, 이전, 처분](../guides/asset-flow.kr.md) | supply-chain, 합격한 자산 대상 입고와 승인 모델 |
| 운영 담당자 | [운영, 백업, 업그레이드](../guides/operations-upgrade.kr.md), [문제 해결](../guides/troubleshooting.kr.md) | 확인한 로컬 환경과 백업 범위 |

초기 시드는 과거 스크린샷의 주문이나 승인을 보장하지 않습니다. 그림 속 업무 번호는 예시이며 실제 작업에서 반환한 ID를 사용합니다. SQL 상태 변경으로 승인, 견적, 입고를 건너뛰지 않습니다.

## 3. 소스 읽기와 확장

- [소스 읽기와 중단점](source-debugging.kr.md): 요청부터 권한, 트랜잭션, 이벤트, 테스트, 실패 조건까지 추적합니다.
- [서비스 및 CRUD 생성](../guides/scaffold-development.kr.md): dry-run 후 생성, 통합, 검증합니다.
- [의존성 매트릭스](../preset-dependency-matrix.kr.md), [유지보수](../preset-maintenance.kr.md), [사용자 정의](../custom-preset-tutorial.kr.md), [업그레이드](../preset-upgrade-guide.kr.md): 사실 표는 catalog에서 생성됩니다.
- [아키텍처](../architecture.kr.md), [API 계약](../api-contract.kr.md), [핵심 흐름](../core-flows.kr.md): 유지보수자를 위한 현재 기술 사실입니다.
- [백엔드](../backend-patterns.kr.md), [프런트엔드 규칙](../frontend-patterns.kr.md): 코드 변경 전에 해당 규칙을 읽습니다.

## 4. 용어

| 용어 | 프로젝트에서의 의미 |
|---|---|
| preset | 모듈 선택. catalog가 전체 의존성 폐쇄를 계산 |
| migrator | 앱 시작 전 구조 마이그레이션, 멱등 시드, 초기 설정을 수행하는 일회성 프로세스 |
| tenant | 격리 경계. ALL 데이터 범위도 테넌트를 넘지 않음 |
| RBAC / DataScope | 기능 권한과 행 가시성, 두 조건 모두 적용 |
| Gateway assertion | 게이트웨이의 서명된 신원 정보. 하위 서비스는 클라이언트 신원 헤더를 신뢰하지 않음 |
| Outbox / Inbox | 같은 트랜잭션의 전송 대기 이벤트 / 소비 중복 제거 기록 |
| Saga | 분산 단계와 보상. 서비스 간 ACID 트랜잭션을 대신하지 않음 |
| fixture | 테넌트, 업무 키, 정리 범위가 명확한 테스트 또는 교육 데이터 |
| ADR | 이유, 결과, 대체 관계가 포함된 과거 결정 |
| Harness | 사실, 제약, 도구, 검증으로 이루어진 실행 루프 |

## 5. 문서 사이트와 유지보수

[독립 문서 사이트](site-maintenance.kr.md)는 Docker 빌드, 4173 포트, 로컬 검색, 정적 결과물, 호스팅 플랫폼별 소스 링크를 설명합니다. 저장소 Markdown이 유일한 본문 원본이며 언어 디렉터리는 매니페스트로 생성합니다.

현재 사실, 설계, 튜토리얼, ADR, 검수 이력의 책임은 [저장소 규칙](../repository-governance.md)에 있습니다. 과거 검수는 당시 범위만 증명합니다. 사이트 미리보기는 번역 검토나 릴리스 완료를 의미하지 않습니다.
