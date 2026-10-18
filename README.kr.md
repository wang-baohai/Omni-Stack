# Omni-Stack

Spring Boot 4 + Vue 3 기반 마이크로서비스 프로젝트 생성 도구입니다. 테넌트, 권한, 승인, 업무 도메인이 필요한 기업 앱을 위해 소스, 기술 제약, 튜토리얼, 재사용 가능한 AI 워크플로를 함께 제공합니다.

[简体中文](README.md) · [English](README.en.md) · [日本語](README.jp.md)

## 주요 기능

- 테넌트 격리, 기능 권한, 데이터 범위, CAPTCHA, 소셜 로그인, OAuth2 인가 흐름.
<!-- omni:modules:workflow -->
- 독립 Workflow 서비스의 BPMN 설계, 배포, 승인, 합의.
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB의 시스템 및 개인 작업.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM 영업 흐름.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset 공급업체, 구매, 자산 협업.
<!-- omni:modules:end -->
- 트랜잭션 Outbox, 멱등 소비, 데드 레터 처리, 선택 가능한 메트릭·추적·로그.
- `tools/omni-cli`로 서비스, CRUD, 프리셋별 독립 프로젝트 생성.
- [AGENTS.md](AGENTS.md)와 기술 사실로 실행을 제약하고 [AI 자산](ai/README.kr.md)으로 재사용 가능한 도구 진입점 제공.

주요 기술: Spring Boot 4.0.6, JDK 25, Spring Cloud, Vue 3, TypeScript, Vite, Element Plus. 정확한 버전은 POM과 npm 잠금 파일을 참조합니다.

## 빠른 체험

[첫 실행 안내](docs/guides/quick-start.kr.md)에 따라 코드를 받고 로컬 Docker와 `.env`의 모든 필수 비밀 값을 준비합니다. JDK와 Node는 빌드 컨테이너에서 제공합니다.

```bash
docker compose --profile full up -d --build
docker compose ps
```

프런트엔드: `http://localhost:3000`. 개발 테넌트와 시드 계정을 선택하고 현재 CAPTCHA를 직접 입력합니다. 일부 업무만 실행하려면 먼저 프리셋을 선택합니다. 구조 마이그레이션과 기본 시드는 migrator가 관리하며 [데이터 설명](docs/wiki/data-and-fixtures.kr.md)을 참조합니다.

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[선택 가이드](docs/preset-quick-selection.kr.md) · [프리셋 의존성 매트릭스](docs/preset-dependency-matrix.kr.md)
<!-- omni:preset-table:end -->

## 문서와 기여

- [프로젝트 Wiki](docs/wiki/index.kr.md): 코드 받기, 업무 작업, 디버깅, 확장, 운영.
- [아키텍처 및 기술 사실](docs/architecture.kr.md), [API 계약](docs/api-contract.kr.md): 유지보수자를 위한 현재 동작 기준.
- [독립 문서 사이트](docs/wiki/site-maintenance.kr.md): Docker, 로컬 검색, 정적 배포.
- [저장소 규칙](docs/repository-governance.md), [변경 이력](CHANGELOG.md), [아키텍처 결정](docs/adr/0001-scaffold-upgrade-foundations.md).

기여 전에 관련 규칙을 읽고 영향을 받는 검증을 Docker에서 실행합니다. 이슈와 PR은 [GitHub](https://github.com/wang-baohai/Omni-Stack) 또는 [Gitee](https://gitee.com/wang-baohai/Omni-Stack)에 제출할 수 있습니다.

[Apache License 2.0](LICENSE) · © Wang Baohai
