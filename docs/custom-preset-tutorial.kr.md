# 커스텀 프리셋 튜토리얼

커스텀 프리셋은 catalog에 등록된 모듈을 공식 5종 이외의 형태로 조합합니다. 공식 프리셋과 같은 Schema, 의존성 클로저, 충돌 검사, 원자 생성, 잔여 검사를 사용합니다.

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: Supplier Workspace
description: Core platform, workflow, and supplier management.
modules: [srm, gateway, mysql, redis, nacos]
~~~

진입 모듈만 적습니다. `srm`은 `workflow → base → auth → platform`을 자동으로 포함하고 관계없는 Procurement/Asset은 추가하지 않습니다.

~~~powershell
Set-Location tools/omni-cli
npm run build
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset explain C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app --dry-run
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
~~~

Schema 오류, 알 수 없는 ID, 충돌은 쓰기 전에 실패합니다. 출력에는 정규화된 YAML과 `scaffold.lock`이 저장되고 이 예에서는 Workflow의 공급업체 승인만 남기며 구매/자산 모델을 제거합니다.

Maven `clean install`, frontend `npm ci`·lint·build, Compose config를 실행한 뒤 격리된 project/volume에서 fresh·기동·browser smoke를 검증합니다. artifactId를 module ID로 쓰기, 전이 의존성 전부 나열하기, 비어 있지 않은 디렉터리 사용, 생성 후 수동 삭제, 선택 인프라가 자동 비활성화된다고 가정하는 실수를 피하세요.
