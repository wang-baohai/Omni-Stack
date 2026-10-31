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

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[전체 Wiki: 소개, 그림 사용법, 기동, 개발](docs/wiki/index.kr.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
