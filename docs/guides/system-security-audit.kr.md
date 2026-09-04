# 시스템 설정, 보안 설정과 감사 로그

테넌트 설정, XSS 보호, 온라인 세션, 로그인 기록, 작업 로그와 신뢰성 메시지 기록 유지보수를 설명합니다.

## 1. 기초 데이터

시스템 관리는 테넌트, 조직, 사용자, 역할, 권한, OAuth2 클라이언트, 온라인 사용자, 인증 기록, 감사, XSS 설정을 포함합니다. 기초 데이터에는 사전이 있습니다. 변경 전에 테넌트와 권한 범위를 확인합니다.

사전은 안정적인 표시 열거에 사용합니다. 번역은 표시만 바꾸며 상태 값, 권한 코드, API 파라미터를 바꾸지 않습니다.

## 2. OAuth2 클라이언트

클라이언트 ID, 허용 grant, 리다이렉트 URI, scope, 동의 여부를 설정합니다. 운영은 HTTPS, 정확한 URI, Secret 관리, 최소 grant/scope, 자격 증명 순환과 승인 검토가 필요합니다.

## 3. XSS 보호

`@RequestBody`가 있는 새 서비스는 `XssConfigProvider` SPI를 구현합니다. 공통 계층은 요청 필터, Jackson 문자열 정화, Gateway 보안 헤더를 제공합니다.

규칙이나 토글 변경은 즉시 `xss:enabled:{tenantId}`와 `xss:rules:{tenantId}`를 무효화하고 TTL에만 의존하지 않습니다. 이벤트 속성, 스크립트 프로토콜, 허용 리치 텍스트 경계를 다시 검증합니다.

## 4. 보안 헤더

Gateway는 `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`를 추가합니다. 입력 정화, 출력 인코딩, CSP, 인가를 대체하지 않습니다.

## 5. 감사 정보

| 정보 | 위치 | 용도 |
|---|---|---|
| 로그인 로그 | `sys_login_log` | 성공/실패, 출처, 인증 방식 |
| 작업 로그 | 관리 화면 | 업무 쓰기, 실행자, 결과, Trace ID |
| MQ 기록 | 메시지 화면 | Outbox, 재시도, 데드레터, 상관 관계 |

`omni-auth`는 `@OperLog`를 사용하지 않고 인증을 로그인 기록으로 보존합니다. 외부 조회는 테넌트로 필터링하며 백그라운드 relay의 전체 테넌트 스캔은 의도된 설계입니다.

## 6. 진단 흐름

Trace ID를 얻어 작업 로그, MQ 기록, Tempo, Loki 순으로 동기·비동기 경로를 연결합니다. 데드레터는 하류 멱등성과 상태를 확인한 후 재전송합니다.

## 7. 운영 점검

개발 계정과 비밀을 교체하고 MySQL, Redis, Nacos, XXL-JOB, 관측/관리 포트를 공개하지 않습니다. TLS, 최소 권한, 보존, 마스킹, 알림 수신자, 복구 훈련을 설정합니다. 이미지와 보고서에 인증 정보나 개인정보를 포함하지 않습니다.

## 8. 관리 페이지 4개 언어 스크린샷

공식 이미지는 문서 전용 Playwright 케이스 `omni-frontend/e2e-docs/flows/management.flows.spec.ts` 에 의해 실제 실행 스택에서 생성되며, 언어별 디렉토리에 저장되고, 다른 언어 이미지를 재사용하지 않으며, 자리표시자 이미지나 목 응답을 사용하지 않습니다.

- 전제 조건: 로컬 Compose 전체 스택 실행 중, 프론트엔드 `127.0.0.1:3000`; `omni-auth` 와 `omni-base` 헬스.
- 조작자: `admin`(`SUPER_ADMIN`, 시스템 관리·기초 데이터·모니터링 메뉴 권한 보유).
- 조작: 로그인 후 테넌트, 조직, 역할, 권한, 사전, 온라인 사용자, 감사 로그, 인가 기록, XSS 방어, 작업 로그 페이지에 순차 진입.
- 기대 상태: 페이지 제목과 열 레이블이 현재 언어로 렌더링, 목록은 실제 시스템 데이터를 표시; 레코드가 없으면 제품 자체의 빈 상태를 표시(캡처 실패가 아님).
- 토큰: `E2eTokenFixture` 가 테스트 프로세스 내에서 단기 JWT(TTL 1200초)를 발급, 마무리 시 파기하며, 문서·로그·저장소에 쓰지 않습니다.
- 본 그룹은 모두 **읽기 전용 수집**: 구성이나 감사 데이터를 전혀 생성·수정·삭제하지 않으므로, 쓰기 스위치가 불필요하고 데이터 마무리도 없습니다.

민감 정보 설명: 인가 기록 페이지는 OAuth2 `client_id`(공개 식별자), 주체 및 인가 유형만 표시하고, **client secret·토큰·비밀번호를 포함하지 않음**; 온라인 사용자 페이지는 현재 환경에서 빈 상태이며 세션 토큰을 포함하지 않습니다.

| 페이지 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 테넌트 관리(tenant) | ![테넌트 관리(간체 중국어)](../images/zh-CN/system-tenants.png) | ![테넌트 관리(영어)](../images/en-US/system-tenants.png) | ![테넌트 관리(일본어)](../images/ja-JP/system-tenants.png) | ![테넌트 관리(한국어)](../images/ko-KR/system-tenants.png) |
| 조직 관리(organization) | ![조직 관리(간체 중국어)](../images/zh-CN/system-organizations.png) | ![조직 관리(영어)](../images/en-US/system-organizations.png) | ![조직 관리(일본어)](../images/ja-JP/system-organizations.png) | ![조직 관리(한국어)](../images/ko-KR/system-organizations.png) |
| 역할 관리(role, data-scope 입구 포함) | ![역할 관리(간체 중국어)](../images/zh-CN/system-roles.png) | ![역할 관리(영어)](../images/en-US/system-roles.png) | ![역할 관리(일본어)](../images/ja-JP/system-roles.png) | ![역할 관리(한국어)](../images/ko-KR/system-roles.png) |
| 권한 관리(permission, menu 노드 포함) | ![권한 관리(간체 중국어)](../images/zh-CN/system-permissions.png) | ![권한 관리(영어)](../images/en-US/system-permissions.png) | ![권한 관리(일본어)](../images/ja-JP/system-permissions.png) | ![권한 관리(한국어)](../images/ko-KR/system-permissions.png) |
| 사전 관리(dictionary) | ![사전 관리(간체 중국어)](../images/zh-CN/system-dictionaries.png) | ![사전 관리(영어)](../images/en-US/system-dictionaries.png) | ![사전 관리(일본어)](../images/ja-JP/system-dictionaries.png) | ![사전 관리(한국어)](../images/ko-KR/system-dictionaries.png) |
| 온라인 사용자(online-user, 빈 상태) | ![온라인 사용자(간체 중국어)](../images/zh-CN/system-online-users.png) | ![온라인 사용자(영어)](../images/en-US/system-online-users.png) | ![온라인 사용자(일본어)](../images/ja-JP/system-online-users.png) | ![온라인 사용자(한국어)](../images/ko-KR/system-online-users.png) |
| 감사 로그(audit) | ![감사 로그(간체 중국어)](../images/zh-CN/system-audit-log.png) | ![감사 로그(영어)](../images/en-US/system-audit-log.png) | ![감사 로그(일본어)](../images/ja-JP/system-audit-log.png) | ![감사 로그(한국어)](../images/ko-KR/system-audit-log.png) |
| 인가 기록(oauth2) | ![인가 기록(간체 중국어)](../images/zh-CN/system-auth-records.png) | ![인가 기록(영어)](../images/en-US/system-auth-records.png) | ![인가 기록(일본어)](../images/ja-JP/system-auth-records.png) | ![인가 기록(한국어)](../images/ko-KR/system-auth-records.png) |
| XSS 방어(xss) | ![XSS 방어(간체 중국어)](../images/zh-CN/system-xss-config.png) | ![XSS 방어(영어)](../images/en-US/system-xss-config.png) | ![XSS 방어(일본어)](../images/ja-JP/system-xss-config.png) | ![XSS 방어(한국어)](../images/ko-KR/system-xss-config.png) |
| 작업 로그(operation-log) | ![작업 로그(간체 중국어)](../images/zh-CN/system-operation-log.png) | ![작업 로그(영어)](../images/en-US/system-operation-log.png) | ![작업 로그(일본어)](../images/ja-JP/system-operation-log.png) | ![작업 로그(한국어)](../images/ko-KR/system-operation-log.png) |

## 9. 사전 유형 신규 생성 3-state 스크린샷(4개 언어)

`omni-frontend/e2e-docs/flows/system-dictionary.flows.spec.ts` 에 의해 실제 실행 스택에서 생성되며, 커버리지 목록의 `detail-and-action-states` 와 `failure-states` 에 대응.

- 전제 조건: 로컬 Compose 전체 스택 실행 중, `omni-base` 헬스; 사전 유형 테이블에 실제 기준선 데이터 존재(수집 전 17건).
- 조작자: `admin`(`dict:type:list`/`dict:type:create`/`dict:type:delete` 권한 필요).
- 쓰기 스위치: 본 그룹은 **데이터를 생성**하므로, `E2E_MUTATIONS=true` 를 명시 설정했을 때만 실행; 미설정 시 그룹 전체가 건너뛰고 모든 쓰기 호출은 직접 오류를 발생.
- 데이터 귀속 및 마무리: 각 언어가 유일 `typeCode`(본 라운드 `runStamp` 포함)를 자체 생성하고, 생성 성공 시 등록; afterAll 이 공식 `DELETE /api/base/dict/type/{id}` 계약으로 한 건씩 정리하고 응답과 목록 재조회를 검증.
- 실측 마무리 결과: 4 passed / 0 skipped; `registered=4 deleted=4 residual=0`; `sys_dict_type` 는 기준선 **17** 행으로 복귀, `E2EDICT-%` 잔여 **0**, `base-dictionary-catalog` 시드 단정은 여전히 **101** 행을 재현(본 배치로 오염되지 않음).

| 상태 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 생성 대화상자(create-or-edit) | ![사전 생성 대화상자(간체 중국어)](../images/zh-CN/system-dictionary-create-form.png) | ![사전 생성 대화상자(영어)](../images/en-US/system-dictionary-create-form.png) | ![사전 생성 대화상자(일본어)](../images/ja-JP/system-dictionary-create-form.png) | ![사전 생성 대화상자(한국어)](../images/ko-KR/system-dictionary-create-form.png) |
| 필수 항목 검증 실패(failure-or-forbidden) | ![사전 필수 검증 실패(간체 중국어)](../images/zh-CN/system-dictionary-create-validation.png) | ![사전 필수 검증 실패(영어)](../images/en-US/system-dictionary-create-validation.png) | ![사전 필수 검증 실패(일본어)](../images/ja-JP/system-dictionary-create-validation.png) | ![사전 필수 검증 실패(한국어)](../images/ko-KR/system-dictionary-create-validation.png) |
| 생성 성공(key-action-success) | ![사전 생성 성공(간체 중국어)](../images/zh-CN/system-dictionary-create-success.png) | ![사전 생성 성공(영어)](../images/en-US/system-dictionary-create-success.png) | ![사전 생성 성공(일본어)](../images/ja-JP/system-dictionary-create-success.png) | ![사전 생성 성공(한국어)](../images/ko-KR/system-dictionary-create-success.png) |

### 등록된 i18n PRODUCT_DEFECT(본 배치에서 수정하지 않음)

검증 실패 이미지에서, **4개 언어 UI 모두에서 오류 메시지가 중국어**(예 `typeCode: 字典类型编码不能为空`). 실측 근본 원인:

1. `views/base/dict/index.vue` 는 `form rules` 를 전혀 정의하지 않아, 프론트엔드는 필수 검증을 하지 않고(`.el-form-item__error` 가 비어 있음), 오류는 전적으로 백엔드 400 응답에 의존;
2. `CreateDictTypeRequest` 의 `@NotBlank(message = "字典类型编码不能为空")` 는 **중국어 하드코딩**으로, 백엔드 메시지 국제화에 미연결;
3. 또한: ja-JP/ko-KR 에서 대화상자 제목과 `Type Code`/`Type Name`/`Remark` 레이블이 영어로 렌더링되는데, 언어 팩의 해당 키의 **값 자체가 영어**이기 때문(「排序/並び順/정렬」만 번역됨).
   `npm run ui:i18n:parity`(4개 언어 각 2319 키, 0 누락)와 `npm run ui:i18n:check`(0/0 항목)가 모두 통과하므로, **번역 완전도** 문제이며 하드코딩 결함이 아님.

위 스크린샷은 **실제 문구를 그대로 보존**하고, 목·미화·숨김을 하지 않으며, 본 배치에서 제품 코드도 수정하지 않음; 수정에는 제품 측 결정(백엔드 메시지 국제화 방안 + ja/ko 번역 보완)이 필요.

아직 커버되지 않은 두 required flow: `config`(파라미터 구성)와 `login-record`(로그인 기록). 실측으로 `sys_permission` 에 대응 권한 코드가 없고 프론트엔드에도 대응 view 디렉토리가 없어, 제품이 현재 페이지를 제공하지 않음; 제약에 따라 **required flow 를 삭제하지 않고 임의로 exempt 로 표시하지 않으며**, 커버리지 목록에 명시적 gap 으로 보존.

[신뢰성 메시지](../mq-reliability.kr.md), [관측성](../observability.md), [Docker 배포](../docker-deployment.kr.md)를 참고하세요.

