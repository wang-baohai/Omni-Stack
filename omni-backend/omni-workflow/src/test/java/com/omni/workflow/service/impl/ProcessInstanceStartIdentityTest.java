package com.omni.workflow.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 普通流程启动的可信身份边界回归，验证首个同步节点可使用真实发起人。 */
@ExtendWith(MockitoExtension.class)
class ProcessInstanceStartIdentityTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private WfProcessModelVersionMapper versionMapper;

    @InjectMocks
    private ProcessInstanceServiceImpl service;

    /** 清理测试线程的身份与租户，避免污染后续用例。 */
    @AfterEach
    void clearContext() {
        TenantInfoHolder.clear();
        Authentication.setAuthenticatedUserId(null);
    }

    /** 版本启动在进入引擎前已覆盖伪造发起人，业务变量仍可使用。 */
    @Test
    void versionStartExposesTrustedActorBeforeFirstNode() {
        WfProcessModelVersion version = new WfProcessModelVersion();
        version.setTenantId(1L);
        version.setStatus("PUBLISHED");
        version.setProcessDefinitionId("approval:1:definition");
        when(versionMapper.selectById(7L)).thenReturn(version);
        RuntimeException boundary = new IllegalStateException("已检查引擎首个节点边界");
        when(runtimeService.startProcessInstanceById(eq("approval:1:definition"), eq("owned-business"), anyMap()))
                .thenAnswer(invocation -> {
                    assertTrustedVariables(invocation.getArgument(2));
                    throw boundary;
                });
        StartProcessRequest request = request();
        request.setModelVersionId(7L);
        assertThatThrownBy(() -> service.start(request, 202L, "requester", 1L)).isSameAs(boundary);
        assertThat(Authentication.getAuthenticatedUserId()).isNull();
        assertThat(request.getVariables()).containsEntry("initiator", "999");
    }

    /** 兼容定义标识启动也必须在同步候选解析之前注入可信身份。 */
    @Test
    void legacyKeyStartExposesTrustedActorBeforeFirstNode() {
        TenantInfoHolder.setTenantId("1");
        RuntimeException boundary = new IllegalStateException("已检查引擎首个节点边界");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(eq("approval"), eq("owned-business"), anyMap(), eq("1")))
                .thenAnswer(invocation -> {
                    assertTrustedVariables(invocation.getArgument(2));
                    throw boundary;
                });
        StartProcessRequest request = request();
        request.setProcessKey("approval");
        assertThatThrownBy(() -> service.start(request, 202L, "requester", 1L)).isSameAs(boundary);
        assertThat(Authentication.getAuthenticatedUserId()).isNull();
    }

    /** 指定他人为发起人仍被拒绝，不能到达引擎。 */
    @Test
    void simulatedOtherActorCannotReachEngine() {
        StartProcessRequest request = request();
        request.setSimulateUserId(999L);
        assertThatThrownBy(() -> service.start(request, 202L, "requester", 1L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(runtimeService, versionMapper);
    }

    private static void assertTrustedVariables(Map<String, Object> variables) {
        assertThat(variables).containsEntry("initiator", "202").containsEntry("amount", "1500.00");
        assertThat(variables).doesNotContainKeys("startUserId", "authenticatedUserId", "START_USER_ID");
        assertThat(Authentication.getAuthenticatedUserId()).isEqualTo("202");
    }

    private static StartProcessRequest request() {
        return StartProcessRequest.builder().title("普通启动身份回归").businessKey("owned-business")
                .variables(Map.of("initiator", "999", "startUserId", "999", "authenticatedUserId", "999",
                        "START_USER_ID", "999", "amount", "1500.00")).build();
    }
}
