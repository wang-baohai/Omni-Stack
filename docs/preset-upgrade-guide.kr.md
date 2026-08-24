# 프리셋 프로젝트 업그레이드 가이드

`scaffold.lock`은 source, catalog, preset, module, template 버전을 기록합니다. 새 빈 디렉터리에 다시 생성하고 수기 코드를 이전하는 방식이 기본이며 파괴적인 제자리 가지치기는 지원하지 않습니다.

## 사전 점검

1. 프로젝트, DB, 환경 설정을 commit 또는 backup합니다.
2. 현재 lock을 보존하고 관리 파일의 수동 변경을 확인합니다.
3. release note와 catalog의 `compatibility` / `deprecation`을 검토합니다.
4. 공식 preset은 `omni preset diff`, custom은 해석된 클로저를 비교합니다.
5. DB expand/migrate/contract 구간과 서비스 간 계약 변경을 확인합니다.

## 절차

새 CLI로 validate·explain·dry-run을 실행하고 새 빈 디렉터리에 생성합니다. 새/이전 lock과 생성물을 비교하되 이전 Maven/Compose/Gateway/seed/catalog 등록으로 덮어쓰지 않습니다. 수기 domain code, test, 비밀이 아닌 설정만 이전하고 관리 영역 변경은 declaration/template로 되돌립니다.

DB backup, fresh, 운영 형태 snapshot의 clone-upgrade를 수행하고 backend, frontend, Compose, 잔여, 로그인/메뉴/health, 핵심 흐름, E2E를 통과시킵니다. 한 릴리스 기간의 호환 rollback 여유를 유지한 뒤 전환합니다.

## 롤백과 완료

이전 앱은 호환되는 확장 schema에 롤백합니다. 검증되지 않은 down SQL 대신 Liquibase 전진 수정 changeSet을 추가합니다. 배포 전이라면 새 디렉터리만 버리면 원본은 변경되지 않습니다.

lock이 module/route/permission/DB/Compose와 일치하고 fresh와 clone-upgrade가 멱등이며 제거 모듈 잔여가 없고 tenant/role 핵심 흐름 및 README·matrix·유지관리 문서가 동기화되면 완료입니다.
