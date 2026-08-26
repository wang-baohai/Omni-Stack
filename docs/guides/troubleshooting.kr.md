# 문제 해결 안내서

먼저 실패 계층을 정하고 Trace ID, 업무 ID, 로그를 연결합니다. 반복 재시작이나 DB 직접 변경으로 근본 원인을 숨기지 않습니다.

## 1. 시작

Maven 문제는 JDK 25와 `./mvnw`, Nacos 대기는 health/8848/9848/인증, migrator는 새 changeSet과 환경, MySQL은 대상 Compose와 URL, 선택하지 않은 의존 재시도는 CLI plan/profile을 확인합니다.

## 2. 인증

새 CAPTCHA Key와 테넌트를 확인합니다. 리다이렉트 반복은 토큰 만료, 시간, Gateway ID, 소셜은 URI/PKCE/state/client, Portal 403은 역할, 연결, 공급업체 상태를 확인합니다.

## 3. 메뉴와 권한

JWT authorities, `/api/auth/menus`, seed, 역할 관계를 확인하고 변경 후 다시 로그인합니다. 금지된 쓰기를 직접 호출해 403을 확인하고 `v-permission`과 백엔드 코드를 비교합니다.

## 4. 데이터 범위

Gateway 경유, 테넌트, 사용자, 조직, 역할, 도메인 테이블/열, 자식 집계 상속, Interceptor 순서를 확인합니다. 존재하지 않는 owner 열을 추가하지 않습니다.

## 5. Workflow

BPMN과 후보자 설정을 검증하고 시작 실패는 업무 ID와 예약 기록, 후보자 없음은 role/anchor/scope, 다중 승인은 `MI_END`와 카운터를 확인합니다.

## 6. XXL-JOB

시스템 작업은 두 어노테이션, 개인 처리기는 Bean 이름=`typeCode`, 등록 실패는 DB 행 삭제, 즉시 실행 로그 없음은 Admin, 실행기, 처리기, 로그 저장을 확인합니다.

## 7. 메시지

Outbox → Broker → Inbox를 메시지 ID, topic/key, producer/consumer trace로 추적합니다. 하류 멱등성을 확인한 뒤 데드레터를 재전송합니다. relay 전체 테넌트 스캔은 설계이며 외부 조회는 테넌트로 필터링합니다.

## 8. 프론트엔드

빈 화면은 메뉴, chunk, 401/403/404, 동적 폼은 Schema, 언어는 `omni-lang`, 날짜/금액은 현재 locale을 확인합니다. `--max-warnings 0`을 약화하지 않습니다.

## 9. 지원 정보

버전/commit, preset, Compose project, 시간, Trace/업무 ID, 마스킹 응답·로그, 재현, 기대/실제를 제공합니다. 비밀번호, CAPTCHA 답, JWT, 내부 토큰, 개인 키, 원본 개인정보를 제공하지 않습니다.

