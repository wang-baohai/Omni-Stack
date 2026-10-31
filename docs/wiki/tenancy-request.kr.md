# 4. 프로젝트 실습: 요청에 신뢰할 테넌트 전달하기

리 씨의 조회를 DB까지 따라갑니다. 회사 선택과 로그인, 게이트웨이의 자격 증명 검사, 서비스의 요청 신원 구성, 해당 규칙에 따른 데이터 조회가 주 흐름입니다.

## 첫 단계: 올바른 회사에서 계정 찾기

회사 선택은 화면 이름만 바꾸지 않습니다. Auth는 해당 테넌트의 활성 사용자를 조회합니다. SysUserMapper의 연속 원문입니다.

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/mapper/SysUserMapper.java" -->
```java
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} AND status = 1")
    SysUser selectByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);
```

tenant_id, username, status=1을 함께 사용하므로 다른 회사의 같은 이름은 다른 사용자로 남습니다. 성공 후 JWT에도 신원 주장의 일부로 tenant_id를 넣습니다.

브라우저가 테넌트 헤더를 보낼 수 있어도 회사를 바꿀 권한이 생기지는 않습니다. 클라이언트 값은 아직 신뢰할 수 없는 입력입니다.

## 두 번째: 검증한 자격 증명으로 신원 헤더 다시 쓰기

보호 요청에서 Gateway는 JWT 서명·기간·사용자와 테넌트 주장·차단 목록을 확인합니다. 성공 후 헤더를 바꾸는 연속 부분입니다.

<!-- omni:source path="omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java" -->
```java
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(h -> {
                                h.remove(AUTH_HEADER);
                                TRUSTED_IDENTITY_HEADERS.forEach(h::remove);
                                h.set(GATEWAY_FORWARDED_HEADER, GATEWAY_FORWARDED_VALUE);
                                // 向后兼容：旧服务仍可读明文身份头；新服务必须验证 X-Gateway-Assertion
                                h.set("X-User-Id", claims.getSubject());
                                h.set("X-Tenant-Id", getClaimAsString(claims, "tenant_id"));
                                h.set("X-User-Name", getClaimAsString(claims, "username"));
                                h.set("X-User-Roles", getClaimAsString(claims, "roles"));
                                h.set("X-User-Scopes", getClaimAsString(claims, "scope"));
                                if (assertionHeader != null) {
                                    h.set(GATEWAY_ASSERTION_HEADER, assertionHeader);
                                }
                            })
                            .build();
```

수신 Bearer와 신뢰 대상 헤더를 지우고 검증된 claims에서 사용자·테넌트·역할·권한을 채우며 해당 경로의 게이트웨이 assertion을 전달합니다. 클라이언트의 X-Tenant-Id가 자격 증명의 회사를 덮어쓰지는 않습니다.

assertion은 하위 서비스가 신원 출처를 검사하는 서명 자료입니다. 현재 userId, tenantId, username, 발급 시각만 서명하고 역할·권한 헤더·메서드·주소·본문은 포함하지 않습니다. 반복 방지용 무작위 식별자 nonce도 없습니다. 역할과 권한은 게이트웨이의 헤더 정리 및 신뢰하는 서비스 네트워크에도 의존하므로 요청 전체를 서명한다고 설명하면 안 됩니다.

## 세 번째: 출처 검증 후 요청 신원 설정

일반 common-service 업무 체인에서 GatewayPreAuthenticationFilter가 assertion·시간·평문 헤더와의 일치를 검사하고 인증 객체를 만듭니다. 그 뒤 ServiceIdentityFilter가 사용자·테넌트 ID가 양의 정수인지 확인하여 ServiceIdentityContext를 설정합니다.

전자는 출처를 검사하고 후자는 서비스가 읽을 위치에 현재 신원을 둡니다. 후자만 호출해서 서명을 검증했다고 할 수 없습니다.

요청 종료 시 정리도 필요합니다. ServiceIdentityFilter의 마지막 부분입니다.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/identity/ServiceIdentityFilter.java" -->
```java
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    userId, tenantId, request.getHeader("X-User-Name")));
            filterChain.doFilter(request, response);
        } finally {
            ServiceIdentityContext.clear();
        }
```

하위 처리에서 오류가 나도 finally가 기록을 지웁니다. 서버는 작업 스레드를 재사용하므로 이전 요청 신원을 다음 요청에 남기면 안 됩니다.

공개 페이지·관리 엔드포인트·내부 호출은 별도의 경로 정책이 있습니다. 모든 HTTP가 이 체인 하나를 사용하는 것은 아닙니다.

## 네 번째: 해당 테이블에 테넌트 제한 추가

common-service는 테넌트 기능이 켜졌을 때 아래 처리기를 등록합니다. 회사는 신뢰한 서비스 신원에서, 대상 테이블은 도메인 정책에서 정합니다.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/config/ServicePersistenceAutoConfiguration.java" -->
```java
        if (properties.getTenant().isEnabled()) {
            TenantTablePolicy tenantPolicy = requireUnique(tenantPolicyProvider, "TenantTablePolicy");
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    return new LongValue(ServiceIdentityContext.requireTenantId());
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    return !tenantPolicy.appliesTo(tableName);
                }
            }));
        }
```

requireTenantId는 유효한 신원이 없으면 실패하며 기본 회사를 추측하지 않습니다. appliesTo는 이 서비스가 담당하는 테넌트 업무 테이블인지 답합니다.

전체 저장소의 CRM·SRM·구매·자산은 crm_, srm_, proc_, ast_를 각각 대상으로 합니다. sys_mq_message는 제외하여 백그라운드 릴레이가 다른 회사의 대기 메시지도 조회하게 합니다. 모든 테이블에 tenant_id가 있다고 가정하면 안 됩니다.

## 한 프로젝트 안의 서로 다른 경로

Flowable은 승인을 진행하는 엔진입니다. 업무 확장 테이블은 프로젝트 추가 정보, 엔진 테이블은 프레임워크 실행 정보를 저장하므로 소속을 따로 정합니다. JDBC는 DB 연결을 직접 사용하는 방법이며 이 MyBatis 플러그인을 자동으로 거치지 않습니다.

| 경로 | 현재 처리 | 바로 추론할 수 없는 것 |
|---|---|---|
| Auth 계정 등의 쿼리 | Mapper/Service가 명시적 테넌트 조건과 자체 범위 처리를 결합 | 일반 TenantLine이 없으므로 도메인 설정이 증거가 되지 않음 |
| common-service 도메인 | 기능 스위치·신원·테이블 정책·범위 처리기 결합 | 직접 JDBC와 매핑 밖 테이블의 자동 보호 |
| Workflow 확장과 엔진 | wf_와 ACT_*를 따로 처리하고 자체 컨텍스트·엔진 조건 사용 | 엔진 테이블을 도메인 플러그인에만 맡길 수 없음 |
| 내부 서비스와 백그라운드 | 해당 인증과 명시적 테넌트 인수 | 브라우저 스레드 신원 자동 상속, 모든 호출의 공개 게이트웨이 통과 |

<!-- omni:modules:workflow -->
### 워크플로 서비스의 현재 제한

Workflow MybatisPlusConfig는 TenantInfoHolder를 읽고 다음 기본 분기가 있습니다.

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/MybatisPlusConfig.java" -->
```java
            @Override
            public Expression getTenantId() {
                // 从 SecurityContext 或请求头中获取租户 ID，默认返回 1
                String tenantId = com.omni.common.workflow.tenant.TenantInfoHolder.getTenantId();
                return new LongValue(tenantId != null ? Long.parseLong(tenantId) : 1L);
            }
```

컨텍스트가 없을 때 1을 쓰는 것은 현재 구현이지 일반 권장 방식이 아닙니다. “회사가 없으면 첫 회사”로 복사하거나 common-service의 신원 누락 거부가 이 분기에도 적용되었다고 설명하면 안 됩니다.

향후 강화하려면 초기화·내부 호출·백그라운드·엔진 배포를 따로 검증해야 합니다. 설명을 통일하려고 내부 동작을 수정하지는 않습니다.
<!-- omni:modules:end -->

## 쓰기에는 추가 조건이 있음

회사를 제한해도 작업 권한·행 소유 관계·현재 상태·버전·영향받은 행 수 검사가 필요합니다. 목록에서 안 보이는 것과 수정할 수 없는 것은 별도 책임입니다.

다음은 [역할·조직·데이터 범위](tenancy-permissions.kr.md)에서 회사 내부의 분담을 살펴봅니다.
