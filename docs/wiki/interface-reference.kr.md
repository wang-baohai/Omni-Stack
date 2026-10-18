# 화면 참고와 기존 이미지

기준 main fc165e3에서 보존한 과거 화면 이미지 68개를 나열하며, 각 문서에서 네 언어 디렉터리를 함께 보여 줍니다. 이번 업무 실행이나 현재 UI 인수의 증거는 아닙니다. 기존 last_success_at 등록은 이미지 해시나 컨테이너 이미지와 연결되지 않아 실제 촬영 시각과 환경을 확정할 수 없습니다. 현재 가이드로 자신의 기록을 준비하고 과거 업무 ID가 존재한다고 가정하지 마세요. 언어 디렉터리가 이미지의 모든 문구 번역 완료를 보장하지 않습니다.

<!-- omni:modules:workflow -->
## workflow

| 페이지 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 프로세스 정의(publish) | ![프로세스 정의(간체 중국어)](../images/zh-CN/workflow-definitions.png) | ![프로세스 정의(영어)](../images/en-US/workflow-definitions.png) | ![프로세스 정의(일본어)](../images/ja-JP/workflow-definitions.png) | ![프로세스 정의(한국어)](../images/ko-KR/workflow-definitions.png) |
| 프로세스 인스턴스 추적(instance-tracking) | ![프로세스 인스턴스(간체 중국어)](../images/zh-CN/workflow-instances.png) | ![프로세스 인스턴스(영어)](../images/en-US/workflow-instances.png) | ![프로세스 인스턴스(일본어)](../images/ja-JP/workflow-instances.png) | ![프로세스 인스턴스(한국어)](../images/ko-KR/workflow-instances.png) |
| 통계 대시보드(요약 뷰) | ![통계 대시보드(간체 중국어)](../images/zh-CN/workflow-stats.png) | ![통계 대시보드(영어)](../images/en-US/workflow-stats.png) | ![통계 대시보드(일본어)](../images/ja-JP/workflow-stats.png) | ![통계 대시보드(한국어)](../images/ko-KR/workflow-stats.png) |
### 읽기 전용 상세 오버레이(4개 언어)
| 오버레이 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 프로세스 진행(instance-tracking) | ![프로세스 진행(간체 중국어)](../images/zh-CN/workflow-instance-progress.png) | ![프로세스 진행(영어)](../images/en-US/workflow-instance-progress.png) | ![프로세스 진행(일본어)](../images/ja-JP/workflow-instance-progress.png) | ![프로세스 진행(한국어)](../images/ko-KR/workflow-instance-progress.png) |
| 승인 기록(approval) | ![승인 기록(간체 중국어)](../images/zh-CN/workflow-instance-approval-records.png) | ![승인 기록(영어)](../images/en-US/workflow-instance-approval-records.png) | ![승인 기록(일본어)](../images/ja-JP/workflow-instance-approval-records.png) | ![승인 기록(한국어)](../images/ko-KR/workflow-instance-approval-records.png) |
| 버전 이력(publish) | ![버전 이력(간체 중국어)](../images/zh-CN/workflow-model-versions.png) | ![버전 이력(영어)](../images/en-US/workflow-model-versions.png) | ![버전 이력(일본어)](../images/ja-JP/workflow-model-versions.png) | ![버전 이력(한국어)](../images/ko-KR/workflow-model-versions.png) |

기존 버전 이력 이미지는 중국어에서 DRAFT 버전 0, 영어·일본어·한국어에서 PUBLISHED 버전 2를 표시합니다. 동일 기록의 네 언어 비교나 이번 게시 성공 증거가 아닙니다. 현재 설계·게시·승인 절차는 [워크플로 가이드](../guides/workflow-approval.kr.md)를 참조하세요.

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

| 페이지 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 메시지 상세 오버레이(message-status) | ![메시지 상세(간체 중국어)](../images/zh-CN/monitor-mq-message-detail.png) | ![메시지 상세(영어)](../images/en-US/monitor-mq-message-detail.png) | ![메시지 상세(일본어)](../images/ja-JP/monitor-mq-message-detail.png) | ![메시지 상세(한국어)](../images/ko-KR/monitor-mq-message-detail.png) |

기존 일본어·한국어 MQ 상세 이미지의 제목과 레이블은 영어입니다. 중국어는 다른 PENDING 메시지이고 나머지는 SENT이므로 동일 메시지의 네 언어 전달을 증명하지 않습니다. 본문 하단은 뷰포트 밖에서 잘려 전체 필드는 자신의 기록으로 확인해야 합니다. 현재 임대·CAS·재시도·소비자 멱등성은 [MQ 사실 문서](../mq-reliability.kr.md)를 참조하세요.

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## srm

### 공급업체 견적 흐름 스크린샷(4개 언어)
| 항목 | 내용 |
|---|---|
| 환경 | 과거 스크립트의 기본 프런트엔드는 `127.0.0.1:3000`이며 `omni-procurement`와 `omni-srm`에 접근하지만, 이전 이미지의 배포 환경을 증명하지 않습니다. 새로 촬영할 때는 앱과 브라우저 모두 로컬 Docker에서 실행하고 `E2E_RUNTIME_MODE=external`로 컨테이너 프런트엔드에 연결하세요. 인수는 [촬영 설정](../../omni-frontend/playwright.docs.config.ts)을 참조하며 호스트에서 기본 webServer를 실행하지 않습니다. |
| 데이터 전제 | 과거 준비 스크립트는 `admin`과 공식 API로 분류·품목 생성 → `procurement-approval` 모델 버전에 연결된 승인 규칙 생성 → 구매 요청 제출·승인 → 견적 요청 생성과 `send`(DRAFT→SENT, `supplier1` 초대)를 수행합니다. 언어별로 별도 견적 요청을 사용하며 이번 문서 확인에서는 이 쓰기 흐름을 재실행하지 않았습니다. |
| 조작자 | 과거 준비 스크립트는 `admin`, 화면 계정은 `supplier1`(`SUPPLIER` 역할과 유효한 `srm_supplier_portal_user` 연결)입니다. 현재 일반 사용자의 최소 권한 목록은 아닙니다. 현재 역할·데이터 범위·후보 조건은 [SRM 가이드](../guides/srm-flow.kr.md)와 [구매 가이드](../guides/procurement-flow.kr.md)를 참조하세요. |
| 토큰 | `E2eTokenFixture`는 기존 테넌트 사용자와 DB 권한으로 TTL 1200초의 테스트 JWT를 발급하며 비밀번호 로그인이나 실제 CAPTCHA를 대체하지 않습니다. 저장소 밖 임시 파일은 호출자가 삭제해야 하지만, 이 문서는 과거 삭제 완료를 증명하지 않습니다. 토큰은 문서·로그·버전 관리에 남기지 마세요. |
| 쓰기 스위치 | 테스트 토큰이 없거나 `E2E_MUTATIONS=true`를 명시하지 않으면 견적 스위트 전체를 건너뜁니다. expectOk의 스위치 검사는 네트워크 응답을 기다린 뒤 수행되므로 요청 전송 전 차단기가 아닙니다. 승인된 격리 환경에서만 실행하며 이번 문서 확인에서는 스위트를 실행하지 않습니다. |
| 뷰포트 | 기존 견적 이미지는 1440×900이며 도우미는 촬영 애니메이션을 비활성화합니다. 현재 도우미는 `display-fixtures` 모드에서만 시계를 고정하므로 모든 과거 이미지가 그 모드에서 촬영되었다고 단정할 수 없습니다. |
#### 단계 1: 초대 목록(미견적)
| zh-CN | en-US |
|---|---|
| ![공급업체 포털 견적 초대 목록(중국어)](../images/zh-CN/srm-portal-quotation-invitations.png) | ![공급업체 포털 견적 초대 목록(영어)](../images/en-US/srm-portal-quotation-invitations.png) |
| ja-JP | ko-KR |
|---|---|
| ![공급업체 포털 견적 초대 목록(일본어)](../images/ja-JP/srm-portal-quotation-invitations.png) | ![공급업체 포털 견적 초대 목록(한국어)](../images/ko-KR/srm-portal-quotation-invitations.png) |
#### 단계 2: 견적 양식(단가와 유효기간 입력)
| zh-CN | en-US |
|---|---|
| ![공급업체 포털 견적 양식(중국어)](../images/zh-CN/srm-portal-quotation-form.png) | ![공급업체 포털 견적 양식(영어)](../images/en-US/srm-portal-quotation-form.png) |
| ja-JP | ko-KR |
|---|---|
| ![공급업체 포털 견적 양식(일본어)](../images/ja-JP/srm-portal-quotation-form.png) | ![공급업체 포털 견적 양식(한국어)](../images/ko-KR/srm-portal-quotation-form.png) |
#### 단계 3: 제출 성공(QUOTED 와 견적 총액)
| zh-CN | en-US |
|---|---|
| ![공급업체 포털 견적 제출 성공(중국어)](../images/zh-CN/srm-portal-quotation-submitted.png) | ![공급업체 포털 견적 제출 성공(영어)](../images/en-US/srm-portal-quotation-submitted.png) |
| ja-JP | ko-KR |
|---|---|
| ![공급업체 포털 견적 제출 성공(일본어)](../images/ja-JP/srm-portal-quotation-submitted.png) | ![공급업체 포털 견적 제출 성공(한국어)](../images/ko-KR/srm-portal-quotation-submitted.png) |
### SRM 관리측 페이지 스크린샷(4개 언어)
| 페이지 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 공급업체 관리(lifecycle) | ![공급업체 관리(중국어)](../images/zh-CN/srm-suppliers.png) | ![공급업체 관리(영어)](../images/en-US/srm-suppliers.png) | ![공급업체 관리(일본어)](../images/ja-JP/srm-suppliers.png) | ![공급업체 관리(한국어)](../images/ko-KR/srm-suppliers.png) |
| 성과 평가(evaluation) | ![성과 평가(중국어)](../images/zh-CN/srm-evaluations.png) | ![성과 평가(영어)](../images/en-US/srm-evaluations.png) | ![성과 평가(일본어)](../images/ja-JP/srm-evaluations.png) | ![성과 평가(한국어)](../images/ko-KR/srm-evaluations.png) |
| 위험 관리(risk) | ![위험 관리(중국어)](../images/zh-CN/srm-risks.png) | ![위험 관리(영어)](../images/en-US/srm-risks.png) | ![위험 관리(일본어)](../images/ja-JP/srm-risks.png) | ![위험 관리(한국어)](../images/ko-KR/srm-risks.png) |
| 위험 지표 설정(risk) | ![위험 지표 설정(중국어)](../images/zh-CN/srm-risk-config.png) | ![위험 지표 설정(영어)](../images/en-US/srm-risk-config.png) | ![위험 지표 설정(일본어)](../images/ja-JP/srm-risk-config.png) | ![위험 지표 설정(한국어)](../images/ko-KR/srm-risk-config.png) |
| 초대 관리(invite) | ![초대 관리(중국어)](../images/zh-CN/srm-invites.png) | ![초대 관리(영어)](../images/en-US/srm-invites.png) | ![초대 관리(일본어)](../images/ja-JP/srm-invites.png) | ![초대 관리(한국어)](../images/ko-KR/srm-invites.png) |
### 공급업체 포털 반응형 안정성 스크린샷(4개 언어)
| 뷰포트 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 390×844 모바일 | ![포털 견적 목록 모바일(중국어)](../images/zh-CN/srm-portal-quotation-mobile.png) | ![포털 견적 목록 모바일(영어)](../images/en-US/srm-portal-quotation-mobile.png) | ![포털 견적 목록 모바일(일본어)](../images/ja-JP/srm-portal-quotation-mobile.png) | ![포털 견적 목록 모바일(한국어)](../images/ko-KR/srm-portal-quotation-mobile.png) |
| 1024×768 태블릿 | ![포털 견적 목록 태블릿(중국어)](../images/zh-CN/srm-portal-quotation-tablet.png) | ![포털 견적 목록 태블릿(영어)](../images/en-US/srm-portal-quotation-tablet.png) | ![포털 견적 목록 태블릿(일본어)](../images/ja-JP/srm-portal-quotation-tablet.png) | ![포털 견적 목록 태블릿(한국어)](../images/ko-KR/srm-portal-quotation-tablet.png) |

두 좁은 화면은 포털 목록과 탭 배치 참고이며 모바일 입력·제출·전체 견적 흐름 성공을 증명하지 않습니다. 과거 견적 양식에는 배경 비침, 일본어·한국어 유효기간 레이블 줄바꿈, 중국어 단위 “件”이 남아 있습니다. 이미지를 다시 그려 결함을 숨기지 않습니다.

<!-- omni:modules:end -->

[이번 확인 기록](../reference/evidence/interface-reference-wiki-review-2026-10-17.json)은 기준 이미지 68개의 해시 확인과 원본 크기 이미지 12개의 실제 확인을 구분합니다. 문서는 실제 AI_REVIEW로 인수하며 사람의 문서 검토는 필수가 아닙니다. 과거 화면 번역 부족, 현재 업무 실행, 최종 사이트 인수는 각각 별도 확인 항목으로 남깁니다.
