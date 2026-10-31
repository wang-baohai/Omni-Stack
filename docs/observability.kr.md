# Omni-Stack 관측성 운영 및 유지보수

이 문서는 지표, 추적, 구조화 로그, 알림, SLO의 현재 엔지니어링 경계를 정의합니다. 관측은 기본적으로 비활성이며 앱 시작의 전제 조건이 아닙니다.

개념과 7개 대시보드·수집 대상·로그·실제 트레이스·경보 화면의 그림 설명은 [Wiki 관측 가능성 장](wiki/observability-principles.kr.md)을 읽으세요.

## 1. 데이터 흐름과 경계

| 시그널 | 애플리케이션 출구 | 로컬 백엔드 | 조회 입구 |
|---|---|---|---|
| 지표 | `/actuator/prometheus`; 마이그레이터 종료 시 푸시 | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP, W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| 로그 | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| 알림 | Prometheus rules | Alertmanager | Alertmanager / 외부 receiver |

`X-Trace-Id` 는 레거시 클라이언트와 로그 검색을 위한 호환 응답 헤더일 뿐입니다. 트레이싱 활성화 시 현재 Micrometer/OTel traceId 와 같으며; 서비스 간 전파는 W3C `traceparent` 를 기준으로 합니다. Gateway 는 클라이언트가 위조한 신원 헤더를 계속 덮어쓰지만, 표준 Trace 컨텍스트는 파괴하지 않습니다.

관리 엔드포인트는 Gateway 를 거치지 않습니다. Compose 에서 애플리케이션 포트는 컨테이너 내 `8080` 으로 통일되지만, Actuator 는 독립적인 관리 포트 `MANAGEMENT_SERVER_PORT`(서비스별 `19900`~`19907`, auth/base/gateway/workflow/crm/srm/procurement/asset 에 대응) 를 사용하며 두 포트는 공유되지 않습니다. 관리 포트는 호스트에 매핑되지 않고 내부 Compose 네트워크를 통해 Prometheus 만 스크레이프합니다. Gateway 비즈니스 포트를 제외하면 서비스 호스트 포트는 모두 `127.0.0.1` 에 바인딩됩니다. 프로덕션에서는 네트워크 격리를 계속 적용하고 Actuator 인증을 활성화해야 합니다.

## 2. 시작과 중지

observability profile과 명시적 내보내기 설정이 관측 기능을 제어하며 기본값은 비활성입니다. 활성화해도 업무 포트를 바꾸지 않습니다. 관리 엔드포인트를 공개하면 안 됩니다. 지표는 별도 management 포트를 쓰므로 업무 포트의 404는 지표 누락 증거가 아닙니다. 시작·포트·상태 확인과 안전한 종료는[운영](guides/operations-upgrade.kr.md)을 참조합니다.

## 3. 대시보드와 지표 계약

Grafana 는 7 개의 읽기 전용 대시보드를 자동 로드합니다: Platform Overview, Service RED, JVM and Pools, Feign Clients, MQ and Outbox, Workflow, Database Migrations. JSON 은 `observability/grafana/dashboards/` 에, 데이터 소스와 대시보드 provider 는 `observability/grafana/provisioning/` 에 있습니다.

Alloy는 Docker 레이블 `com.docker.compose.project`로 로그 대상을 필터링하고 현재 Compose 프로젝트의 로그만 수집합니다. 필터 값은 `COMPOSE_PROJECT_NAME`에서 전달된 `OMNI_COMPOSE_PROJECT`를 사용하며 `docker compose -p <name>` 또는 명시한 `COMPOSE_PROJECT_NAME`을 따릅니다. Docker discovery는 컨테이너 메타데이터를 발견하지만 로그 수집은 필터링 후 수행하므로 다른 Compose 프로젝트의 로그는 수집하지 않습니다.

허용되는 지표 레이블은 다음뿐입니다: `service.name`, `environment`, `instance`, HTTP method/route template/status, exception class, MQ destination/result, 그리고 코드 내의 닫힌 열거 operation/status. 데이터베이스 마이그레이션 정보는 저장소 통제하의 schema version 을 추가로 사용할 수 있습니다. tenantId, userId, username, businessKey, 원시 URL, 요청 본문, 메시지 본문, 동적 SQL, 테이블 이름, 연결 주소를 레이블에 넣어선 안 됩니다. 새 레이블은 먼저 값 집합이 고정됨을 증명하고 카디널리티 상한을 평가해야 합니다.

현재 커스텀 Outbox 지표:

- `omni_mq_outbox_messages{status}`: pending/sent/failed/dead_letter 수량.
- `omni_mq_outbox_oldest_age_seconds`: 가장 오래된 pending/failed 메시지의 경과 시간.
- `omni_mq_outbox_operations_total{destination,result}`: enqueued/sent/retry/dead_letter 결과.

기타 커스텀 지표:

- `omni_job_registrations_total{result}`, `omni_job_executions_total{result}`: XXL-JOB 등록 및 사용자 작업 실행 결과.
- `omni_workflow_start_operations_total{result}`, `omni_workflow_approval_operations_total{result}`: 프로세스 시작 및 승인 결과.
- `omni_workflow_approval_backlog`, `omni_workflow_approval_duration_seconds`, `omni_workflow_process_duration_seconds`: 미결 백로그, 단일 승인 처리, 프로세스 엔드투엔드 소요 시간.
- `omni_procurement_workflow_start_retries_total{result}`: 구매 요청 Workflow 시작 재시도 결과.
- `omni_inbox_operations_total{destination,result}`: SRM, 조달, 자산 Inbox 성공 또는 Broker 재시도 트리거.
- `omni_db_migration_operations_total{result}`, `omni_db_migration_duration_seconds`, `omni_db_schema_version_info{version}`: 일회성 마이그레이터가 종료 시 푸시하는 결과, 소요 시간, 통제된 매니페스트 버전.

Outbox 생성 시 실제 traceId 를 `producer_trace_id` 로 저장하고, 전송 시 `omniProducerTraceId` 와 `omniMessageId` 메시지 헤더로 컨슈머에 전달합니다; 이력에 프로듀서 traceId 가 없어도 정상 전달됩니다. 릴레이는 전달마다 새 relay span 을 만들고, 로그에는 `msgId`, `producerTraceId`, `relayTraceId` 와 고정된 destination/result 만 출력합니다; Asset Consumer 는 메시지 헤더에서 연관 필드를 꺼내 `msgId`, `producerTraceId` 와 자신의 `consumerTraceId` 를 기록합니다. 어느 쪽도 payload, 테넌트, 비즈니스 키를 출력하지 않습니다. `msgId` 로 프로듀서 로그에서 릴레이 및 소비 로그로 이동한 뒤 각각 해당 Trace 를 조회할 수 있습니다.

### Alertmanager UI 경계

`alertmanager-ui`는 공식 v0.30.1 Elm 소스에서 네 언어 UI를 빌드하고 같은 `alertmanager:9093` 백엔드로 프록시합니다. 호스트 진입점은 `127.0.0.1:9093`을 유지하며 백엔드에서 같은 포트를 중복 공개하지 않습니다. UI 경로는 `/zh-CN/`, `/en-US/`, `/ja-JP/`, `/ko-KR/`이며 언어를 바꿔도 hash 경로와 필터를 유지합니다. API 필드, 레이블, 상태 열거 값, 수신기 이름은 원래 값을 유지하고 요일 설정도 업스트림의 영어 프로토콜 값으로 저장합니다. 빌드는 업스트림 아카이브 SHA-256을 고정하고 Apache-2.0 LICENSE/NOTICE를 보존합니다. 문구 누락이나 구조 패치 불일치 시 빌드가 실패합니다. 출처와 유지보수 결정은 [ADR-0004](adr/0004-native-alertmanager-localization.md), 절차와 이미지는 [운영 가이드](guides/operations-upgrade.kr.md)를 참조합니다. 업무 서비스나 공개 관리 진입점을 추가하는 변경은 아닙니다.

## 4. 알림과 임계값 보정

`observability/prometheus/alerts.yml` 에는 서비스 사용 불가, 5xx, P95/P99, 커넥션 풀, Outbox 백로그/데드레터, Workflow/XXL-JOB/Inbox 실패, 디스크, 컨테이너 재시작, 메모리, JVM 데드록 규칙이 포함됩니다. 파일의 5%, 1초, 2초, 85%, 5분 등은 모두 로컬 예시 임계값이며 프로덕션 약속이 아닙니다.

프로덕션 릴리스 전 반드시:

1. 최소 7 일간의 대표 트래픽으로 기준선과 일/주 주기를 계산합니다.
2. 서비스별로 서로 다른 지연과 오류 예산을 설정해, 하나의 전역 임계값이 차이를 가리는 것을 피합니다.
3. 실제 Alertmanager receiver, 온콜, 에스컬레이션 정책을 구성합니다; 자격 증명은 Secret 관리에서만 가져오고 저장소에 커밋해선 안 됩니다.
4. service down, 5xx, Outbox 데드레터, receiver 실패를 훈련합니다.
5. 임계값 책임자, 보정 날짜, 다음 검토 날짜를 기록합니다.

## 5. SLO 템플릿

각 프로덕션 서비스는 다음 템플릿을 복사해 비즈니스와 플랫폼이 공동으로 확인합니다:

| 필드 | 예시 | 필수 설명 |
|---|---|---|
| 서비스/사용자 여정 | `omni-procurement / 구매 요청 제출` | 사용자 결과로 명명 |
| SLI | 성공한 비 5xx 요청 / 유효 요청 | 헬스 체크와 클라이언트 취소를 명시적으로 제외 |
| 목표 | 30 일 99.9% | 예시를 그대로 베끼면 안 됨 |
| 지연 목표 | P95 < 800ms, P99 < 2s | route template 으로 집계 |
| 오류 예산 | 30일간 유효 요청 수 × 0.1% | 이 표는 요청 수 기준이다. 시간 기반 가용성으로 측정할 때만 30일 99.9%가 43m12s에 해당하며 두 예산은 서로 바꿔 쓸 수 없다 |
| 빠른 소진 알림 | 1h 창, 14.4x | 짧은 창/긴 창을 동시에 요구 |
| 느린 소진 알림 | 6h 창, 6x | 온콜 응답 레벨에 바인딩 |
| 책임자 | 팀과 온콜 표 | 연락 가능해야 함 |
| 보정 날짜 | YYYY-MM-DD | 분기별 또는 대형 버전 후 검토 |

## 6. 보안과 프로덕션화

- 로컬 Grafana 기본 자격 증명은 초기 상태 확인 전용; 공유나 프로덕션 환경에서는 `.env`/Secret 에서 덮어쓰고 SSO/TLS 를 활성화해야 합니다.
- Alloy 는 컨테이너 stdout 수집을 위해 Docker socket 을 읽기 전용 마운트합니다. Docker socket 은 고권한 제어면과 동등하므로, 프로덕션에서는 제한된 로그 에이전트, 최소 권한, 독립 수집 노드로 전환해야 합니다.
- 로컬 보존 기간은 기본으로 Prometheus 7 일, Tempo 24 시간, Loki 7 일입니다. 프로덕션 용량, 백업, 암호화, 보존 정책은 재설계해야 합니다.
- 관리 포트, OTLP, Grafana, Prometheus, Pushgateway, Exporter, Loki, Tempo, Alertmanager 를 인터넷에 직접 노출해선 안 됩니다. 프로덕션 Pushgateway 는 TLS/인증을 활성화하고 푸시 출처를 제한해야 합니다.
- ECS 로그에 토큰, 비밀번호, Cookie, Authorization, 요청 본문, 메시지 본문, 원시 예외의 Secret 을 기록해선 안 됩니다.

## 7. 검증과 문제 해결

설정, 실행 상태, Trace·로그 연결, 알림 전달과 성능은 별도로 검증합니다. 구문 통과만으로 targets UP이나 알림 도착을 입증할 수 없습니다. 시작하지 않은 서비스는 선택 preset과 비교하며 모든 DOWN을 무시하지 않습니다. 현재 소스, 이미지, preset, 시간, 실제 실패와 범위를 기록합니다. 과거 검증은 당시 기준만 증명합니다. 절차와 복원 범위는[운영](guides/operations-upgrade.kr.md)을 참조합니다.
