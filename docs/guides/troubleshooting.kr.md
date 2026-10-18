# 문제 해결 안내서

문제 해결 원칙: 먼저 실패 계층을 확정한 뒤 Trace ID, 업무 ID, 로그 증거로 위치를 특정합니다; 반복 재시작이나 데이터베이스 직접 수정으로 근본 원인을 가리지 않습니다. 빌드·검사·진단 프로그램은 로컬 Docker에서 실행하며 호스트는 편집·읽기 감사·Docker 관리에만 사용합니다. HTTP 상태와 응답 본문 R.code를 별도로 보관하고 업무 코드를 그대로 HTTP 상태라고 쓰지 마세요.

네 언어 문서는 [승인된 AI 검토 정책](../adr/0003-authorized-ai-translation-review.md)에 따라 실제 AI 의미 검토 통과로 인수하며 사람의 번역 검토는 필수가 아닙니다. [이번 검토 범위](../reference/evidence/troubleshooting-guide-review-2026-10-17.json)는 문서·현재 소스이며 로그인·작업·메시지·캐시 새로고침·로그 보관을 다시 실행하지 않았습니다. 절차를 사용할 수 있다는 사실이 현재 장애의 재시험·해결을 증명하지는 않습니다.

## 1. 시작 실패

| 현상 | 검사 | 처리 |
|---|---|---|
| Maven 플러그인 비호환 | `java -version`, `JAVA_HOME` | JDK 25 로 전환 후 `./mvnw` 사용 |
| 앱이 Nacos 대기 | Nacos 헬스, 8848/9848, 신원 구성 | 먼저 Nacos 를 복구한 뒤 대상 서비스 재시작 |
| migrator 실패 | migrator 로그, DATABASECHANGELOG | 새 changeSet 이나 환경을 수정하고 실행된 changeSet 은 고치지 않음 |
| MySQL 연결 거부 | 컨테이너 헬스, 포트, 계정, 데이터베이스 | 대상 Compose project 와 연결 문자열 확인 |
| 미선택 의존 반복 재시도 | `dev status` / `dev doctor` (`dev plan` 하위 명령은 없고 계획은 `dev up` 이 Docker 실행 전 출력), profile | 올바른 프리셋 사용 또는 선택적 통합을 명시적으로 비활성화 |

## 2. 로그인 실패

- CAPTCHA 가 비어 있거나 오류: 이미지를 새로고침하고 새 `captchaKey` 를 사용.
- 사용자가 존재하지 않음: 테넌트와 테넌트 내 사용자 이름 확인.
- 로그인 후 로그인 페이지로 반복 이동: 토큰 유효기간, 로컬 시간, Gateway 신원 헤더 확인.

- 소셜 콜백 실패: 콜백 URI, 백엔드 HMAC 서명 `state`, 프로바이더 구성을 확인합니다(외부 소셜은 프런트엔드 PKCE와 무관). 프런트엔드 PKCE (`code_verifier`/`code_challenge`)는 로컬 `/oauth2/authorize` SPA 흐름에 속하며 별도로 진단합니다.
- Portal 403: `SUPPLIER` 역할, Portal 연관, 공급업체 상태 확인.

로그인 이벤트는 보안 감사의 로그인 이벤트를 읽기 전용으로 투영한 로그인 로그에서 확인합니다. 그 밖의 인증·보안 이벤트는 보안 감사 기록을 확인하세요. Auth 인증 동작은 업무 작업 로그에서 찾지 않습니다.

## 3. 메뉴 또는 권한 이상

1. 비공개 환경에서 JWT `scope` 권한 집합과 `/api/auth/menus` 응답을 확인합니다. 전체 JWT를 로그나 지원 요청에 붙이지 않습니다.
2. 권한 시드, 역할 관계, 조직 범위를 확인.
3. 권한 변경 후 다시 로그인.
4. 권한 없는 계정으로 쓰기 API 를 직접 호출해 백엔드가 403 을 반환하는지 확인.
5. 프론트엔드 버튼만 비정상이면 `v-permission` 과 백엔드 권한 코드가 완전히 일치하는지 대조.

## 4. 데이터가 안 보이거나 권한 초과

- 요청이 Gateway 를 거치는지 확인(신원 헤더를 위조해 비즈니스 서비스에 직접 연결하지 않음).
- 테넌트 ID, 현재 사용자, 조직, 역할 범위를 확인.
- 도메인 DataPermission 테이블 이름과 열 매핑을 확인.
- 자식 테이블은 집계 루트를 통해 상속해야 하며, 존재하지 않는 owner 열에 조건을 추가하지 않음.
- DataPermission 인터셉터가 Pagination 앞에 있는지 확인.

## 5. Workflow

- 모델을 저장할 수 없음: BPMN XML 과 디자이너 상태 확인.
- 검증 실패: 검증 목록에 따라 process id, 연결선, 후보자 구성, 표현식 수정.
- 게시 충돌: 동일 모델의 동시 작업과 현재 초안 확인.
- 시작 실패: 업무 전표 번호로 시작 요청 예약 기록과 재시도 상태 조회.
- 승인자가 비어 있음: 후보자 미리보기로 role, anchor, 조직 범위, 폴백 전략 확인.
- 합동 결재 결과 이상: `MI_END` 이력 작업 삭제 사유와 카운터 변수 확인.

## 6. XXL-JOB

- 시스템 작업이 안 보임: `@XxlJob` 과 `@SystemJobMeta` 이중 어노테이션 확인.
- 개인 작업에 처리기 없음: Bean 이름은 `typeCode` 와 같아야 함.
- 생성이 고아 기록을 남김: 등록 실패 후 데이터베이스 삭제 보상 확인.
- 즉시 실행에 로그 없음: Admin, 실행기 등록, 처리기 예외, 로그 쓰기 확인.

## 7. MQ 및 크로스 서비스

1. Outbox 에서 메시지 ID, Topic, 업무 Key 로 조회.
2. PENDING, FAILED, DEAD_LETTER 상태와 다음 재시도 시간 확인.
3. Broker 전달과 컨슈머 Inbox 확인.
4. producer traceId 와 consumer traceId 로 Loki/Tempo 에서 연관.
5. 하류 멱등성을 확인한 뒤에만 수동 재전송.

Relay 의 크로스 테넌트 스캔은 설계된 동작이며, 외부 조회 인터페이스는 여전히 테넌트로 필터링해야 합니다.

## 8. 프론트엔드

- 빈 페이지: 동적 메뉴 로드, 라우트 chunk, 브라우저 콘솔, API 401/403/404 확인.
- 동적 폼 필드 유형 오류: JSON Schema `type`, `enum`, `options`, required 확인.
- 언어가 지속되지 않음: `omni-lang` 이 `zh-CN`, `en-US`, `ja-JP`, `ko-KR` 인지 확인.
- 날짜/금액 형식 오류: 현재 locale 을 사용하는지 확인, 인터페이스 값은 번역으로 바뀌면 안 됨.
- lint 실패: 규칙을 낮추지 말고 소스를 수정해 `--max-warnings 0` 을 유지.

## 9. 지원 요청 시 제공

- 해당 버전과 커밋 ID.
- 대상 프리셋과 Compose project 이름.
- 시간 범위, Trace ID, 업무 ID.
- 마스킹된 오류 응답과 관련 서비스 로그.
- 재현 절차, 기대 결과, 실제 결과.

비밀번호, CAPTCHA 답, JWT, 내부 토큰, 개인 키, 마스킹되지 않은 개인 데이터를 제공하지 마세요.

## 10. 이전 운영 설명에서 옮긴 점검 절차

현재 소스에 맞춘 절차입니다. 이전 이미지·포트·과거 검증 성공은 현재 장애 해결을 증명하지 않습니다. 빌드와 진단 프로그램은 로컬 Docker에서 실행하고 호스트는 편집과 Docker 관리에 사용합니다.

<!-- omni:modules:auth -->
| 증상 | 우선 확인 | 소스와 복구 범위 |
|---|---|---|
| CAPTCHA 이미지 없음 | Auth 상태, Redis 연결, 비공개 비밀번호 설정 | `CaptchaServiceImpl` 검증은 레코드를 한 번 소비합니다. 실패·만료 시 새로고침 후 사람이 입력하며 이전 코드를 재사용하지 않습니다 |
| 로그인 직후 401 | Gateway의 Auth `/oauth2/jwks` 접근, `kid`, 서명, `exp`, 시각 | `JwkKeyProvider`와 Gateway 검증을 확인합니다. 비밀번호 로그인 코드의 대체 기본값은 900초지만 저장소 `application.yml` 설정은 7200초입니다. 배포 덮어쓰기가 적용된 `auth.token.access-token-ttl`와 실제 `exp`를 확인하고 다른 OAuth grant는 별도로 점검합니다 |
| 빈 메뉴·숨겨진 버튼 | `scope`, `sys_role_permission`, `permission_code`, `v-permission` | 권한 변경 후 다시 로그인합니다. 메뉴 숨김과 API 403은 별도로 확인하며 고정 경로로 권한을 보충하지 않습니다 |
| 소셜 콜백 실패 | provider URI와 `AUTH_OAUTH2_*_REDIRECT_URI`의 정확한 일치, 서버 `state` 서명 설정, 컨테이너 외부 연결 | 로컬 콜백은 Gateway 8102로 들어가며 내부 Auth 8080은 외부 콜백이 아닙니다. [인증 절차](authentication.kr.md) 참고 |
<!-- omni:modules:end -->

<!-- omni:modules:platform -->
| 증상 | 우선 확인 | 소스와 복구 범위 |
|---|---|---|
| 사전 수정 후 이전 값 표시 | 테넌트, `typeCode`, `DictDataServiceImpl`의 테넌트 캐시 키 | 쓰기 시 즉시 무효화해야 합니다. `dict:data:refresh` 권한으로 `POST /api/base/dict/data/refresh-cache?typeCode={code}` 후 다시 읽습니다. 이전 PUT `/refresh`는 사용하지 않으며 30분 TTL은 정확성 보장이 아닙니다 |
| 사전 유형 삭제 후 데이터 잔존 | `DictTypeServiceImpl.deleteType()`의 같은 테넌트 데이터·유형·캐시 트랜잭션 | 반환 ID와 고유 코드만 대조합니다. 실패 시 트랜잭션과 테넌트를 확인하며 공유 데이터를 수동 삭제하지 않습니다 |
| 작업 로그 누락·지연 | 원본 서비스 Outbox, 공식 relay, Broker, `OperLogConsumer.operlogConsumer()`, Inbox 중복 제거 | 동기 요청 커밋부터 확인하고 producer/consumer Trace를 추적합니다. 비동기 지연은 롤백 증거가 아니며 Auth 로그인은 보안 감사 투영에서 조회합니다 |
| 로그 보관 작업 미실행 | Base 실행기 등록, XXL 실행 기록, `OperLogArchiver`의 처리기 `operLogArchiveHandler` 이중 어노테이션 | 실행 한 번과 보관 기록을 대조합니다. 복제본이 여러 개면 단일 실행기 라우팅을 사용하며 현재 결과 확인 전 재실행하거나 증거를 위해 공유 로그를 보관 처리하지 않습니다 |
<!-- omni:modules:end -->
