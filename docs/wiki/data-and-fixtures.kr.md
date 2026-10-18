# 기본 시드, 교육 데이터, 테스트 fixture

기준: 주 브랜치 `fc165e3`. [첫 실행](../guides/quick-start.kr.md) 후 Compose project, 테넌트, 기록을 확인합니다. 이 문서는 기존 데이터 초기화를 허가하지 않습니다.

## 1. 데이터 출처

| 출처 | 책임 | 사용 시점 |
|---|---|---|
| `database/changelog/` | 불변 구조 및 필수 데이터 마이그레이션 | fresh와 업그레이드, migrator 실행 |
| `scripts/sql/seed/` | 멱등 기본 시드 | migrator 참조, 수동으로 중복 실행하지 않음 |
| `database/seed/manifest.yaml` | 해시, 자연 키, 검증 조건 | 사용자 데이터가 비어 있지 않아도 기본 데이터 검증 |
| `database/seed/history/` | 고정한 과거 시드 버전 | 기존 changeSet 재실행, 임의 편집 금지 |
| `scripts/fixtures/`, test classpath | 범위가 제한된 신원과 테스트 데이터 | 확인한 로컬 검증, 운영 초기화가 아님 |
| 튜토리얼 기록 | 실제 업무 데모 | UI 또는 공식 API로 만들고 반환 ID 사용 |

관리자, 개발 테넌트, 메뉴, 승인 모델은 기본 기능입니다. 승인된 구매 요청, 견적, 주문, 자산은 업무 기록입니다. 시드는 과거 `PR-1-1` 또는 `RFQ-1-1`을 보장하지 않습니다.

## 2. fresh, 재실행, 기존 데이터 인수

fresh는 격리된 빈 DB 환경입니다. Compose는 migrator가 정상 종료한 후 앱을 실행합니다. 반복 실행은 업그레이드 재실행이며 실패를 숨기기 위해 volume을 삭제하지 않습니다.

기존 데이터 인수에는 `database/adoption/`의 고정 기준과 실제 백업 증거가 필요합니다. 확인하지 않은 DB를 마이그레이션 완료로 표시하지 않습니다. 실패 시 로그와 버전을 보존하고 전진 수정하며 실행한 changeSet을 변경하지 않습니다.

기본 검증은 공식 시드의 자연 키만 대상으로 하며 사용자 추가 데이터는 고정 개수에서 제외합니다. 권한·기본 데이터 변경 시 원본, SHA-256, 검증 조건을 갱신하고 누락을 숨기기 위해 기대값을 바꾸지 않습니다.

## 3. 교육 기록 만들기

1. 구매·자산은 supply-chain, 영업은 crm을 선택합니다.
2. 테넌트, 관리자, 요청자, 승인자, 공급업체를 기록합니다. 공급업체에는 SUPPLIER와 유효한 포털 연결이 필요합니다. 사용자명만으로 권한을 가정하지 않습니다.
3. 이름에 `WIKI-<실행 고유 ID>` 접두사를 붙이고 실제 개인·은행·회사 정보를 쓰지 않습니다.
4. [SRM](../guides/srm-flow.kr.md), [구매](../guides/procurement-flow.kr.md), [자산](../guides/asset-flow.kr.md)을 순서대로 공식 작업으로 만들고 ID, 상태, 버전을 기록합니다.
5. 같은 요청의 재시도는 requestId를 재사용하고 새 작업은 새 requestId를 사용합니다. 응답이 불확실하면 먼저 부작용을 확인합니다.
6. 주요 업무의 성공을 확인하기 전에 비가시성, 거부, 취소, 중복 요청을 검증합니다. 이번 실행 키만 상태가 허용하는 철회·종료·삭제 기능으로 정리합니다.

물리 삭제가 불가능하면 취소·최종 상태를 유지합니다. 교육 도구는 일반 reset이 아닙니다. 공유 테넌트를 덮어쓰거나 DB·volume을 일괄 삭제하지 않습니다.

<!-- omni:modules:workflow -->
### 교육용 승인자의 조직 범위

역할과 기본 조직을 지정하는 것만으로 승인 후보자가 되지는 않습니다. 현재 사용자 역할 API는 `sys_user_role`을 갱신하지만 후보자 조회는 별도로 `sys_user_role_scope`를 사용합니다. 관리 화면에는 이 연결을 관리하는 기능이 없습니다. 먼저 후보자 미리보기를 확인하세요. 역할 선택만으로 승인이 준비되었다고 판단하면 안 됩니다.

호스트에 MySQL 포트를 공개하지 않는 독립 교육 프로젝트에서만 [조직 범위 fixture](../../scripts/fixtures/run-workflow-scope-fixture.ps1)를 사용해 명시한 `wiki{run}_name` 계정의 기존 역할과 기본 조직을 `SAME_UNIT`으로 연결할 수 있습니다. 사용자를 만들거나 역할을 부여하거나 충돌을 덮어쓰지 않으며 표준 `omni-stack`은 거부합니다. 활성 사용자, 역할, 기본 조직을 먼저 준비하세요. 호스트 PowerShell은 실행을 중개하고 DB 클라이언트와 SQL은 대상 Docker 컨테이너 안에서 실행됩니다.

```powershell
$env:E2E_MUTATIONS = 'true'
$evidence = Join-Path ([IO.Path]::GetTempPath()) ('omni-scope-' + [guid]::NewGuid())
./scripts/fixtures/run-workflow-scope-fixture.ps1 -ProjectName 'omni-wiki-acceptance-yourrun' -Usernames 'wikiyourrun_manager','wikiyourrun_compliance' -OutputDirectory $evidence
```

프로젝트와 계정 이름은 실제 값으로 바꾸세요. 결과 파일에는 SQL 해시, 변경 전후 연결, 컨테이너 식별 정보가 기록됩니다. 재확인은 새 출력 디렉터리와 `-VerifyOnly`를 사용하세요. 충돌하면 결과를 보존하고 교육 설정을 수정하며 DB를 초기화하지 않습니다.

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
후보자 자격과 승인 완료 API 권한은 별도 조건입니다. 현재 SRM 승인 역할 세 가지의 시드에는 `workflow:approval:complete`가 없습니다. 역할 관리에서 `workflow`, `workflow:instance`, `workflow:instance:list`, `workflow:task:todo`, `workflow:approval:complete`만 포함하는 최소 교육 승인 역할을 추가하고 기존 업무 역할도 유지하세요. 다시 로그인하거나 짧은 수명의 fixture 토큰을 재발급한 뒤 해당 계정의 후보 작업만 처리합니다. 최고 관리자의 대리 승인으로 권한 누락을 감추면 안 됩니다.
<!-- omni:modules:end -->

## 4. 제한된 테스트 신원과 CAPTCHA

실제 로그인은 서비스 CAPTCHA를 사람이 입력합니다. 성공과 실패 모두 captchaKey를 원자적으로 소비하므로 재시도 전에 새로고침합니다.

로그인 이후 E2E는 `omni-auth/src/test/java/.../E2eTokenFixture.java`의 단기 신원을 사용할 수 있습니다. 확인한 DB의 역할과 현재 Redis 키를 사용하며 운영 로그인 백도어, 비밀번호 읽기, CAPTCHA 해독을 추가하지 않습니다. `scripts/fixtures/run-token-fixture.ps1`을 사용하고 출력은 저장소 밖에 두고 사용 후 정리합니다.

과거 fixture의 테넌트, UUID, 자원 보호 조건을 유지합니다. 허가된 입력이 없으면 거부하며 표준 스택으로 바꾼 결과는 격리 검증 성공이 아닙니다. [fixture 규칙](../../scripts/fixtures/README.md)을 참조합니다.

## 5. 결과 판단

| 현상 | 확인 |
|---|---|
| 기본 목록, 권한, 모델 누락 | migrator와 시드 검증, 업무 상태 직접 수정 금지 |
| 빈 개요 | 빈 업무 환경에서는 정상, 교육 기록 생성 |
| 입고 후 자산 없음 | PASS, assetManaged, 정확한 양의 정수 수량, MQ와 Inbox |
| 데모 기록이 보이지 않음 | 테넌트, 역할, owner/current user 범위 |
| 재실행 중복 | requestId, 이벤트 ID, 원본 단위 번호, 이전 부작용 |
| 스크린샷과 데이터가 다름 | 이번 ID·상태 사용, 과거 그림은 현재 증거가 아님 |

백업·복구는 [운영](../guides/operations-upgrade.kr.md)을 참조합니다. 비식별 결론과 필요한 그림만 커밋하고 token, 인증 상태, DB 덤프, 원시 로그는 제외합니다.
