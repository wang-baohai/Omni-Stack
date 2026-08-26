# 운영, 관측성, 백업, 복구와 업그레이드

로컬 검증과 운영 계획 안내입니다. 운영에서는 용량, 규정, 책임에 맞게 재구성하고 로컬 Compose 포트를 그대로 공개하지 않습니다.

## 1. 시작 순서

```text
MySQL / Redis / RocketMQ → Nacos / XXL-JOB → Liquibase migrator
→ Auth / Base / Workflow / 업무 서비스 → Gateway / Frontend
```

`docker compose --profile full up -d` 후 컨테이너 시작이 아니라 health를 확인하고 migrator 성공 후 트래픽을 받습니다.

## 2. 관측성 profile

```bash
docker compose --profile full --profile observability up -d
```

OpenTelemetry Collector, Prometheus, Alertmanager, Tempo, Loki, Alloy, Grafana, Pushgateway, Node Exporter, cAdvisor와 대시보드를 시작합니다. 동기는 W3C `traceparent`, `X-Trace-Id`는 실제 trace, 비동기는 producer/consumer trace를 연결합니다.

운영 샘플링, 보존, 임계값, 수신자는 용량에 맞춥니다. observability를 끄더라도 업무는 정상 시작하고 Span을 내보내지 않습니다.

## 3. 상태와 알림

앱 health, Prometheus targets, Gateway/Feign 오류와 지연, JVM/풀, Outbox/Inbox, 데드레터, XXL-JOB, Workflow, migration을 감시합니다.

## 4. 백업

MySQL, Nacos 영속 설정, 외부 Secret 목록, Grafana/알림 설정, 배포 버전, 이미지 digest, `database/seed/manifest.yaml`, `omni-scaffold.lock`을 보호합니다. Redis, MQ, Tempo, Loki는 RPO/RTO에 따라 결정합니다.

## 5. 복구 훈련

격리 환경에 복원하고 운영 하류를 차단한 뒤 migration, 시작, health, 로그인, 권한, 업무, 비동기, 데이터 수를 검증하고 실제 복구 시간을 기록합니다. 복구 성공 증거가 없는 백업은 사용 가능하지 않습니다.

## 6. 업그레이드

expand → migrate/backfill → contract를 따릅니다. 릴리스 검토, 백업 복구, 운영 유사 데이터 upgrade, 호환 Schema, 양쪽 구조 지원 앱, 관측, 구버전 종료 후 contract 순서입니다. 실행한 changeSet을 바꾸지 않고 백업 복원이나 보상 changeSet을 사용합니다.

## 7. 보안

관리 포트는 내부 또는 localhost로 제한하고 TLS, reverse proxy/WAF, 최소 권한, 외부 Secret 관리를 사용합니다. Secret을 Git, 이미지 레이어, 스크린샷, 명령 기록, 로그에 남기지 않습니다. [Docker 배포](../docker-deployment.kr.md)와 [관측성](../observability.md)을 참고하세요.

