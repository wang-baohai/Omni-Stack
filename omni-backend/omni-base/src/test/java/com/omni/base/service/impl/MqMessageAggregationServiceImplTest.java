package com.omni.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.base.client.CrmMqMessageInternalClient;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** MQ 跨服务聚合服务测试。 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MqMessageAggregationServiceImplTest {

    @Mock private SysMqMessageMapper mapper;
    @Mock private CrmMqMessageInternalClient crmClient;

    /** 本地与 CRM 消息必须按时间合并并保留总数。 */
    @Test
    void shouldMergeLocalAndCrmPages() {
        SysMqMessage local = message("local", LocalDateTime.of(2026, 8, 17, 9, 0));
        SysMqMessage remote = message("remote", LocalDateTime.of(2026, 8, 17, 10, 0));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(
                new Page<SysMqMessage>(1, 10).setRecords(List.of(local)).setTotal(1));
        when(crmClient.list(ArgumentMatchers.eq(1L), any(), any(), any(), any(),
                any(), any(), ArgumentMatchers.eq(1), ArgumentMatchers.eq(10)))
                .thenReturn(R.ok(new PageResult<>(List.of(remote), 1, 10, 1)));
        MqMessageAggregationServiceImpl service = new MqMessageAggregationServiceImpl(mapper, crmClient);

        PageResult<SysMqMessage> result = service.list(1L, null, null, null,
                null, null, null, 1, 10);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(SysMqMessage::getMsgId)
                .containsExactly("remote", "local");
    }

    /** CRM 网络故障必须转换为可恢复的 503，而不是伪装为空列表。 */
    @Test
    void shouldFailAggregationWhenCrmIsUnavailable() {
        when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));
        when(crmClient.list(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(FeignException.class);
        MqMessageAggregationServiceImpl service = new MqMessageAggregationServiceImpl(mapper, crmClient);

        assertThatThrownBy(() -> service.list(1L, null, null, null,
                null, null, null, 1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
    }

    private SysMqMessage message(String msgId, LocalDateTime createTime) {
        SysMqMessage message = new SysMqMessage();
        message.setMsgId(msgId);
        message.setCreateTime(createTime);
        return message;
    }
}
