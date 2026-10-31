# 예약 작업 제약

이 문서는 예약 작업의 사실과 엔지니어링 제약을 정의합니다. 기초 개념, 프레임워크 원문, 프로젝트 구현, 실제 화면은 [XXL-JOB 독립 장](wiki/distributed-jobs-principles.kr.md), 기존 세부 조작은 [예약 작업 튜토리얼](guides/scheduling.kr.md), 실행 설정은 [Docker 배포](docker-deployment.kr.md)를 참고하세요.

## 1. 경계와 데이터 소유권

| 소유자 | 책임 |
|---|---|
| `omni-common-core` | Spring에 의존하지 않는 `UserJobHandler` SPI와 `UserJobMessage` |
| `omni-common-job` | XXL-JOB 실행기, Admin 클라이언트, 시스템 메타데이터 및 자동 등록 |
| `omni-base` | 시스템/유형 관리, 개인 작업 소유권 검증, 처리기 라우팅과 실행 로그 |
| XXL-JOB Admin | Cron 예약, 실행기 검색, 트리거/콜백 기록. 개인 작업 권한의 소유자는 아님 |

`sys_user_job_type`은 전역 카탈로그이며 `type_code`는 고유하고 테넌트 열은 없습니다. `sys_user_job`은 테넌트와 생성자, `sys_user_job_log`는 메시지 테넌트와 `jobId`를 저장합니다. 워크벤치는 부모 작업의 테넌트/생성자를 검증한 뒤 `jobId`로 로그를 조회하며 현재 로그 서비스는 `tenantId` 인자를 SQL 조건에 추가하지 않습니다. `xxl_job_id`가 스케줄러 행을 연결합니다. 이름을 신뢰할 수 있는 상관 키로 사용하지 마세요.

## 2. 시스템 작업

- 시스템 메서드에는 `@XxlJob("handlerName")`과 `@SystemJobMeta(...)`가 모두 필요합니다. 하나라도 빠지면 `SystemJobRegistry`에 포함되지 않습니다.
- `SystemJobRegistry.scan()`이 메타데이터를 수집합니다. 실행기가 활성화되면 `XxlJobAutoConfiguration.systemJobAutoRegistrar()`가 없는 작업을 등록하고 기존 Cron/매개변수는 유지합니다.
- 서비스에는 `omni-common-job`이 필요합니다. 자동 구성은 classpath의 `XxlJobSpringExecutor`를 요구합니다. 실행기 Bean과 등록 Runner는 `xxl.job.executor.enabled=false`이면 비활성화됩니다.
- 서비스별로 AppName을 구분합니다. 같은 handler 이름은 서로 다른 실행기 그룹에 존재할 수 있습니다. 등록, 조회, 삭제에는 올바른 그룹이 필요합니다.
- `/api/job/system-job/list`는 `job:system-job:list`, 등록/시작/중지/트리거/삭제는 `job:system-job:manage`가 필요합니다. 목록 상태는 `UNREGISTERED`, `RUNNING`, `STOPPED`입니다. 원격 목록 조회 실패 시에도 `UNREGISTERED`로 돌아갈 수 있으므로 이 표시만으로 원격 부재를 입증할 수 없습니다.
- 시스템 결과는 개인 로그가 아닌 XXL-JOB 로그에서 확인합니다. 트리거 성공 응답은 예약 서버의 접수만 의미합니다.

## 3. 개인 작업 계약과 일관성

- `/api/base/my-job` 생성은 신뢰하는 헤더와 인증 문맥에서 테넌트와 생성자를 얻습니다. 수정/삭제/상태/트리거/로그는 `MyJobController.verifyOwnership(id, tenantId, username)`으로 두 값을 모두 확인합니다. 행별 소유권 검증을 `@PreAuthorize`로 대체하면 안 됩니다.
- 목록/통계는 현재 테넌트와 `createBy`로 고정됩니다. 활성 유형 목록은 전역 카탈로그입니다. `/api/job/user-job-type` 관리는 `job:user-job-type:*` 권한을 사용합니다.
- `UserJobMessage` 필드는 `jobId`, `tenantId`, `jobType`, `jobName`, `jobParams`입니다. 모든 개인 작업은 `userJobExecuteHandler`를 등록하고 내부에서 `jobType`으로 Bean을 찾습니다.
- 생성 시 로컬 행 삽입, XXL-JOB 등록, ID 저장 순서로 처리합니다. 정상 생성 직후 활성화됩니다. 로컬 `status=1`은 활성, `0`은 일시 중지입니다.
- 등록/ID 저장 실패 시 실행기 그룹, handler, 테넌트/로컬 ID가 담긴 `executorParam`으로 대상을 정확히 정리합니다. 원격 작업이 없음을 확인한 뒤에만 로컬 행을 삭제합니다. 결과를 알 수 없으면 복구 연결을 보존하고 오류를 반환합니다. 롤백 완료를 선언하거나 확인 없이 다시 생성하면 안 됩니다.
- XXL ID가 연결된 경우 Cron/매개변수 수정, 상태 변경, 삭제는 원격 작업을 먼저 수행합니다. 실패 시 기존 로컬 값/연결을 유지합니다. 삭제 실패 후 원격 부재 확인에는 전체 페이지 조회가 필요합니다. 이름만 바꾸면 XXL-JOB 설명은 갱신되지 않습니다.
- 일시 중지는 향후 Cron 예약을 중단하지만 수동 실행을 금지하지 않습니다. 접수와 완료는 별개입니다. 새 로그 ID, 시각, XXL-JOB 트리거 출처로 결과를 확인합니다.
- 실행 로그 `status=1`은 성공, `0`은 실패입니다. Handler 누락이나 처리한 handler 예외는 실패 로그 저장을 시도하며 JSON 역직렬화 실패는 로그 저장 전에 반환합니다. 로그 삽입과 마지막 시각 갱신은 같은 선언적 트랜잭션에 있지 않고 DB 예외로 최종 결과 설정이 중단될 수 있으므로 모든 실패의 개인 로그를 보장하지 않습니다.
- `XxlJobAdminClient`의 세션 Cookie는 메모리에만 유지합니다. 응답 code가 200이 아니거나 `not login`이 있으면 재로그인 후 한 번 재시도하지만 비즈니스 쓰기 멱등성을 보장하지는 않습니다. Cookie를 파일이나 공개 증거에 저장하지 않습니다.

## 4. 확장 제약

1. `UserJobHandler`를 구현하고 `@Component` Bean 이름을 카탈로그 `type_code`와 완전히 일치시킵니다. 활성 카탈로그 행만으로 처리기 배포를 증명할 수 없습니다.
2. 매개변수를 검증하고 HTTP 스레드 문맥 대신 메시지의 `tenantId`를 명시적으로 사용합니다. 비즈니스 효과는 멱등이어야 하며 Cron과 수동 실행의 중첩을 고려합니다.
3. `param_template`은 표준 JSON Schema `object/properties`와 호환 평면 형식을 지원합니다. 열거형은 안정 값을 제출하며 표시 라벨을 라우팅 코드로 사용하지 않습니다.
4. 시드 변경은 `scripts/sql/seed/base.sql`과 `database/seed/manifest.yaml`의 해당 체크섬/검증을 갱신합니다. 스키마에는 전진 전용 마이그레이션을 사용합니다.
5. 새 시스템 작업은 두 애너테이션으로 기본 Cron, 매개변수, 전략을 선언합니다. 개인 작업 공통 handler를 시스템 작업으로 분류하지 않습니다.
6. 로컬 Docker에서 생성, 수정, 일시 중지, 수동 실행, 재개, 실제 Cron, 성공/실패 로그, 소유권 거부, 삭제 후 원격 잔존 없음까지 확인합니다.

## 5. 소스 진입점

- [개인 컨트롤러](../omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java): 목록 필터와 `verifyOwnership()`.
- [개인 서비스](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobServiceImpl.java): 원격 우선 처리, 정확한 보상, 결과 불명 보존.
- [Admin 클라이언트](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobAdminClient.java): 등록, 전체 필드 갱신, 전체 페이지 조회, 반복 페이지 방지.
- [공통 실행 진입점](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java), [라우팅 레지스트리](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java), [물 마시기 처리기](../omni-backend/omni-base/src/main/java/com/omni/base/job/handler/DrinkWaterRemindHandler.java).
- [자동 구성](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java), [시스템 레지스트리](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java), [시드 동기화](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobSeedSyncRunner.java). 시드 동기화는 활성 상태이고 XXL ID가 없는 행을 검색합니다. 재시작은 결과 불명 상태의 대조를 대체하지 않습니다.

- [개인 로그 조회](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobLogServiceImpl.java): 현재 진입점의 부모 소유권 검증과 고정 jobId에 의존합니다.
- [시스템 작업 서비스](../omni-backend/omni-base/src/main/java/com/omni/base/service/SystemJobService.java): 메타데이터·원격 상태 병합 및 원격 조회 실패 시 표시를 확인할 수 있습니다.

## 6. 실행 및 검증 경계

인프라는 `compose.infra.yaml`과 migrator가 관리합니다. XXL-JOB 스키마/시드는 `database/changelog/xxl-job/`, `scripts/sql/seed/xxl-job.sql`에 있습니다. 자격 정보와 access token은 비공개 `.env`에서 가져오며 튜토리얼 고정값을 사용하지 않습니다. 서비스 간 연결은 `xxl-job-admin:8080/xxl-job-admin`이며 호스트 바인딩은 실제 Compose를 확인합니다.

날짜/시간은 `yyyy-MM-dd HH:mm:ss`를 사용합니다. 앱, 빌드, 테스트, 이미지, 증거 수집은 로컬 Docker에서 실행합니다. 튜토리얼 증거에는 실행자, 이미지, 소스, 실제 트리거 출처, 한계를 명시합니다. 카탈로그 이미지, 접수 응답, 모의 장애만으로 실제 실행 완료를 증명할 수 없습니다.
