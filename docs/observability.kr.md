# Omni-Stack 관측성 운영 및 유지보수

본 문서는 WP-07 의 운영 사실 소스로, 애플리케이션 지표, 분산 트레이싱, 구조화 로그, 로컬 관측 스택, 알림, SLO 템플릿을 설명합니다. 관측 기능은 기본적으로 비활성화되어 있으며 애플리케이션 시작의 전제 조건이 아닙니다.

## 1. 데이터 흐름과 경계

| 시그널 | 애플리케이션 출구 | 로컬 백엔드 | 조회 입구 |
|---|---|---|---|
| 지표 | `/actuator/prometheus`; 마이그레이터 종료 시 푸시 | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP, W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| 로그 | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| 알림 | Prometheus rules | Alertmanager | Alertmanager / 외부 receiver |

`X-Trace-Id` 는 레거시 클라이언트와 로그 검색을 위한 호환 응답 헤더일 뿐입니다. 트레이싱 활성화 시 현재 Micrometer/OTel traceId 와 같으며; 서비스 간 전파는 W3C `traceparent` 를 정으로 합니다. Gateway 는 클라이언트가 위조한 신원 헤더를 계속 덮어쓰지만, 표준 Trace 컨텍스트는 파괴하지 않습니다.

관리 엔드포인트는 Gateway 를 거치지 않습니다. Compose 에서 비즈니스 서비스의 Actuator 는 애플리케이션과 컨테이너 내 `8080` 을 공유하고, Prometheus 만 내부 Compose 네트워크를 통해 스크레이프합니다; Gateway 비즈니스 포트를 제외하면 서비스 호스트 포트는 모두 `127.0.0.1` 에 바인딩됩니다. 프로덕션에서는 독립 관리 포트, 네트워크 정책, Actuator 인증을 권장합니다.

## 2. 시작과 중지

먼저 `.env.example` 에서 완전한 `.env` 를 만든 뒤, 저장소 루트에서 실행합니다:

```powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
```

CLI 는 본 프로세스에 `OTLP_EXPORT_ENABLED=true`, ECS JSON 로그, 로컬 100% 샘플링을 설정하고, 일회성 마이그레이터가 종료 시 결과를 Pushgateway 로 푸시하게 합니다. `--observability` 가 없으면 OTLP 와 마이그레이션 지표 푸시가 모두 비활성화되고 샘플링률은 0 이며, 애플리케이션은 Span 을 내보내지 않고 정상 시작합니다.

로컬 입구:

| 컴포넌트 | 주소 | 기본 용도 |
|---|---|---|
| Grafana | `http://127.0.0.1:3001` | 대시보드, 로그, Trace |
| Prometheus | `http://127.0.0.1:9090` | Targets, PromQL, 규칙 |
| Pushgateway | `http://127.0.0.1:9091` | 단기 수명 마이그레이션 지표 |
| Node Exporter | `http://127.0.0.1:9100` | 로컬 노드 및 파일 시스템 지표 |
| cAdvisor | `http://127.0.0.1:8088` | 컨테이너 리소스 및 수명주기 지표 |
| Alertmanager | `http://127.0.0.1:9093` | 알림 상태 |
| Tempo | `http://127.0.0.1:3200` | Trace API |
| Loki | `http://127.0.0.1:3100` | 로그 API |
| Alloy | `http://127.0.0.1:12345` | 로그 수집 상태 |
| OTLP | `127.0.0.1:4317/4318` | gRPC/HTTP 수신 |

중지는 기본적으로 관측 데이터 볼륨을 보존합니다:

```powershell
npm --prefix tools/omni-cli run dev -- dev down
```

로컬 지표, Trace, 로그, 데이터베이스 데이터가 더 이상 필요 없음을 확인했을 때만 모든 이름 있는 볼륨을 명시적으로 삭제할 수 있습니다:

```powershell
npm --prefix tools/omni-cli run dev -- dev down --volumes --confirm-delete-volumes
```

## 3. 대시보드와 지표 계약

Grafana 는 7 개의 읽기 전용 대시보드를 자동 로드합니다: Platform Overview, Service RED, JVM and Pools, Feign Clients, MQ and Outbox, Workflow, Database Migrations. JSON 은 `observability/grafana/dashboards/` 에, 데이터 소스와 대시보드 provider 는 `observability/grafana/provisioning/` 에 있습니다.

Alloy 는 자신과 동일한 `COMPOSE_PROJECT_NAME` 레이블의 컨테이너 로그만 수집합니다. `docker compose -p <name>` 을 사용하거나 `COMPOSE_PROJECT_NAME` 을 설정하면 이 필터 값이 동기적으로 바뀌며, 호스트의 다른 Compose 프로젝트를 스캔하지 않습니다.

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
| 오류 예산 | 30 일 43m12s | 목표에서 자동 계산 |
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

구성 수준 검증:

```powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
```

실행 후 Prometheus `/targets` 에서 시작된 애플리케이션이 UP 인지 확인합니다; 현재 프리셋에 포함되지 않은 서비스가 DOWN 으로 표시되는 것은 예상된 현상입니다. Grafana 에 데이터가 없으면 애플리케이션 `/actuator/prometheus`, Prometheus target, OTel Collector 로그, Tempo/Loki readiness, Alloy targets 순으로 확인합니다.

성능 검증은 동일 fixture 로 관측 비활성화 시와 활성화 시의 시작 시간, CPU, 메모리, 처리량, P95/P99 를 각각 측정합니다. 결과는 `docs/evidence/scaffold-upgrade/` 에 기록합니다; 로컬 100% 샘플링은 검증 전용이며 프로덕션 샘플링률은 용량과 SLO 로 보정합니다.
