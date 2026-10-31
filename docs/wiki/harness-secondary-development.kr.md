# Harness로 2차 개발하기: 작은 작업을 끝까지

이 글은 하나의 작업으로 Harness의 다섯 요소를 연결합니다. **supply-chain 학습 프로젝트에서 CRUD 생성기로 "자재 브랜드"의 실행 가능한 기능을 만들고 권한, 메뉴, 빌드를 검증합니다.** 작업은 작지만 같은 방식은 다른 업무 객체에도 적용됩니다.

아래 명령은 [스캐폴드 개발 가이드](../guides/scaffold-development.kr.md)에서 왔습니다. 이 글이 사용자 환경에서 실행되었다고 주장하지 않으며, 각 단계에 기대 결과와 실패 시 기록할 내용을 제시합니다.

## 0단계: 경계 정하기

작업 전에 답합니다.

- 대상 프리셋: `supply-chain`(SRM, 구매, 자산).
- 생성 위치: 저장소 안 `generated/learning-supply-chain`, 주 프로젝트를 건드리지 않음.
- 공유 데이터베이스를 바꾸지 않고 생성물의 migrator나 시드를 실행하지 않음.
- 완료 조건: 백엔드 빌드, 프런트엔드 lint/build, 생성물 자체 검증 통과, 권한과 메뉴 진입점에 실제 증거 존재.

이것이 문맥과 제약입니다. 없으면 생성기가 잘못된 위치에 쓰거나 이 컴퓨터에서 두 번째 환경을 띄울 수 있습니다.

## 1단계: 현재 사실 읽기

모델에게 코드를 바꾸게 하기 전에 세 곳을 읽습니다.

1. 루트 [AGENTS.md](../../AGENTS.md)의 하드 제약과 완료 체크리스트.
2. [아키텍처와 엔지니어링 사실](../architecture.kr.md)의 supply-chain 모듈 경계.
3. [구매 설계](../design/procurement-design.kr.md)의 자재 카탈로그 테이블, 권한, 데이터 범위 규칙.

읽은 뒤 자재 테이블이 어느 서비스에 속하는지, 테넌트 차단 범위, 쓰기에 필요한 권한 코드, 실패 응답을 말할 수 있어야 합니다. 말할 수 없으면 계속 읽고 생성을 시작하지 않습니다.

## 2단계: 학습 프로젝트 생성

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

기대: 첫 명령은 `preset created: supply-chain@...`, 두 번째는 `generated preset valid`를 출력합니다. 실패하면 전체 출력과 종료 코드를 기록하고, 템플릿, 네트워크, 디스크 중 원인을 확인하기 전에 디렉터리를 지우고 반복 재시도하지 마세요.

생성물에는 자체 `scaffold.lock`, Compose, AI 진입점이 있습니다. 이후 명령은 생성물 안에서 실행하고 원 저장소의 절대 경로에 의존하지 않습니다.

## 3단계: 생성기 변경 미리보기

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
```

`--check`는 입력을 검증하고 `--dry-run`은 계획 변경을 나열합니다. 세 가지를 확인합니다.

- 예상 모듈만 바뀌고 다른 업무를 건드리지 않았는가.
- 권한 코드, 메뉴, 마이그레이션, 프런트엔드 진입점이 포함되었는가.
- 기존 파일 덮어쓰기 경고가 없는가.

미리보기는 형식이 아닙니다. 계획에 공유 파일 재작성이 보이면 `--apply`로 밀어붙이지 말고 템플릿을 확인한 뒤 멈춥니다.

## 4단계: 적용과 검증

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-procurement -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

기대: 생성기가 끝나고 변경을 나열하며, Maven reactor가 성공하고, 프런트엔드 lint와 build가 통과합니다. 실패 시 기록합니다.

- 명령, 작업 디렉터리, 종료 코드.
- 마지막 요약 줄이 아니라 첫 번째 실제 오류.
- 템플릿, 의존성 다운로드, 로컬 환경 중 원인.
- 생성된 파일과 되돌릴 필요 여부.

"다시 실행"으로 근본 원인 판단을 대체하지 마세요. 쓰기 결과가 불확실하면 파일이 생성되었는지 확인한 뒤 재시도를 결정합니다.

## 5단계: 권한과 진입점 검증

빌드 성공은 코드가 컴파일되었다는 것만 증명합니다. 다음도 검증합니다.

1. 백엔드 쓰기가 `resource:action` 권한 코드를 선언하는가.
2. 권한 시드와 프런트엔드 `v-permission`이 일치하는가.
3. 메뉴/라우트가 권한이 있을 때만 나타나는가.
4. 테넌트와 데이터 범위가 모듈 규칙대로인가.
5. 실제 로그인 후 대상 테넌트에서 올바른 진입점이 보이고 월권 요청이 거부되는가.

실제 실행 환경과 신원이 필요하며 소스 읽기만으로는 부족합니다. 조건이 없으면 "미검증"으로 기록하고 빌드 성공을 기능 통과로 바꾸지 마세요.

## 6단계: 피드백과 체크포인트 남기기

완료 후 최소한 다음을 기록합니다.

- 변경한 대상과 각각의 소유 모듈.
- 통과한 검사와 증거 위치.
- 검증하지 못한 내용과 이유.
- 다음에 누가 무엇을 하는가.

긴 작업이면 현재 단계, 실패 로그 위치, 복귀 진입점도 남깁니다. 체크포인트는 "나중에 보자"는 메모가 아니라 다음 세션이 0에서 헤매지 않게 하는 최소 정보입니다.

## AI에게 실제 변경을 맡기기: 요구사항·사실·경계·피드백

위의 6단계는 "생성기를 따라가는" 흐름입니다. 실제 2차 개발은 오히려 **기존 프로젝트 안에서 AI에게 통제된 변경을 맡기는** 경우가 많습니다. 이 절은 같은 주 예제(워크스페이스에 모듈 진입점 추가)를 사용해 요구사항·사실·경계·검증을 어떻게 전달할지 설명합니다. "만들어 줘"만으로는 부족합니다.

### 요구사항: 누가 어떤 조건에서 무엇을 보는지 먼저 쓴다

실행 가능한 요구사항에는 최소 네 가지가 있습니다.

```text
역할: 로그인한 관리자 또는 직원
진입: 워크스페이스 앱 목록, 전역 앱 드로어, 헤더 메뉴 검색
동작: 모듈 클릭 시 새 탭으로 열림. 모듈 내부 이동은 현재 탭 유지
경계: 진입점은 현재 권한으로 들어갈 수 있는 페이지만. 링크에 토큰 없음. 네 언어와 좁은 화면 동작
수용: 카드/드로어/검색이 같은 대상을 가리킴. 권한 없는 딥링크는 403. 빌드·단위·구조 게이트 통과
```

"워크스페이스를 개선해 줘"처럼 검증할 수 없는 문장은 피합니다. **수용 조건이 구현보다 먼저**입니다. 그렇지 않으면 AI는 추측할 수밖에 없습니다.

### 영향 분석: 권한·테넌트·데이터를 먼저 판단한다

코드를 쓰기 전에 AI에게 네 가지를 묻습니다.

| 질문 | 주 예제의 답 | "예"인 경우 |
| --- | --- | --- |
| 권한 코드를 추가하는가 | 아니오. 기존 메뉴 권한 재사용 | `sys_permission` 시드와 `v-permission` 갱신 |
| 테넌트 테이블/컬럼을 추가하는가 | 아니오 | 전방 Liquibase changeset과 시드 검증 추가 |
| 백엔드 API를 바꾸는가 | 아니오. 기존 메뉴/통계 API만 사용 | API 계약과 테스트 갱신 |
| 생성기 클로저를 바꾸는가 | 아니오. 페이지 귀속만 등록 | catalog와 트리밍 갱신 |

주 예제에는 권한·테넌트·마이그레이션 변경이 **없습니다**. 템플릿을 변형해 필요하다면 UI 변경에 섞지 말고 독립 작업으로 먼저 수행합니다.

### 사실 선택: 인상이 아니라 검증 가능한 파일을 준다

변경 지점에 가까운 순서로 제공합니다.

1. 대상 페이지와 컴포넌트(`ConsoleHeader.vue`, `ApplicationDrawer.vue`, `WorkspaceEntryList.vue`, `layout/index.vue`);
2. 파생 로직(`useApplicationNavigation`, 라우트 meta);
3. 제약 문서(`docs/frontend-patterns.md`와 `docs/architecture.md`의 권한 장);
4. 기존 테스트와 검사 명령(`tests/navigation/*`, `package.json` scripts).

답변에서 `file:line`을 인용하고 "확인된 사실"과 "추측"을 구분하도록 요구합니다. 추측은 표시하고 구현에 넣으면 안 됩니다.

### 파일 경계: 바꾸면 안 되는 것을 먼저 말한다

주 예제의 경계:

- **허용**: 워크스페이스, 헤더, 드로어, 진입점 목록, 모듈 레이아웃, 대응 문구와 테스트, `scaffold/catalog/modules.yaml`의 귀속;
- **금지**: 백엔드 업무 코드, DB 마이그레이션, 공식 시드, 권한 모델, 생성기 클로저;
- **승인 필요**: 품질 스크립트, 스캐너, README 등 횡단 WP 파일.

AI가 범위 밖 변경을 제안하면 "가는 김에 고치기"가 아니라 확인하고 멈춥니다.

### 구현 피드백: 모든 단계가 재확인 가능한 결과를 낸다

실제 흐름은 닫힌 루프였습니다.

1. 먼저 빌드하고 캡처해 CRM 본문이 x=1038로 밀린 것을 발견;
2. DOM 치수로 `ConsoleHeader`와 `el-main`이 같은 행에 있음을 찾아 `direction="vertical"` 추가;
3. 1280~1920 다섯 폭에서 재측정해 본문이 모두 x=220에서 시작함을 확인;
4. 네 언어 캡처에서 영어 라벨과 글자 단위 줄바꿈을 발견해 두 가지 최소 수정;
5. 마지막으로 G1/G2/G6를 실행하고 G3의 기존 실패는 별도 등록.

각 단계에서 "입력 → 명령 → 출력 → 판단"을 남깁니다. **출력 없는 단계는 완료가 아닙니다.**

### 검증 약속: 게이트를 작업에 적는다

| 유형 | 주 예제 사용 | 설명 |
| --- | --- | --- |
| 빌드/정적 | `npm run build`, `npm run lint` | 실패 시 중단, 건너뛰지 않음 |
| 단위/타입 | `npm run test:unit`, `npm run typecheck:e2e` | 내비게이션 파생과 페이지 동작 포함 |
| 구조 | 다섯 프리셋 structure | 트리밍 후 죽은 링크 없음을 증명 |
| 런타임 | 실제 로그인 + 컨테이너 캡처 | 사용자가 보는 결과 증명 |
| 기존 결함 | G3 README 음성 사례 | 이 단계 PASS에 섞지 않고 별도 등록 |

실패 후 다음 행동도 씁니다. 빌드 실패는 첫 오류와 최근 diff, 런타임 실패는 이미지/포트/로그인, 구조 실패는 미등록 페이지. **재실행으로 운에 맡기는 것은 금지**입니다.

### 권한이나 마이그레이션이 필요한 시점

- 새 페이지나 버튼: 권한 코드와 시드를 먼저 추가하고 `v-permission` 적용;
- 테넌트 테이블/컬럼 추가: 전방 changeset → 엔티티와 쿼리 → 시드 매니페스트 갱신;
- 표시와 내비게이션만: 권한·마이그레이션 변경은 **발생하지 않음**. 주 예제가 여기에 해당.

"이번에는 발생하지 않았다"고 쓰는 것도 "수행했다"만큼 중요합니다.

## 흔한 실패

| 현상 | 먼저 확인 | 하지 말 것 |
|---|---|---|
| 생성기가 경로 없음 보고 | 현재 디렉터리, 프리셋 출력, 템플릿 완전성 | 저장소 밖 임시 디렉터리에서 재생성해 우회 |
| Maven이 모듈을 못 찾음 | 생성물에 포함되는지, 부모 POM이 갱신되었는지 | 부모 POM을 손으로 고쳐 템플릿 문제를 숨김 |
| 프런트엔드 타입 오류 | 템플릿과 현재 컴포넌트 API 일치 | 타입 검사 비활성화 |
| 권한 진입점 없음 | 권한 코드, 역할, 메뉴 시드, 테넌트 | DB를 직접 고쳐 메뉴 추가 |
| 실제 로그인 실패 | CAPTCHA, 테넌트, 계정, 게이트웨이, 서비스 상태 | 테스트 백도어로 로그인 우회 |

## 마친 뒤

세 질문에 답할 수 있어야 합니다. 작업에 필요한 사실, 도구, 검사는 무엇인가. 실패 시 증거는 어디인가. 다음에는 어떻게 계속하는가. 세 답이 나와야 생성기를 실행한 것을 넘어 Harness를 사용한 것입니다.

[Harness란 무엇인가](harness-what-and-why.kr.md) 또는 [사실 유지](harness-fact-maintenance.kr.md)로 돌아가 개념을 보완하세요.
