# 코드 복제에서 첫 로그인까지

기준: main fc165e3 이후 0.6.x. 이번 브랜치는 문서와 도구를 추가합니다. 동작은 현재 checkout의 Compose, 소스, seed가 정의합니다. 첫 빌드는 네트워크, CPU, 캐시에 따라 달라지며 5분을 보장하지 않습니다.

## 1. 코드 복제와 디렉터리

호스팅 한 곳을 골라 빈 위치에 복제합니다. 같은 디렉터리에 두 명령을 연속 실행하지 않습니다. 이후 명령은 저장소 루트에서 실행합니다.

GitHub:

```bash
git clone --branch main https://github.com/wang-baohai/Omni-Stack.git
cd Omni-Stack
```

Gitee:

```bash
git clone --branch main https://gitee.com/wang-baohai/Omni-Stack.git
cd Omni-Stack
```

`omni-backend/`는 Maven 모듈, `omni-frontend/`는 Vue 소스입니다. `docker/`와 Compose가 컨테이너를 빌드하고 실행합니다. `database/`는 구조 마이그레이션, `scripts/sql/seed/`는 공식 초기 데이터, `tools/omni-cli/`와 `scaffold/`는 프로젝트 생성을 담당합니다. `docs/`는 기술 참조와 Wiki, `ai/`는 공유 AI 자산입니다.

## 2. Docker와 도구 컨테이너

호스트에는 Git, Compose가 있는 Docker Desktop/Engine, 편집기가 필요합니다. 호스트 JDK/Node 설치는 필수가 아니며 도구 컨테이너가 JDK 25, Node 22, Maven Wrapper를 제공합니다. 로컬 Linux engine을 사용합니다. [프리셋 표](../preset-dependency-matrix.kr.md)의 자원 예산을 Docker에 할당합니다. 전체는 메모리 12–16 GiB와 빈 디스크 약 20 GiB로 계획을 시작하고 실제 이미지와 빌드 피크에 맞춰 조정합니다. 측정된 한도는 아닙니다.

```bash
docker version
docker compose version
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
```

Docker client/server가 모두 응답하고 Compose 버전과 도구 이미지 빌드 성공을 확인해야 합니다. 레지스트리/Maven/npm 시간 초과는 프록시, DNS, Docker 네트워크를 확인합니다. 권한 실패는 플랫폼 승인으로 처리하며 daemon 정지로 단정하지 않습니다.

## 3. 새 환경의 비공개 설정

컨테이너 스크립트가 `.env.example`에서 모든 필수 비밀을 안전하게 생성합니다. 새 `.env`만 만들고 기존 파일을 덮어쓰거나 값을 출력하지 않습니다. 기존 데이터 볼륨은 원래 자격 정보와 [업그레이드/복구 절차](operations-upgrade.kr.md)를 사용하며 이전 DB에 새 암호를 생성하지 않습니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node scripts/init-env.mjs
```

출력은 생성 확인뿐입니다. 실제 `.env`는 Git에서 제외됩니다. 네 언어는 같은 키 이름을 사용합니다.

| 변수 | 용도와 제약 |
|---|---|
| `MYSQL_ROOT_PASSWORD` / `OMNI_DB_PASSWORD` | 마이그레이션 관리자 / 일곱 업무 DB의 앱 사용자. 새 환경에서 migrator가 생성하고 권한 부여 |
| `REDIS_PASSWORD` | Redis 및 모든 클라이언트가 일치 |
| `NACOS_AUTH_TOKEN` | 최소 32 랜덤 바이트의 Base64, 서버 서명 |
| `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE` | Nacos 서버 신원 인증 |
| `NACOS_PASSWORD` | 콘솔과 클라이언트 공통 암호. 기본 사용자 nacos |
| `XXL_JOB_ACCESS_TOKEN` / `XXL_JOB_ADMIN_PASSWORD` | 실행기 token / 콘솔 랜덤 암호. 기본 관리자 admin, 14–20자 |
| `GATEWAY_ASSERTION_SECRET` / `OMNI_INTERNAL_API_TOKEN` | 전체 공통 Gateway HMAC(최소 32바이트) / 내부 API 인증 |
| `OAUTH2_STATE_SECRET` / `JWK_ENCRYPT_KEY` | 소셜 state HMAC(최소 32바이트) / Redis JWK 암호 키(정확히 32자) |

GitHub, Google, Gitee OAuth ID/secret은 선택적인 외부 앱 자격 정보입니다. 암호 로그인에는 필요하지 않습니다. 빈 설정으로 외부 소셜 로그인을 사용할 수는 없습니다.

## 4. 선택하고 시작

전체 공급망 또는 모든 기능은 full을 선택합니다. 첫 `--build`는 의존성을 다운로드하고 이미지 안에서 빌드합니다. migrator가 성공 종료한 뒤 건강 조건으로 서비스를 시작합니다.

```bash
docker compose --profile full up -d --build
docker compose ps -a
docker compose logs omni-db-migrator
```

로그인, 권한, 기본 플랫폼 학습은 core로 시작할 수 있습니다. CLI는 의존 폐쇄, 서비스, 비활성 기능을 출력합니다. core lite는 Nacos client, XXL-JOB, MQ relay를 끄므로 공급망 검증에는 사용할 수 없습니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset explain core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset core --project-name omni-stack --build
```

이는 선택 서비스의 시작입니다. 잘라낸 별도 프로젝트 생성은 [개발 안내](scaffold-development.kr.md)를 참조합니다. `preset create`와 `dev up`의 결과는 다릅니다.

## 5. 마이그레이션과 건강 확인

`omni-db-migrator`는 Exited (0), healthcheck를 선언한 선택 컨테이너는 healthy여야 하며 프런트엔드에도 접근할 수 있어야 합니다. 선택하지 않은 서비스는 없어도 됩니다. 실패하면 migrator 또는 해당 서비스 로그, 필수 변수, 포트를 조사합니다. 볼륨 삭제로 문제를 숨기지 않습니다. 표준 project는 omni-stack이고 기본 포트는 frontend 3000, Gateway 8102, MySQL 3306으로 고정됩니다.

```bash
docker compose ps -a
docker compose logs --tail 100 omni-auth omni-gateway omni-frontend
```

## 6. 첫 로그인과 일회용 CAPTCHA

1. 브라우저에서 `http://localhost:3000`을 엽니다.
2. 개발 테넌트를 고르고 공식 `auth.sql`의 로컬 예제 `admin` / `admin123`을 사용합니다.
3. 사람이 현재 이미지 CAPTCHA를 입력합니다. 이미지 새로고침은 새 captchaKey를 생성합니다.
4. 성공하면 작업 공간이 열립니다. 콘솔에서 시스템 관리 → 사용자를 확인합니다.
5. 예제 자격 정보는 격리 개발 환경에서만 쓰며 공유/운영 전에 교체합니다.

오답이나 전송 실패 후에는 새 이미지를 받아야 합니다. Redis GETDEL이 이전 값을 소비했습니다. 잘못된 테넌트, 계정 잠금, 503은 [인증 안내](authentication.kr.md)로 조사하고 이전 captchaKey를 재전송하지 않습니다. 단기 fixture token은 인증 후 기능만 검증하며 이 단계를 대신하지 않습니다.

이미지는 언어별 폼 배치를 보여 주며 CAPTCHA는 가려져 있습니다. 2026-10-09 사람이 일회성 CAPTCHA를 입력하여 실제 최초 로그인을 완료했고 HTTP·업무 코드 모두 200입니다. [비식별 기록](../reference/evidence/human-login-2026-10-09.json)을 참조합니다. 다른 언어 페이지 테스트는 단기 테스트 계정을 사용하며 네 번의 비밀번호 로그인을 입증하지 않습니다.

![로그인 입구: 테넌트, 계정, 암호, CAPTCHA](../images/ko-KR/auth-login.png)

## 7. 프리셋 확인, 종료, 개발

관리자는 선택 모듈을 볼 수 있습니다. core에 workflow/공급망 메뉴가 없는 것은 정상이며 일반 사용자는 역할 제한도 받습니다. 빈 목록도 정상일 수 있습니다. seed에는 과거 주문, 승인, 자산이 없습니다. [데이터와 fixture](../wiki/data-and-fixtures.kr.md)로 학습 기록을 만듭니다.

기본 down은 이름 있는 볼륨을 보존하고 다음 up은 데이터를 재사용합니다. `--volumes`는 추가하지 않습니다. 소스 수정 뒤에는 컨테이너에서 테스트하고 해당 이미지를 다시 빌드합니다.

```bash
docker compose --profile full down
docker compose --profile full up -d
```

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose --profile full up -d --build
```

다음 단계: [인증과 테넌트](authentication.kr.md) · [권한과 데이터 범위](permissions.kr.md) · [서비스와 자르기](scaffold-development.kr.md) · [문제 해결](troubleshooting.kr.md)
