# 4. 프로젝트 적용: 시스템 유지보수와 개인 알림

관리자는 시스템 작업을, 일반 사용자는 통합 워크스페이스에서 개인 작업을 관리합니다. 실행기는 공유하지만 소유권 규칙은 다릅니다.

## 시스템 작업에 두 애너테이션이 필요한 이유

XXL-JOB에는 호출 방법을, 프로젝트에는 이름, 기본 주기, 인수 정의를 알려야 합니다. 따라서 두 애너테이션을 함께 검사합니다.

<!-- omni:source path="omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java" -->
```java
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                SystemJobMeta meta = AnnotationUtils.findAnnotation(method, SystemJobMeta.class);
                if (xxlJob != null && meta != null) {
                    String handlerName = xxlJob.value();
                    SystemJobInfo info = new SystemJobInfo();
```

실행 애너테이션만 있으면 프로젝트 시스템 목록에 포함된다고 보장할 수 없고, 설명 애너테이션만으로는 실행할 수 없습니다. 자동 등록은 없는 작업만 추가하고 기존 Cron과 인수를 보존하므로 재시작해도 관리자의 설정을 덮어쓰지 않습니다. 실행기를 껐다면 목록에 있다고 백그라운드에서 실행되는 것은 아닙니다.

MQ 중계와 로그 보관은 유지보수 작업입니다. 개인 작업 공통 진입점에는 실행 애너테이션만 있으므로 시스템 목록에 표시하려고 시스템 메타데이터를 추가하면 안 됩니다.

## 개인 작업: 개별 시간표와 공통 진입점

개인 작업마다 센터 항목은 따로 있지만 모두 userJobExecuteHandler를 호출합니다. 메시지에는 로컬 ID, 테넌트, 종류, 이름, 인수가 들어갑니다. 공통 진입점이 해석한 뒤 종류에 맞는 처리기를 찾습니다.

실제 조회는 다음처럼 짧습니다.

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java" -->
```java
    public UserJobHandler getHandler(String jobType) {
        return handlers.get(jobType);
    }
```

Spring이 주입하는 Map의 키는 Bean 이름입니다. Task-00001은 정확히 같은 이름의 Bean을 찾아야 하며, 표시 이름인 물 마시기 알림이 라우팅 코드는 아닙니다. 종류 목록에 행을 추가한다고 처리기가 배포되지는 않습니다.

## 결과가 내 작업에 나타나는 과정

공통 진입점은 메시지의 테넌트를 명시해 개인 실행 로그를 저장하고 최종 실행 시간을 갱신한 다음 XXL-JOB에 성공 또는 실패를 설정합니다. 핵심 저장 코드는 다음과 같습니다.

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java" -->
```java
        SysUserJobLog logEntity = new SysUserJobLog();
        logEntity.setJobId(jobId);
        logEntity.setTenantId(message.getTenantId());
        logEntity.setJobName(message.getJobName());
        logEntity.setJobType(jobType);
        logEntity.setFireTime(fireTime);
        logEntity.setExecuteTimeMs(executeTimeMs);
        logEntity.setStatus(status);
        logEntity.setErrorMessage(errorMsg);
        logEntity.setResultMessage(resultMessage);
        sysUserJobLogMapper.insert(logEntity);
```

이 부분만으로 전체 성공을 보장하지는 않습니다. 인수 해석 실패는 로그 저장 전에 반환하고 DB 쓰기도 실패할 수 있습니다. 개인 로그와 최종 시간 갱신은 하나의 선언적 트랜잭션이 아닙니다. 로그가 없으면 기본 실행 로그도 확인하고 성공 시간을 만들어 넣지 않습니다.

## 다른 작업 번호를 알아도 접근할 수 없는 이유

수정, 삭제, 상태 변경, 실행, 로그 조회 전에 부모 작업을 읽고 현재 테넌트와 작성자를 모두 확인합니다.

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java" -->
```java
    private void verifyOwnership(Long jobId, Long tenantId, String username) {
        SysUserJob job = userJobService.getJobById(jobId);
        if (!tenantId.equals(job.getTenantId()) || !username.equals(job.getCreateBy())) {
            throw new BusinessException(403, "无权操作他人的任务");
        }
    }
```

메뉴 권한보다 세밀한 검사입니다. 현재 로그 서비스는 검사한 부모의 jobId로 조회하므로 모든 SQL에 별도 테넌트 조건을 더한다고 설명하면 안 됩니다. 백그라운드 작업도 브라우저 HTTP 스레드에 남은 테넌트 정보에 의존하면 안 됩니다.

## 생성 실패 시 연결 정보를 보존하기

정상 경로는 로컬 저장, 원격 등록, 원격 ID 기록입니다. 등록 오류는 원격 커밋 전일 수도 있고 커밋 후 응답만 유실된 경우일 수도 있습니다. 이름으로 추측하지 않고 실행기 그룹, handler, 테넌트와 로컬 ID를 포함한 인수로 대조합니다.

원격 잔여 항목이 없음을 확인한 뒤 로컬 행을 삭제합니다. 조회나 정리도 실패하면 연결 정보를 유지하고 오류를 반환합니다. 이후 주기 수정, 상태 변경, 삭제는 원격 동작 후 로컬을 갱신합니다. 재시작이나 재생성은 대조를 대신하지 못합니다.

다음 [실제 관리 화면](distributed-jobs-admin.kr.md)에서 각 단계를 목록과 로그에 연결해 봅니다.
