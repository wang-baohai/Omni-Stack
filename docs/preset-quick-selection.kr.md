# 프로젝트 프리셋 빠른 선택

프리셋은 현재 저장소를 삭제하지 않고 경계가 명확한 새 Omni-Stack 프로젝트를 생성합니다. 모듈, 의존성, 포트, 리소스의 단일 사실 원본은 `scaffold/catalog/modules.yaml`입니다. 해석된 결과는 [의존성 매트릭스](preset-dependency-matrix.kr.md)를 확인하세요.

## 선택 기준

| 요구사항 | 프리셋 | 경계 |
|---|---|---|
| 로그인, RBAC, 조직, 사전, 로그, 기본 작업 | `core` | Workflow와 업무 도메인 제외 |
| BPMN, 승인, 할 일, 프로세스 인스턴스 | `workflow` | `core` + Workflow |
| 영업, 고객, 영업기회 | `crm` | `core` + CRM, 공급망 제외 |
| 공급업체, 구매, 자산 전체 흐름 | `supply-chain` | Workflow + SRM + Procurement + Asset |
| 저장소의 모든 기능 | `full` | CRM, 공급망, 자산, 전체 인프라 |

가까운 요구를 충족하는 가장 작은 프리셋부터 시작하세요. `full`에서 수동 삭제하는 것보다 나중에 catalog 모듈을 추가하는 편이 안전합니다.

~~~powershell
Set-Location tools/omni-cli
npm ci
npm run build
node dist/src/cli.js preset list
node dist/src/cli.js preset explain workflow
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow --dry-run
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow
node dist/src/cli.js preset validate workflow --output C:\WorkSpace\my-workflow
~~~

미리보기는 의존성 클로저, 백엔드, Compose, 포트, DB, 권한 루트, 메모리 추정을 보여줍니다. 출력 디렉터리는 없거나 비어 있어야 하며 `scaffold.lock`에 버전이 기록됩니다.

생성 후 Maven, `npm ci`, lint/build, Compose config를 실행하세요. 유지관리자는 `test:preset-structure`, `test:preset-smoke`, 릴리스 전에는 5종 `test:preset-golden`을 실행합니다. fresh migration, 실제 기동, 로그인·메뉴·health·브라우저 smoke도 별도 필수입니다.

`core`, `workflow`, `crm`은 RocketMQ와 XXL-JOB Admin을 기동하지 않습니다. `supply-chain` 중간 의존성은 자동 해석됩니다. 사용자 조합은 [커스텀 프리셋](custom-preset-tutorial.kr.md)을 참고하고 관리 파일을 수동 삭제하지 마세요.
