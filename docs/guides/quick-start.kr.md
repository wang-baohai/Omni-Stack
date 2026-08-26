# 5분 빠른 시작

적용 버전: Omni-Stack 0.6.x

로컬 개발 환경을 시작하고 처음 로그인하기 위한 안내서입니다. 운영 배포는 [Docker 배포](../docker-deployment.kr.md)와 [운영·백업·복구·업그레이드](operations-upgrade.kr.md)를 참고하세요.

## 1. 사전 조건

- JDK 25.
- Node.js LTS 22.12.0 이상.
- Docker Desktop과 Docker Compose.
- 사용 가능 메모리 12GB 이상 권장. 단일 도메인 평가에는 프로젝트 프리셋을 사용합니다.

## 2. 시작 방식 선택

전체 시스템:

```bash
docker compose --profile full up -d
docker compose ps
```

CRM만 시작:

```bash
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

`core`, `crm`, `srm`, `procurement`, `asset`, `supply-chain` 프리셋을 제공합니다. [프리셋 선택](../preset-quick-selection.kr.md)을 참고하세요. 최초 시작 시 Liquibase가 먼저 마이그레이션합니다. 기존 `init-all.sql` 또는 `migrate-*.sql`을 실행하지 마세요.

## 3. 상태 확인

`docker compose ps`에서 선택한 애플리케이션이 `healthy`가 될 때까지 기다립니다.

| 진입점 | 주소 | 용도 |
|---|---|---|
| 프론트엔드 | `http://localhost:3000` | 로그인, 작업 공간, 관리 화면 |
| Nacos | `http://localhost:8080` | 로컬 서비스 검색과 설정 |
| Gateway | `http://localhost:8102` | 프론트엔드 API 통합 진입점 |

포트 충돌은 `docker compose ps`와 `docker compose logs <service>`로 실패 서비스를 찾고 DB 볼륨을 반복해서 만들지 마세요.

## 4. 첫 로그인

로그인 화면에서 개발 테넌트를 선택하고 `scripts/sql/seed/auth.sql`에 개발용으로 명시된 관리자를 사용합니다. 화면의 일회용 CAPTCHA를 입력하고, 로그인 후 개발 자격 증명을 즉시 교체합니다. 공유 또는 운영 환경에서 시드 계정을 사용하지 않습니다.

CAPTCHA는 요청마다 한 번만 사용합니다. 새로 고침이나 실패 후에는 새 이미지와 `captchaKey`를 사용합니다.

## 5. 첫 기능 확인

관리자는 시스템 관리, 기초 데이터, 작업 스케줄링, 운영 모니터링, 워크플로와 프리셋의 업무 모듈을 확인할 수 있어야 합니다. 사용자 목록, 프로세스 모델, 도메인 개요, 개인 작업 생성 대화상자를 차례로 확인합니다.

메뉴가 없으면 권한과 프리셋을 점검하고 프론트엔드 정적 라우트로 우회하지 않습니다.

## 6. 소스 개발

```bash
cd omni-backend
./mvnw clean install
```

Windows에서는 먼저 `JAVA_HOME`을 JDK 25로 설정합니다.

```bash
cd omni-frontend
npm install
npm run lint
npm run build
npm run dev
```

## 7. 종료

```bash
docker compose --profile full down
```

기본적으로 `--volumes`를 사용하지 않습니다. 완전히 새 DB가 필요할 때 정확한 Compose project와 전용 볼륨을 확인한 뒤 삭제합니다.

## 8. 다음 단계

- [인증과 테넌트 선택](authentication.kr.md)
- [메뉴·역할·데이터 권한](permissions.kr.md)
- [스캐폴드 개발](scaffold-development.kr.md)
- [문제 해결](troubleshooting.kr.md)

