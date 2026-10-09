package com.omni.base.service.impl;

import com.omni.base.client.CrmMqMessageInternalClient;
import com.omni.common.core.result.BusinessException;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工 resend/skip 并发保护（CAS）单元测试（Codex 审计 MQ-02 P1 纠偏）。
 *
 * <p>核心合同：管理操作禁止「先读后无条件写」，必须使用带状态前置条件的单语句
 * UPDATE；受影响 0 行说明状态已被并发修改，必须抛 400 业务异常显式告知调用方
 * "操作未生效"，而不是静默把 SKIPPED/SENT/PROCESSING 覆盖回 PENDING。</p>
 */
@ExtendWith(MockitoExtension.class)
class MqMessageAggregationCasTest {

    private static final Long TENANT_ID = 41L;
    private static final String MSG_ID = "msg-cas-admin-1";

    @Mock private SysMqMessageMapper mapper;
    @Mock private CrmMqMessageInternalClient crmClient;

    /** 对已 SKIPPED 的消息执行 resend：CAS 0 行 → 400，且不得发生任何盲目写。 */
    @Test
    void should_rejectResendWith400_when_messageAlreadySkipped() {
        // 读出快照为 SKIPPED（读后状态随时可能被并发修改，守卫只能落在 UPDATE 条件上）
        when(mapper.selectOne(any())).thenReturn(message(SysMqMessage.STATUS_SKIPPED));
        when(mapper.casAdminResend(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        MqMessageAggregationServiceImpl service = newService();

        assertThatThrownBy(() -> service.resend(TENANT_ID, MSG_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作未生效")
                .extracting("code").isEqualTo(400);
        verify(mapper, times(1)).casAdminResend(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class));
        // 不得先读后无条件写：updateById 全程禁止出现
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** 并发重复 resend 同一 DEAD_LETTER：第一个成功，第二个 CAS 0 行 → 400，不重复重投。 */
    @Test
    void should_rejectSecondResend_when_stateAlreadyChangedByConcurrentOperator() {
        when(mapper.selectOne(any())).thenReturn(message(SysMqMessage.STATUS_DEAD_LETTER));
        // 第一次条件更新命中 1 行；第二次因状态已变为 PENDING 命中 0 行
        when(mapper.casAdminResend(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class)))
                .thenReturn(1, 0);
        MqMessageAggregationServiceImpl service = newService();

        assertThatCode(() -> service.resend(TENANT_ID, MSG_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.resend(TENANT_ID, MSG_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        verify(mapper, times(2)).casAdminResend(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class));
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** 对非 DEAD_LETTER 的消息执行 skip：CAS 0 行 → 400，不得覆盖新状态。 */
    @Test
    void should_rejectSkipWith400_when_skipCasAffectsNoRows() {
        when(mapper.selectOne(any())).thenReturn(message(SysMqMessage.STATUS_SENT));
        when(mapper.casAdminSkip(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        MqMessageAggregationServiceImpl service = newService();

        assertThatThrownBy(() -> service.skip(TENANT_ID, MSG_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作未生效")
                .extracting("code").isEqualTo(400);
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** 对 DEAD_LETTER 的 skip：CAS 命中 1 行 → 正常成功。 */
    @Test
    void should_skipSuccessfully_when_deadLetterCasHits() {
        when(mapper.selectOne(any())).thenReturn(message(SysMqMessage.STATUS_DEAD_LETTER));
        when(mapper.casAdminSkip(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class)))
                .thenReturn(1);
        MqMessageAggregationServiceImpl service = newService();

        assertThatCode(() -> service.skip(TENANT_ID, MSG_ID)).doesNotThrowAnyException();
        verify(mapper, times(1)).casAdminSkip(eq(TENANT_ID), eq(MSG_ID), any(LocalDateTime.class));
        verify(mapper, never()).updateById(any(SysMqMessage.class));
    }

    /** resend CAS SQL 静态断言：仅允许 FAILED/DEAD_LETTER 起始，且必须保留租户隔离条件。 */
    @Test
    void should_keepTenantAndStatusGuard_when_inspectingResendCasSql() {
        String sql = updateSqlOf("casAdminResend");
        assertThat(sql)
                .as("resend 必须以 tenant_id + msg_id + 状态前置条件为守卫")
                .contains("tenant_id = #{tenantId}")
                .contains("msg_id = #{msgId}")
                .contains("status IN (2, 3)");
    }

    /** skip CAS SQL 静态断言：仅允许 DEAD_LETTER 起始，且必须保留租户隔离条件。 */
    @Test
    void should_keepTenantAndStatusGuard_when_inspectingSkipCasSql() {
        String sql = updateSqlOf("casAdminSkip");
        assertThat(sql)
                .as("skip 必须以 tenant_id + msg_id + status=3 为守卫")
                .contains("tenant_id = #{tenantId}")
                .contains("msg_id = #{msgId}")
                .contains("status = 3");
    }

    private MqMessageAggregationServiceImpl newService() {
        return new MqMessageAggregationServiceImpl(mapper, crmClient);
    }

    /** 反射读取 Mapper 接口方法上的 @Update SQL，用于静态断言 CAS 前置条件。 */
    private static String updateSqlOf(String methodName) {
        for (Method method : SysMqMessageMapper.class.getMethods()) {
            if (method.getName().equals(methodName)) {
                Update update = method.getAnnotation(Update.class);
                assertThat(update)
                        .as("Mapper 方法 %s 必须是单语句 @Update 原子操作", methodName)
                        .isNotNull();
                return String.join(" ", update.value());
            }
        }
        throw new AssertionError("SysMqMessageMapper 缺少方法: " + methodName);
    }

    private SysMqMessage message(int status) {
        SysMqMessage message = new SysMqMessage();
        message.setId(7L);
        message.setMsgId(MSG_ID);
        message.setTenantId(TENANT_ID);
        message.setStatus(status);
        message.setRetryCount(3);
        message.setMaxRetry(3);
        message.setCreateTime(LocalDateTime.now().minusMinutes(5));
        return message;
    }
}
