# 프리셋, 경량 모드, 서비스 생성과 CRUD 생성

새 프로젝트는 CLI와 모듈 카탈로그를 사용하고 기존 서비스를 복사해 전체 문자열을 바꾸지 않습니다.

## 1. 프리셋 선택

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

프리셋은 백엔드, 프론트엔드, seed, migration, Compose profile, 외부 의존을 선택합니다. `supply-chain`은 SRM, Procurement, Asset을 포함합니다. [프리셋 선택](../preset-quick-selection.kr.md)과 [의존성 매트릭스](../preset-dependency-matrix.kr.md)를 참고하세요.

## 2. 경량 개발

`dev` 하위 명령은 `up`, `down`, `status`, `doctor`, `logs` 다섯 개뿐입니다. `dev plan`은 존재하지 않으며 계획은 `dev up`이 Docker 실행 전에 출력합니다.

```bash
npm --prefix tools/omni-cli run dev -- dev doctor
npm --prefix tools/omni-cli run dev -- dev up --preset crm
npm --prefix tools/omni-cli run dev -- dev status
npm --prefix tools/omni-cli run dev -- dev logs --module crm --tail 200
npm --prefix tools/omni-cli run dev -- dev down
```

`dev up`은 `development preset|module: <id>`, `mode: lite|full`, `modules:`, `services:`, `observability:`와 축소 정보를 이 순서로 출력하며 이것이 실행의 사실원입니다. 모드는 preset/모듈에서 추론(`core`, `workflow`, `crm`은 lite)되고 `--mode lite|full`로 같은 preset을 전환할 수 있어 두 모드를 동일한 계약으로 비교할 수 있습니다. SRM/Procurement/Asset을 포함하는 폐쇄는 실시간 소비자를 조용히 생략하지 않고 lite를 거부합니다. lite는 Nacos 클라이언트, XXL-JOB, MQ relay와 자동 탐지를 명시적으로 끄고(Outbox 기록은 계속), full은 이 스위치를 CLI에서 합성하지 않으므로 `.env`가 권위입니다.

병렬 격리는 `--project-name`(컨테이너·네트워크·볼륨에 이름을 주는 Compose project)과 `--set NAME=VALUE`(Compose가 선언한 호스트 포트와 볼륨 이름만, 예: `OMNI_MYSQL_HOST_PORT=23306`, `OMNI_MYSQL_VOLUME_NAME=wp011-row1-mysql`)로 합니다. Secret은 거부되며 값은 로그에 남지 않습니다.

명명 볼륨은 기본으로 보존됩니다. `dev down`은 `--volumes`, `--confirm-delete-volumes`, `--project-name`이 모두 주어질 때만 삭제하고, 먼저 각 볼륨의 `com.docker.compose.project` 라벨을 확인합니다(`omni-stack-mysql-data` 같은 고정 이름은 project를 넘어 공유되기 때문). project를 지정하지 않으면 Compose가 디렉터리 이름에서 유도하고 삭제 경로는 사용 불가입니다.

## 3. 서비스 생성

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

`create-service` 는 기본으로 미리보기 전용이며 아무것도 쓰지 않습니다. 계획을 검토한 후 **명시적으로 `--apply` 를 추가**해야 원자 쓰기(Maven 모듈, 앱, 설정, health, Docker, 프론트/권한/문서 통합, 잠금 생성)가 수행됩니다: `npm --prefix tools/omni-cli run dev -- create-service inventory --apply`. 원자 쓰기는 기본으로 저장소 현재 작업 공간에 기록되며, `--output` 으로 디렉터리를 지정하면 실제 출력 디렉터리에 맞춰 후속 빌드·통합 명령의 cwd 를 조정해야 합니다. `--apply` 전에는 모듈이 없어 먼저 컴파일하면 실패합니다. 생성 후 JDK 25로 검증합니다.

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

`@RequestBody`가 있는 새 서비스는 `XssConfigProvider`를 구현합니다. Servlet은 `omni-common-service`를 사용하고 Gateway reactive 보안을 복사하지 않습니다.

## 4. CRUD 생성

```bash
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --check
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --dry-run
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --apply
```

명령은 `generate crud --spec <file>`이며 `crud plan`/`crud generate`는 존재하지 않습니다. `--check`는 선언 검증만, `--dry-run`은 변경 계획 렌더링, `--apply`에서야 파일 간 원자적 쓰기를 수행하며 `--apply`와 `--dry-run`은 상호 배타입니다.

타입, API, 서비스, 영속성, 권한, migration, 테스트, i18n 작업을 생성합니다. 상태 머신, 데이터 범위, 멱등성, 업무 검증은 개발자가 추가합니다.

## 5. DB와 권한

forward-only Liquibase changeSet, 안정 seed, `scripts/sql/seed/auth.sql` 권한, `database/seed/manifest.yaml`, fresh/upgrade 두 경로를 갱신합니다. `migrate-*.sql`이나 임시 복구 스크립트를 공식 산출물로 추가하지 않습니다.

## 6. 사용자 프리셋

의존 폐쇄, 충돌, 전후단 선택, migration, seed를 선언하고 5개 프리셋 골든 매트릭스와 잠금을 갱신합니다. [사용자 프리셋](../custom-preset-tutorial.kr.md)과 [유지보수](../preset-maintenance.kr.md)를 참고하세요.

## 7. 완료 조건

JDK 25 Reactor, lint 0 warning, production build, 권한 부정 테스트, fresh/upgrade/대상 프리셋, 4개 언어 문서와 이미지, 임시 산출물 0을 만족합니다. 여기에 `omni dev doctor`에 FAIL이 없어야 합니다(미실행 시 SKIP은 실패가 아니지만 증거도 아님).

