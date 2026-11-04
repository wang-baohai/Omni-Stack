# Omni-Stack

Spring Boot 4 + Vue 3 기반의 마이크로서비스 스캐폴드입니다. 테넌트 분리, 권한 제어, 업무 확장이 필요한 기업용 관리 시스템을 만드는 데 사용합니다. 소스 코드, 기술적 사실, 그림 튜토리얼, AI 협업 규칙을 한 저장소에서 관리합니다.

[简体中文](README.md) · [English](README.en.md) · [日本語](README.jp.md)

## 주요 특징

- 공개 홈에서 Wiki를 읽고, 내 작업대에서 권한이 있는 앱을 찾아 업무 모듈을 새 탭으로 엽니다.
- 멀티테넌트, 기능 권한, 데이터 범위와 CAPTCHA 로그인, 소셜 로그인, OAuth2 인가를 지원합니다.
<!-- omni:modules:workflow -->
- 독립 Workflow 서비스에서 BPMN 모델링, 게시, 승인, 합의를 처리합니다.
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB으로 시스템 작업과 개인 작업을 관리합니다.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM에서 리드, 고객, 영업 기회, 후속 활동을 관리합니다.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset으로 공급업체, 조달, 자산 업무를 연결합니다.
<!-- omni:modules:end -->
- Transactional Outbox, 멱등 소비, 데드레터 관리와 선택 가능한 메트릭, 트레이스, 통합 로그 수집을 제공합니다.
- `tools/omni-cli`로 프리셋별 독립 프로젝트, 서비스, CRUD를 생성합니다.
- Harness 실천에 따라 간결한 기술적 사실, 공유 AI 워크플로, 검증 증거를 지속적인 개발에 활용합니다.

기술 기반: Spring Boot 4.0.6, JDK 25, Spring Cloud, Vue 3, TypeScript, Vite, Element Plus.

## 프로젝트 구조

| 경로 | 역할과 진입점 |
|---|---|
| `omni-backend/` | Maven 멀티 모듈 백엔드입니다. `omni-backend/pom.xml`에서 서비스와 공통 모듈을 묶습니다. |
| `omni-frontend/` | 공개 홈, 개인 작업대, 업무 화면을 포함한 Vue 3 애플리케이션입니다. |
| `database/` · `scripts/sql/seed/` | Liquibase 마이그레이션은 `database/changelog/`에 있으며 시드 데이터와 검증 규칙은 `database/seed/manifest.yaml`에 등록합니다. |
| `scripts/fixtures/` | 통제된 테스트 픽스처와 스크린샷 캡처 도구입니다. |
| `scaffold/` · `tools/omni-cli/` | `scaffold/catalog/modules.yaml`은 모듈을, `scaffold/presets/`는 프리셋을 정의합니다. CLI는 프로젝트, 서비스, CRUD를 생성합니다. |
| `docker/` · `compose.yaml` | Dockerfile은 `docker/`에 있습니다. `compose.yaml`은 로컬 서비스를 구성하고 `compose.tools.yaml`은 빌드와 검증 도구를 제공합니다. |
| `observability/` | 선택적으로 사용하는 메트릭, 트레이스, 로그, 대시보드 및 알림 설정입니다. |
| `docs/` | 기술 참조 문서, 4개 언어 Wiki, [소스 코드 색인](docs/reference/source-map.kr.md)입니다. |
| `AGENTS.md` · `ai/` | 프로젝트 실행 규칙과 공유 AI 자산의 진입점입니다. |
| `.agents/skills/` | Codex의 프로젝트 스킬 진입점입니다. 프로젝트 변경과 검토를 위한 `omni-change`, 질문으로 계획을 구체화하는 `grill-me`를 `ai/skills/`에서 생성합니다. |
| `.qoder/skills/` | Qoder의 프로젝트 스킬 진입점입니다. 어댑터로 동일한 `omni-change`와 `grill-me`를 동기화합니다. |
| `.github/workflows/` | 원본 저장소의 GitHub Actions 설정입니다. `quality.yml`은 푸시, 풀 리퀘스트, 예약 또는 수동 실행을 지원하며 백엔드, 프런트엔드, 문서, AI 스킬 진입점, 데이터베이스 마이그레이션, 스캐폴딩, 보안을 검사합니다. |

### 백엔드 모듈

- `omni-gateway`는 요청 진입과 라우팅, `omni-auth`는 인증과 인가, `omni-base`는 기초 데이터와 플랫폼 관리를 담당합니다.
<!-- omni:modules:workflow -->
- `omni-workflow`는 BPMN 모델링, 승인과 합의를 담당합니다.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- `omni-crm`은 리드, 고객과 영업 기회를 관리합니다.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- `omni-srm`, `omni-procurement`, `omni-asset`은 각각 공급업체, 조달, 자산을 담당합니다.
<!-- omni:modules:end -->
- `omni-common`과 `omni-common-*`는 공통 기반을 제공하며 `omni-db-migrator`는 데이터베이스 마이그레이션을 실행합니다.

### 프런트엔드 소스 진입점

- `omni-frontend/src/views/`에는 화면, `omni-frontend/src/components/`에는 재사용 컴포넌트, `omni-frontend/src/api/`에는 백엔드 API 호출 코드가 있습니다.
- `omni-frontend/src/router/`는 라우팅, `omni-frontend/src/stores/`는 Pinia 상태, `omni-frontend/src/locales/`는 화면 번역을 관리합니다.
- `omni-frontend/tests/`에는 단위 테스트가 있습니다. `omni-frontend/e2e/`와 `omni-frontend/e2e-docs/`는 E2E 테스트와 문서용 스크린샷을 다룹니다.

### AI 도구 디렉터리

`ai/`에서 프로젝트의 AI 자산 원본을 관리합니다. `ai/manifest.yaml`에는 출처, 버전, 라이선스와 지원 도구를 기록합니다.

- `ai/skills/`에는 프로젝트 변경 절차를 담은 `omni-change`와 질문을 통해 계획을 구체화하는 `grill-me` 등의 스킬 원본이 있습니다.
- `ai/prompts/`에는 변경 검토와 문서 동기화용 템플릿이 있습니다.
- `ai/knowledge/`에는 Docker 검증 등 프로젝트에서 얻은 경험을 기록합니다.
- `ai/adapters/bootstrap.mjs`는 공유 원본에서 Codex와 Qoder의 프로젝트 스킬 진입점을 생성하고 동기화합니다.

스킬을 변경할 때는 먼저 `ai/skills/`를 수정한 뒤 어댑터로 도구별 파일을 동기화합니다. 설치와 검증 절차는 [AI 도구 안내서](ai/README.kr.md)를 참고하세요.

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[전체 Wiki: 소개, 그림 사용법, 기동, 개발](docs/wiki/index.kr.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
