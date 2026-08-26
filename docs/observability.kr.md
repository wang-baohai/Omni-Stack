# Omni-Stack 관측성 운영 및 유지보수

이 문서는 WP-07 운영 사실 원본입니다. 메트릭, 분산 추적, 구조화 로그, 선택형 로컬 관측 스택, 알림과 SLO 템플릿을 설명합니다. 관측성은 기본 비활성화이며 애플리케이션 시작의 필수 조건이 아닙니다.

## 1. 데이터 흐름과 경계

| 신호 | 애플리케이션 출력 | 로컬 백엔드 | 조회 진입점 |
|---|---|---|---|
| 메트릭 | `/actuator/prometheus`, 마이그레이터 종료 push | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP, W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| 로그 | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| 알림 | Prometheus rules | Alertmanager | Alertmanager / 외부 receiver |

`X-Trace-Id`는 호환 응답 헤더입니다. 서비스 간 전파는 W3C `traceparent`를 사용합니다. 관리 엔드포인트는 Gateway를 통과하지 않습니다. 운영 환경에서는 관리 포트, 네트워크 정책과 인증을 분리하세요.

## 2. 시작과 중지

`.env.example`에서 완전한 `.env`를 만든 뒤 저장소 루트에서 실행합니다.

```powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
```

이 옵션은 OTLP, ECS JSON, 로컬 100% 샘플링과 마이그레이션 결과 push를 켭니다. 옵션이 없어도 애플리케이션은 정상 실행됩니다.

Grafana `:3001`, Prometheus `:9090`, Pushgateway `:9091`, Node Exporter `:9100`, cAdvisor `:8088`, Alertmanager `:9093`, Tempo `:3200`, Loki `:3100`, Alloy `:12345`, OTLP `:4317/4318`은 모두 `127.0.0.1` 또는 내부 네트워크 전용입니다.

`dev down`은 볼륨을 보존합니다. 데이터가 필요 없음을 확인한 경우에만 `--volumes --confirm-delete-volumes`를 사용합니다.

## 3. Dashboard와 메트릭 계약

Grafana는 Platform Overview, Service RED, JVM and Pools, Feign Clients, MQ and Outbox, Workflow, Database Migrations의 읽기 전용 Dashboard 7개를 자동 등록합니다.

Alloy는 같은 `COMPOSE_PROJECT_NAME`의 컨테이너만 수집합니다. 라벨은 service, environment, instance, HTTP method/route template/status, exception class, 고정 MQ destination/result와 닫힌 열거값으로 제한합니다. tenant, user, business key, 원본 URL, payload, SQL, 테이블명이나 연결 주소를 라벨로 사용하면 안 됩니다.

사용자 정의 메트릭은 Outbox, XXL-JOB, Workflow, Procurement retry, Inbox와 DB migration을 다룹니다. `msgId`로 producer·relay·consumer Trace를 연결하며 payload와 테넌트 정보는 로그에 남기지 않습니다.

## 4. 알림과 임계값 보정

`observability/prometheus/alerts.yml`의 가용성, 5xx, 지연, 연결 풀, Outbox, Workflow, 작업, 디스크, 재시작, 메모리, JVM deadlock 값은 로컬 예시입니다. 운영 전 최소 7일의 대표 트래픽으로 서비스별 보정하고 Secret을 통해 receiver와 에스컬레이션을 구성하며 장애 훈련, 담당자와 검토일을 기록합니다.

## 5. SLO 템플릿

각 운영 서비스는 사용자 여정, SLI, 목표 기간, P95/P99, 계산된 오류 예산, 단기/장기 burn-rate 알림, 연락 가능한 담당자와 보정일을 정의합니다. 측정 없이 예시 목표를 복사하지 마세요.

## 6. 보안과 운영 준비

- 로컬 Grafana 자격 증명을 교체하고 SSO/TLS를 사용합니다.
- Docker socket은 높은 권한이므로 운영에서는 제한된 로그 에이전트를 사용합니다.
- 보존 기간, 용량, 암호화와 백업을 운영 요구에 맞게 다시 설계합니다.
- 관측 관리 포트를 인터넷에 직접 공개하지 않습니다.
- token, password, Cookie, Authorization, 본문이나 Secret을 로그에 기록하지 않습니다.

## 7. 검증과 문제 해결

```powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
```

시작 후 Prometheus target, 애플리케이션 메트릭, Collector 로그, Tempo/Loki readiness, Alloy target 순서로 확인합니다. 같은 fixture로 관측 비활성/활성 시 시작 시간, CPU, 메모리, 처리량과 지연을 비교하고 `docs/evidence/scaffold-upgrade/`에 기록합니다.
