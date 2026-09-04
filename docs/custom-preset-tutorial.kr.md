# 커스텀 프리셋 튜토리얼

커스텀 프리셋은 다섯 종 공식 프리셋 외에도 catalog 에 알려진 모듈을 조합하는 데 적합합니다. 공식 프리셋과 동일한 Schema, 의존성 클로저, 충돌 검사, 원자 생성, 잔여 검사를 사용합니다.

## 1. YAML 작성

예를 들어 공급업체와 워크플로우 기능만 필요하다면:

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: 공급업체 워크스페이스
description: 코어 플랫폼, 워크플로우, 공급업체 관리.
modules: [srm, gateway, mysql, redis, nacos]
~~~

`modules` 에는 진입 모듈만 적습니다. `srm` 은 `workflow → base → auth → platform` 을 자동으로 끌어옵니다; 생성기는 의존성과 무관한 Procurement 이나 Asset 을 자동 추가하지 않습니다.

## 2. 검증과 설명

~~~powershell
Set-Location tools/omni-cli
npm run build
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset explain C:\WorkSpace\supplier-workspace.yaml
~~~

알 수 없는 모듈, Schema 오류, 충돌은 어떤 파일 쓰기 전에 실패합니다.

## 3. 미리보기와 생성

~~~powershell
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app --dry-run
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
~~~

생성된 프로젝트는 정규화된 `scaffold/presets/supplier-workspace.yaml` 과 `scaffold.lock` 을 저장합니다. 공유 Workflow 서비스의 조달, 자산 기본 프로세스는 조합에 따라 잘려 나가고, 공급업체 진입 프로세스만 남습니다.

## 4. 생성 프로젝트 검증

~~~powershell
Set-Location C:\WorkSpace\supplier-app\omni-backend
$env:JAVA_HOME='C:\APP\JDK25\jdk-25.0.2'
.\mvnw.cmd clean install

Set-Location ..\omni-frontend
npm ci
npm run lint
npm run build

Set-Location ..
docker compose config --quiet
~~~

그런 다음 격리된 Compose project/volume 로 fresh 데이터베이스, 기동, 브라우저 스모크를 실행합니다. 기존 개발 스택과 데이터베이스 볼륨을 공유하지 마세요.

## 흔한 실수

- `omni-*` artifact 이름을 `modules` 에 쓰기: 여기서는 catalog 의 모듈 ID 를 사용해야 합니다.
- 모든 전이 의존성을 명시적으로 복사하기: 불필요하며 유지관리 노이즈를 늘립니다.
- 현재 저장소나 비어 있지 않은 디렉터리에 출력하기: 생성기가 거부합니다.
- 생성 후 모듈을 수동 삭제하기: `scaffold.lock`, 시드 다이제스트, 라우트를 왜곡하므로, YAML 을 수정해 재생성해야 합니다.
- 선택적 인프라를 자동 비활성화로 이해하기: 생성 후에도 해당 런타임 스위치를 확인해야 합니다.
