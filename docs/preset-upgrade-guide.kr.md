# 프리셋 프로젝트 업그레이드 가이드

프리셋 프로젝트는 `scaffold.lock` 으로 생성 시의 source 버전, catalog 버전, preset 버전, module 버전 및 service/CRUD/preset 템플릿 버전을 기록합니다. 업그레이드는 "새 디렉터리를 생성하고 수기 변경을 이전" 하는 것을 기본 전략으로 하며, 제자리 파괴적 가지치기는 지원하지 않습니다.

## 업그레이드 전 평가

1. 기존 프로젝트, 데이터베이스, 환경 구성을 commit 또는 backup 합니다.
2. 현재 `scaffold.lock` 을 보존하고 관리 대상 파일에 수동 드리프트가 있는지 확인합니다.
3. 소스 저장소 release note, catalog 의 `compatibility`/`deprecation`, preset 버전 변화를 읽습니다.
4. `omni preset diff <old> <new>` 로 공식 preset 을 비교하고, 커스텀 preset 은 해석된 의존성 클로저를 비교합니다.
5. 데이터베이스 expand/migrate/contract 창과 서비스 간 계약 변화를 식별합니다.

## 권장 업그레이드 절차

1. 새 버전 CLI 로 원본 preset 또는 커스텀 YAML 에 `validate`, `explain`, `--dry-run` 을 실행합니다.
2. 완전히 새 빈 디렉터리에 생성합니다.
3. 새/이전 `scaffold.lock` 과 생성 파일을 비교하고, 이전 Maven, Compose, Gateway, seed, catalog 등록을 직접 복사하지 않습니다.
4. 수기 도메인 코드, 테스트, 비밀이 아닌 환경 구성을 이전하고, 생성 영역 변경은 선언이나 템플릿에서 다시 합니다.
5. 데이터베이스 backup, fresh, 공식 스냅샷 clone-upgrade 연습을 수행합니다.
6. backend, frontend, Compose, 잔여, 로그인/메뉴/health, 핵심 흐름, E2E 게이트를 순서대로 통과합니다.
7. 배포를 전환하고 한 릴리스 주기의 호환 롤백 창을 유지합니다.

## 버전 판단

- `preset.version`: 모듈 구성과 기본 경계 버전.
- catalog `version`: 모듈 사실 구조 버전.
- `modules[].version`: 개별 모듈 구현 버전.
- `templates.*`: 생성 파일 형태 버전.
- `source.version`: 생성에 사용한 Omni-Stack 소스 버전.

어떤 템플릿의 major 변화나 preset 의 major 변화도 마이그레이션 가이드에 따라 수동 검토해야 하며, 수기 파일을 자동 덮어써선 안 됩니다.

## 롤백

애플리케이션 롤백은 이전 버전 이미지/프로젝트와 호환 데이터베이스 구조를 사용합니다. 데이터베이스는 연습되지 않은 down SQL 을 실행하지 않으며, 구조 문제 발생 시 전방 수정 changeSet 추가를 우선합니다. 이전 앱으로 되돌리기 전에 확장된 구조를 읽을 수 있는지 확인해야 합니다.

새로 생성된 프로젝트가 아직 배포되지 않았다면 새 디렉터리를 그냥 폐기하면 되며, 원본 프로젝트는 생성 명령으로 변경되지 않습니다. 이미 배포되었다면 로그, 마이그레이션 보고서, `scaffold.lock`, 실패 증거를 보존하고 근본 원인 분석을 완료한 뒤 재생성합니다.

## 업그레이드 완료 기준

- 새 lock 파일이 실제 모듈, 라우트, 권한, 데이터베이스, Compose 와 일치.
- fresh 와 clone-upgrade 가 모두 통과하고, 반복 마이그레이션과 seed 실행이 멱등.
- 제거된 모듈에 Maven, frontend, 권한, DB, MQ, 문서 잔여가 없음.
- 사용자 핵심 흐름과 역할/테넌트 격리에 회귀가 없음.
- README, 의존성 매트릭스, 유지관리 문서가 새 catalog 로 재생성 또는 동기화됨.
