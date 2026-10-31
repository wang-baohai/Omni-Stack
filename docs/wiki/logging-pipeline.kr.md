# 3. 수집 경로: 컨테이너에서 요청까지

앱이 표준 출력에 기록하면 Docker가 컨테이너 로그를 보관하고, Alloy가 대상을 발견해 필터링한 뒤 Loki로 보냅니다. Grafana는 각 업무 컨테이너에 직접 연결하지 않고 Loki에서 결과를 읽습니다.

## 1단계: 현재 프로젝트만 남기기

Alloy 재레이블 설정의 앞부분입니다. 발췌는 첫 번째 필터 규칙 끝까지입니다.

<!-- omni:source path="observability/alloy/config.alloy" -->
```hcl
discovery.relabel "containers" {
  targets = discovery.docker.local.targets

  rule {
    source_labels = ["__meta_docker_container_label_com_docker_compose_project"]
    regex         = sys.env("OMNI_COMPOSE_PROJECT")
    action        = "keep"
  }
```

`keep`은 Compose 프로젝트 레이블이 일치하는 대상만 남깁니다. OMNI_COMPOSE_PROJECT는 현재 Compose 프로젝트 이름에서 옵니다. 메타데이터 발견이 모든 컨테이너 로그 수집을 뜻하지는 않습니다. 실제 로그 소스는 필터링된 대상을 사용합니다.

<!-- omni:source path="observability/alloy/config.alloy" -->
```hcl
loki.source.docker "local" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.containers.output
  forward_to = [loki.write.local.receiver]
}
```

이후 로컬 쓰기 구성요소가 Loki로 전송합니다. 대상 이름이 맞아도 로그가 없다면 실제 앱 출력, Docker 접근, Loki 쓰기를 확인해야 합니다. 컨테이너가 Running이라는 사실만으로 경로 성공을 증명할 수 없습니다.

## 2단계: 요청의 연결 단서 남기기

공통 Servlet 필터는 현재 스레드의 로그 문맥에 traceId를 넣고 종료할 때 제거합니다.

<!-- omni:source path="omni-backend/omni-common/src/main/java/com/omni/common/web/TraceIdFilter.java" -->
```java
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
```

MDC는 “지금 처리 과정이 가지고 다니는 작은 메모”라고 생각하면 됩니다. finally에서 지워야 재사용된 스레드가 이전 요청의 ID를 다음 요청에 남기지 않습니다. 실제 추적 Span이 있으면 우선 사용하고, 추적이 꺼져도 호환 ID가 생길 수 있습니다. ID만으로 Tempo에 저장됐다고 단정하지 마세요.

## 3단계: 서비스와 메시지 경계 넘기

동기 호출은 표준 W3C traceparent로 추적 문맥을 전달합니다. 메시지는 대기, 재시도, 소비를 거치므로 먼저 msgId로 같은 메시지를 찾고 생산자, 중계, 소비자 추적 ID를 각각 확인합니다. 하나의 연속된 Trace일 필요는 없습니다.

서비스와 시간 범위를 좁힌 뒤 ID를 검색합니다. 결과가 없다면 출력, 프로젝트 필터, 수집 전송, 저장, 조회 조건 순서로 확인합니다. 조회 오류를 “기록 0개”로 취급하지 마세요.

다음은 [작업 기록과 보안 감사](logging-audit.kr.md)를 구분합니다.
