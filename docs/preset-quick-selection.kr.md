# 프로젝트 프리셋 빠른 선택

프리셋은 Omni-Stack 소스 프로젝트에서 경계가 명확한 새 프로젝트 디렉터리를 생성합니다. 명령은 현재 저장소 내용을 제자리에서 삭제하지 않습니다. 모듈, 의존성, 포트, 리소스 점유의 사실 원본은 `scaffold/catalog/modules.yaml`입니다; 전체 결과는 [프리셋 의존성 매트릭스](preset-dependency-matrix.kr.md)를 확인하세요.

## 선택 권장

| 요구사항 | 선택 | 포함되는 업무 경계 |
|---|---|---|
| 로그인, RBAC, 조직, 사전, 로그, 기본 작업 | `core` | Workflow 및 업무 도메인 미포함 |
| BPMN, 승인, 할 일, 프로세스 인스턴스 필요 | `workflow` | `core` + Workflow |
| 영업, 고객, 영업기회 시스템 구축 | `crm` | `core` + CRM, 공급망 미포함 |
| 공급업체, 구매, 자산 클로즈드루프 구축 | `supply-chain` | Workflow + SRM + Procurement + Asset |
| 현재 저장소의 모든 기능 필요 | `full` | CRM, 공급망, 자산, 전체 인프라 |

여전히 불확실하면 가까운 요구를 충족하는 가장 작은 프리셋부터 시작하세요. 나중에 모듈을 추가하는 것이 전체 프로젝트에서 수동으로 모듈을 삭제하는 것보다 안전합니다.

## 사용 단계

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

생성 전에 의존성 클로저, 백엔드 모듈, Compose 서비스, 포트, 데이터베이스, 권한 루트, 메모리 추정을 표시합니다. 출력 디렉터리는 존재하지 않거나 비어 있어야 합니다. 생성 완료 후 `scaffold.lock` 에 소스 버전, 프리셋 버전, 모듈 버전, 템플릿 버전을 기록합니다.

## 생성 후 반드시 검증

생성된 프로젝트에서 백엔드 빌드, `npm ci`, 프론트엔드 lint/build, `docker compose config --quiet` 를 실행합니다. 저장소 유지관리자는 다음도 실행해야 합니다:

~~~powershell
npm run test:preset-structure
npm run test:preset-smoke
~~~

`test:preset-golden` 은 다섯 종 프리셋 전체의 완전한 빌드 매트릭스를 실행하며, 야간이나 릴리스 전 실행에 적합합니다. 데이터베이스 fresh, 실제 기동, 브라우저 smoke 는 런타임 검증으로, 구조 검사로 대체할 수 없습니다.

## 중요 경계

- `core`, `workflow`, `crm` 은 RocketMQ, XXL-JOB Admin 을 시작하지 않습니다; 관련 기능은 구성으로 비활성화하거나 필요에 따라 커스텀 프리셋에 추가해야 합니다.
- `supply-chain` 은 의존성 클로저로 Workflow, SRM, Procurement 을 자동 포함하며, 중간 의존성을 건너뛸 수 없습니다.
- 커스텀 조합은 catalog 의 기존 모듈을 참조해야 하며, 상세는 [커스텀 프리셋 튜토리얼](custom-preset-tutorial.kr.md)을 확인하세요.
- 현재 저장소의 제자리 삭감은 지원하지 않으며, 생성된 프로젝트의 관리 대상 파일을 수동 삭제하지 마세요.
