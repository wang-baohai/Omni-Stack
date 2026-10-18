# 소스 디버깅: 증상에서 경계로

기준: main fc165e3 및 이번 문서 브랜치. 현재 동작은 연결된 소스가 정의합니다. 먼저 [소스 색인](../reference/source-map.kr.md)을 읽고 독립 학습 환경에서 실행합니다. 공유 업무 데이터에 장애를 주입하지 않습니다.

<!-- omni:modules:auth -->
## captcha-jwt

**문제와 호출 흐름**：잘못된 CAPTCHA 뒤에 재전송도 실패하는 이유는?

로그인 요청은 먼저 validate를 호출합니다. Redis GETDEL은 비교 성공 여부와 관계없이 값을 원자적으로 소비합니다. generateToken에서는 tenantId, roles, permissions, jti, 만료 시간을 확인합니다. 답, 암호, 전체 token은 기록하지 않습니다.

- [CaptchaServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java) — `validate`
- [JwtTokenServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/JwtTokenServiceImpl.java) — `generateToken`

**중단점, 실패와 기대 결과**：getAndDelete 전후에 중단점을 둡니다. stored는 처음에만 존재하고 다음에는 비어 있습니다. 실패하면 이미지를 새로 받습니다. 실제 CAPTCHA는 사람이 입력하며 우회 방법은 제공하지 않습니다.

**관련 테스트**：이 단위에는 독립 CAPTCHA 단위 테스트가 없습니다. 실제 로그인과 일회 소비는 컨테이너 통합 증거가 필요하며 통과로 기록할 수 없습니다.

요청은 captchaKey/captchaCode를 포함한 POST /api/auth/login입니다. redisKey는 일회용 기록을 가리키며 stored=null은 만료/없음/소비됨을 뜻합니다. 비밀번호 검사 전에 소비됩니다. 아래 실제 validate 코드로 중단점을 찾고 stored의 정답 값은 기록하지 마세요.

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java" -->
```java
        String stored = stringRedisTemplate.opsForValue().getAndDelete(redisKey);

        if (stored == null) {
            // 键不存在，说明验证码已过期、不存在或已被并发消费
            throw new BusinessException(400, "验证码已过期");
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:gateway -->
## gateway-assertion

**문제와 호출 흐름**：X-User-Id를 위조해도 관리자가 될 수 없는 이유.

AuthFilter는 외부 신원 헤더를 제거하고 검증된 JWT에서 신원과 HMAC 증명을 생성합니다. 후속 서비스는 서명과 유효 시간을 다시 확인합니다. 서명 키가 없으면 503을 반환합니다.

- [AuthFilter.java](../../omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java) — `filter`

**중단점, 실패와 기대 결과**：filter와 후속 검증에 중단점을 두고 헤더 이름, 검증 결과, 상태 코드를 봅니다. 신원이나 증명을 변경하면 테스트가 거부해야 합니다. 키는 출력하지 않습니다.

**관련 테스트**：[AuthFilterHmacTest.java](../../omni-backend/omni-gateway/src/test/java/com/omni/gateway/filter/AuthFilterHmacTest.java) · [GatewayPreAuthFilterHmacTest.java](../../omni-backend/omni-base/src/test/java/com/omni/base/security/GatewayPreAuthFilterHmacTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## data-scope

**문제와 호출 흐름**：메뉴는 보이지만 다른 부서 데이터가 보이지 않을 때.

기능 권한은 호출 가능 여부를, DataScope는 접근 가능한 행을 결정합니다. bindScope는 메서드 실행 중 범위를 연결하고 finally에서 해제합니다. SQL 순서는 TenantLine → DataPermission → OptimisticLocker → Pagination입니다.

- [ServiceDataScopeAspect.java](../../omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java) — `bindScope`

**중단점, 실패와 기대 결과**：테넌트, 범위 유형, 허용 조직/사용자 ID, 최종 WHERE를 확인하고 개인정보는 출력하지 않습니다. SELF는 타인의 행을 읽거나 수정할 수 없어야 하며 예외 후에도 컨텍스트는 비어야 합니다. 하위 행은 집계 루트의 범위를 상속합니다.

**관련 테스트**：[ServiceDataScopeAspectTest.java](../../omni-backend/omni-common-service/src/test/java/com/omni/common/service/datascope/ServiceDataScopeAspectTest.java) · [DataScopeResolveFilterTest.java](../../omni-backend/omni-auth/src/test/java/com/omni/auth/security/DataScopeResolveFilterTest.java)

identity는 검증된 Gateway 어설션에서 옵니다. scope는 userId, tenantId, 현재 permissionCode와 일치해야 합니다. joinPoint.proceed는 주석이 적용된 업무 메서드를 실행하며 성공/예외 모두 finally에서 ThreadLocal을 지웁니다. 범위 종류와 허용 ID를 점검하고 개인정보는 기록하지 마세요.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:workflow -->
## workflow-publish

**문제와 호출 흐름**：동시 게시와 회람 조기 완료를 조사하는 방법.

publishModel은 SELECT FOR UPDATE로 모델을 잠그고 BPMN 검증 후 배포와 버전 갱신을 수행합니다. completionCondition으로 생략된 작업은 HistoricTaskInstance.deleteReason=MI_END로 판정하며 활동 부모에서 결과를 추측하지 않습니다.

- [WorkflowModelServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java) — `publishModel`
- [ProcessInstanceServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java) — `MI_END`

**중단점, 실패와 기대 결과**：게시 잠금, validate 호출, 배포 직전에 중단하여 modelKey, tenantId, 버전을 확인합니다. 잘못된 BPMN은 배포되지 않아야 합니다. 작업 deleteReason과 실제 승인 기록을 확인하세요. MI_END는 엔진이 작업을 건너뛴 것으로 이력에 auto-approved로 표시되며, 사람이 던진 찬성표나 거절이 아닙니다.

**관련 테스트**：[WorkflowModelServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowModelServiceImplTest.java) · [WorkflowApprovalServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

**문제와 호출 흐름**：저장된 메시지의 재시도가 대기하는 이유.

업무 트랜잭션이 PENDING을 저장합니다. relayPendingMessages는 기한이 된 행을 찾고 claimForRelay의 CAS로 PROCESSING 임대를 획득합니다. 전송 후 상태 갱신도 CAS로 보호합니다. 실패는 newRetryCount를 증가시키며 2^newRetryCount × 10s 대기하고 한도에서 DEAD_LETTER로 이동합니다. 알 수 없는 broker도 실패로 계산합니다.

- [MqMessageRelayService.java](../../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) — `relayPendingMessages`

**중단점, 실패와 기대 결과**：id, status, retryCount, nextRetryTime, 획득 행 수를 확인합니다. 두 worker 중 하나만 획득해야 합니다. 백그라운드는 테넌트를 가로지르지만 외부 조회는 tenantId가 필수입니다. 장애 주입은 격리 컨테이너에서만 하고 복구 후 재전송과 소비자 멱등성을 확인합니다.

**관련 테스트**：[MqMessageRelayServiceTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [MqMessageRelayServiceCasTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [MqMessageRelayServiceUnknownBrokerTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## portal-saga

**문제와 호출 흐름**：가입 직후 공급업체 정보가 보이지 않는 이유.

enroll은 inviteToken과 requestId를 검증하고 로컬 트랜잭션에서 연결을 저장한 뒤 역할 부여 이벤트를 보냅니다. Auth가 SUPPLIER를 부여하고 결과를 반환하면 SRM이 연결을 완료합니다. USER는 가입만 가능하며 정보/평가는 역할과 활성 연결이 모두 필요합니다.

- [SupplierPortalServiceImpl.java](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierPortalServiceImpl.java) — `enroll`

**중단점, 실패와 기대 결과**：enroll과 결과 적용에 중단점을 두고 requestId, 연결 상태, 상관 키를 확인합니다. 실패는 retryEnrollment로 복구하며 중복 결과로 권한을 중복 부여하지 않습니다. 포털 사용자 ID를 내부 owner_user_id/owner_unit_id로 사용하지 않습니다.

**관련 테스트**：[SupplierPortalServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/SupplierPortalServiceImplTest.java) · [PortalRoleResultServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/PortalRoleResultServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## receipt-inbox

**문제와 호출 흐름**：수령 이벤트 재전송과 보완으로 자산이 이중 생성되지 않는 이유.

importEvent는 버전과 테넌트를 검증합니다. qualityStatus=PASS, assetManaged=true, 양의 정수 assetQuantity인 행만 카드로 만듭니다. Inbox eventId와 수령 행/단위 순번이 두 멱등성 경계입니다. 금액 JSON은 십진 문자열입니다.

- [ProcurementAssetImportServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImpl.java) — `importEvent`

**중단점, 실패와 기대 결과**：eventId, tenantId, sourceGrLineId, 단위 순번, ImportResult를 확인합니다. 이미 처리되거나 무시된 같은 이벤트를 재전송하면 `replayed=true`, `createdCount=0`을 반환하고, 다른 이벤트로 같은 출처를 보완해도 카드 수가 늘지 않습니다. 테넌트 충돌과 소수 수량은 거부합니다. 테스트는 동시 보완도 다룹니다.

**관련 테스트**：[ProcurementAssetImportServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## asset-occupancy

**문제와 호출 흐름**：하나의 자산을 동시에 이동하고 폐기할 수 없는 이유.

prepareTransfer/prepareDisposal은 한 트랜잭션에서 active_operation_type/active_operation_id를 점유하고 previous_asset_status를 저장합니다. 완료는 현재 작업과 대조하며 거절/취소 시 상태를 복원하고 점유를 해제합니다. 승인은 Workflow에서 실행합니다.

- [AssetOperationWorkflowStateServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImpl.java) — `prepareTransfer`
- [WorkflowCompletionServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java) — `restoreAsset`

**중단점, 실패와 기대 결과**：assetId, version, 점유, previousAssetStatus를 확인합니다. 두 번째 작업은 충돌해야 합니다. 거절/취소 뒤 두 점유 필드는 비고 상태가 복원되며 오래된 완료 이벤트가 새 작업을 해제해서는 안 됩니다.

**관련 테스트**：[AssetOperationWorkflowStateServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImplTest.java) · [WorkflowCompletionServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/WorkflowCompletionServiceImplTest.java)

restoreAsset는 테넌트, 자산 ID, 버전, 현재 상태/점유를 비교한 뒤 한 UPDATE에서 상태를 복원하고 점유를 해제합니다. affected가 1이 아니면 재시도 가능한 예외를 던져 오래된 완료 이벤트가 새 작업을 해제하지 못하게 합니다. previousAssetStatus는 저장된 업무 값이지 이 클래스의 메서드 이름이 아닙니다.

<!-- omni:source path="omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java" -->
```java
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, asset.getTenantId())
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, intent.currentStatus())
                .eq(AstAsset::getActiveOperationType, intent.operationType())
                .eq(AstAsset::getActiveOperationId, intent.operationId())
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, intent.previousStatus())
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, LocalDateTime.now())
                .set(AstAsset::getUpdateBy, "workflow-event")
                .setSql("version = version + 1"));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("恢复资产 Workflow 操作占位失败");
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## cli-atomic

**문제와 호출 흐름**：서비스 폴더 복사나 Maven 모듈 하나 삭제만으로 부족한 이유.

applyRenderedIntegration은 계획을 사전 검증하고 임시 파일과 백업으로 복원 가능한 교체를 수행합니다. applyPresetGeneration은 staging에서 catalog 의존 폐쇄를 잘라내고 lock 검증 후 rename합니다. Gateway, Compose, 마이그레이션, seed, 프런트엔드, 문서도 전달 경계입니다.

- [service-integration-transaction.ts](../../tools/omni-cli/src/service-integration-transaction.ts) — `applyRenderedIntegration`
- [preset-generator.ts](../../tools/omni-cli/src/preset-generator.ts) — `applyPresetGeneration`

**중단점, 실패와 기대 결과**：changes, 선택 모듈, staging, 출력 대상을 확인합니다. 격리 복사본의 경로 충돌은 쓰기 전에 거부되어야 합니다. 실패 시 원래 대상을 보존하고 자체 staging만 정리합니다. 출력을 옮긴 뒤 원래 checkout 없이 Wiki, AI, lock이 유효한지 확인합니다.

**관련 테스트**：[service-integration-renderer.test.ts](../../tools/omni-cli/test/service-integration-renderer.test.ts) · [preset-generator.test.ts](../../tools/omni-cli/test/preset-generator.test.ts)

모든 변경을 준비한 뒤 rename과 쓰기 후 검증을 수행합니다. 아래 안쪽 catch는 현재 파일의 백업 복원을 시도한 뒤 다시 예외를 던지고, 바깥쪽 catch가 rollback을 호출해 완전 복원과 불완전 복원을 구분합니다. 현재 테스트는 39개 변경 중 38번째 쓰기 뒤에 failAfter로 실패를 주입하며, 새 파일 삭제, 원래 파일 복원, 트랜잭션 임시 파일 정리를 확인합니다. failAfter는 테스트 전용이며 공개 CLI 옵션이 아닙니다.

<!-- omni:source path="tools/omni-cli/src/service-integration-transaction.ts" -->
```typescript
  } catch (error) {
        if (item.backup && existsSync(item.backup) && !existsSync(item.target)) {
          renameSync(item.backup, item.target);
        }
        throw error;
      }
      item.applied = true;
      appliedCount += 1;
      if (testOptions.failAfter !== undefined && appliedCount === testOptions.failAfter) {
        throw new Error(`测试注入失败：第 ${appliedCount} 个文件`);
      }
    }

    for (const item of prepared) {
      const actual = readFileSync(item.target, 'utf8');
      if (actual !== item.change.after) throw new Error(`磁盘写后内容不一致：${item.change.target}`);
    }
  } catch (error) {
    const rollbackErrors = rollback(prepared, createdDirectories);
    const detail = error instanceof Error ? error.message : String(error);
    if (rollbackErrors.length > 0) {
      throw new CliError(`接入事务失败且回滚不完整：${detail}；${rollbackErrors.join('; ')}`);
    }
    throw new CliError(`接入事务失败，已完整回滚：${detail}`);
  }
```

<!-- omni:modules:end -->

## 컨테이너 테스트와 디버깅

명령 전에 도구 의존성을 설치합니다. backend runner는 JDK 25/JAVA_HOME을 제공하며 전체 clean install이 백엔드 변경 검증입니다. IDE는 컨테이너 JDWP의 클라이언트로 연결할 수 있습니다. 5005는 로컬로만 바인딩합니다. 중단점은 스레드를 멈추므로 학습 환경에서만 사용합니다. 디버거 이미지는 실제 컨테이너 프로세스를 보여야 하며 소스 이미지는 실행 증거가 아닙니다.
명령 전에 도구 의존성을 설치합니다. backend runner는 JDK 25/JAVA_HOME을 제공하며 전체 clean install이 백엔드 변경 검증입니다. IDE는 컨테이너 JDWP의 클라이언트로 연결할 수 있고 호스트 5005는 로컬로만 바인딩합니다. 중단점은 스레드를 멈추므로 학습 환경에서만 사용합니다. 아래 대상 테스트는 Surefire 인수로 디버깅을 설정하므로 Maven과 테스트 JVM이 5005를 두고 충돌하지 않도록 전역 JAVA_TOOL_OPTIONS를 함께 설정하지 마세요. 디버거 이미지는 실제 컨테이너 프로세스를 보여야 하며 소스 이미지는 실행 증거가 아닙니다.
```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm test
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
# JDWP: only in an isolated teaching environment
# Set JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
# Then connect the IDE to localhost:5005 using the runner's declared port.
```

단언은 동작을 설명하지만 소스 링크는 실행 증거가 아닙니다. 명령, 이미지 ID, 소스 해시, 종료 코드, 단언 결과를 기록하고 token, cookie, 개인정보는 제외합니다.

## Docker 안에서 한 테스트 디버깅하기

실제 업무 데이터에 장애를 넣지 말고 소스 단위와 대응 시험을 선택하세요. 다음은 Outbox CAS 시험만 선택하며 전체 Reactor나 E2E 업무 통과를 뜻하지 않습니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw install -pl omni-common-mqlog -am -DskipTests
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm --service-ports backend ./mvnw test -pl omni-common-mqlog -am -Dtest=MqMessageRelayServiceCasTest -Dsurefire.failIfNoSpecifiedTests=false "-Dmaven.surefire.debug=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
```

두 번째 명령은 Docker의 테스트 JVM을 일시 정지하고 컨테이너 인터페이스에서 연결을 기다리게 합니다. compose.tools.yaml은 호스트의 127.0.0.1:5005에만 포트를 공개합니다. IDE를 이 고정 주소에 연결하고 현재 체크아웃을 소스로 매핑하세요. claimForRelay, casMarkSent/casApplyFailure에 중단점을 두고 획득 횟수, 상태, 변경 행 수를 확인합니다. 두 경쟁자 중 하나만 획득하며 오래된 갱신의 0행 결과를 성공 처리하지 않아야 합니다. 이 단위 테스트는 Mock과 AtomicBoolean으로 획득을 모의하고 별도로 SQL 어노테이션을 검사하므로 실제 데이터베이스/Broker 전달을 증명하지 않습니다. 재개 후 대상 테스트 결과를 확인하세요. 상위 모듈에 같은 이름의 테스트가 없는 것과 대상 테스트 skip은 다릅니다. 자신의 디버깅 컨테이너를 종료하고 업무 포트는 바꾸지 않습니다.

5005가 사용 중이면 소유자와 자신의 디버깅 컨테이너를 확인하고 자신의 프로세스만 중지한 뒤 재시도하세요. 포트를 자동 변경하지 않습니다. docs/scripts/source-excerpts.test.mjs가 인용 코드와 현재 소스의 일치를 확인합니다. 소스가 바뀌면 네 언어 설명을 동기화하고 실제 AI_REVIEW를 진행해야 하며 사람의 문서 검토는 필수가 아닙니다. 중단점/변수 이미지는 실제 연결과 확인 후 등록하고 정적 코드 이미지를 디버깅 증거로 쓰지 마세요.

명령 근거: [Surefire 디버깅 설명](https://maven.apache.org/surefire/maven-surefire-plugin/examples/debugging.html), [JDK 25 JDWP 주소 사양](https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/conninv.html). [이번 소스 확인 기록](../reference/evidence/source-debugging-wiki-review-2026-10-17.json)에 실제 읽기 범위를 남겼습니다. 각 테스트는 실제 단언 범위만 뒷받침하며, 이번 문서 검토에서는 테스트, IDE 연결, 업무 쓰기를 새로 실행하지 않았습니다.
