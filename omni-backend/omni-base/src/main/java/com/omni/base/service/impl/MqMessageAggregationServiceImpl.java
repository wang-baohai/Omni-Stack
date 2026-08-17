package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.base.client.CrmMqMessageInternalClient;
import com.omni.base.service.MqMessageAggregationService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import com.omni.common.web.TraceIdFilter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 跨服务 MQ 消息聚合服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqMessageAggregationServiceImpl implements MqMessageAggregationService {

    private static final String BASE_SERVICE = "omni-base";
    private static final String CRM_SERVICE = "omni-crm";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysMqMessageMapper sysMqMessageMapper;
    private final CrmMqMessageInternalClient crmClient;

    /** {@inheritDoc} */
    @Override
    public PageResult<SysMqMessage> list(Long tenantId, Integer status, String topic, String msgKey,
                                         String serviceName, LocalDateTime beginTime, LocalDateTime endTime,
                                         int page, int size) {
        validatePage(page, size);
        int fetchSize = Math.toIntExact((long) page * size);
        boolean queryBase = !CRM_SERVICE.equals(serviceName);
        boolean queryCrm = !BASE_SERVICE.equals(serviceName);

        PageResult<SysMqMessage> basePage = queryBase
                ? queryLocal(tenantId, status, topic, msgKey, serviceName, beginTime, endTime, fetchSize)
                : emptyPage(fetchSize);
        PageResult<SysMqMessage> crmPage = queryCrm
                ? queryCrm(tenantId, status, topic, msgKey, serviceName, beginTime, endTime, fetchSize)
                : emptyPage(fetchSize);

        List<SysMqMessage> merged = new ArrayList<>(basePage.getRecords());
        merged.addAll(crmPage.getRecords());
        merged.sort(Comparator.comparing(SysMqMessage::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SysMqMessage::getMsgId, Comparator.nullsLast(Comparator.reverseOrder())));

        int fromIndex = Math.min((page - 1) * size, merged.size());
        int toIndex = Math.min(fromIndex + size, merged.size());
        long total = basePage.getTotal() + crmPage.getTotal();
        return new PageResult<>(List.copyOf(merged.subList(fromIndex, toIndex)), total, size, page);
    }

    /** {@inheritDoc} */
    @Override
    public SysMqMessage getByMsgId(Long tenantId, String msgId) {
        SysMqMessage local = findLocal(tenantId, msgId);
        if (local != null) {
            return local;
        }
        R<SysMqMessage> response = callCrm(() -> crmClient.getByMsgId(tenantId, msgId));
        if (response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(404, "消息不存在");
        }
        return response.getData();
    }

    /** {@inheritDoc} */
    @Override
    public void resend(Long tenantId, String msgId) {
        SysMqMessage local = findLocal(tenantId, msgId);
        if (local != null) {
            resetLocal(local);
            return;
        }
        ensureSuccess(callCrm(() -> crmClient.resend(tenantId, msgId)));
    }

    /** {@inheritDoc} */
    @Override
    public void skip(Long tenantId, String msgId) {
        SysMqMessage local = findLocal(tenantId, msgId);
        if (local != null) {
            if (local.getStatus() != SysMqMessage.STATUS_DEAD_LETTER) {
                throw new BusinessException(400, "仅死信状态的消息可标记忽略");
            }
            local.setStatus(SysMqMessage.STATUS_SKIPPED);
            local.setUpdateTime(LocalDateTime.now());
            sysMqMessageMapper.updateById(local);
            return;
        }
        ensureSuccess(callCrm(() -> crmClient.skip(tenantId, msgId)));
    }

    private PageResult<SysMqMessage> queryLocal(Long tenantId, Integer status, String topic,
                                                 String msgKey, String serviceName,
                                                 LocalDateTime beginTime, LocalDateTime endTime,
                                                 int fetchSize) {
        LambdaQueryWrapper<SysMqMessage> wrapper = buildWrapper(
                tenantId, status, topic, msgKey, serviceName, beginTime, endTime);
        Page<SysMqMessage> result = sysMqMessageMapper.selectPage(new Page<>(1, fetchSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), fetchSize, 1);
    }

    private PageResult<SysMqMessage> queryCrm(Long tenantId, Integer status, String topic,
                                               String msgKey, String serviceName,
                                               LocalDateTime beginTime, LocalDateTime endTime,
                                               int fetchSize) {
        R<PageResult<SysMqMessage>> response = callCrm(() -> crmClient.list(
                tenantId, status, topic, msgKey, serviceName,
                format(beginTime), format(endTime), 1, fetchSize));
        if (response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "CRM 消息记录查询失败");
        }
        return response.getData();
    }

    private LambdaQueryWrapper<SysMqMessage> buildWrapper(
            Long tenantId, Integer status, String topic, String msgKey, String serviceName,
            LocalDateTime beginTime, LocalDateTime endTime) {
        return new LambdaQueryWrapper<SysMqMessage>()
                .eq(SysMqMessage::getTenantId, tenantId)
                .eq(status != null, SysMqMessage::getStatus, status)
                .like(hasText(topic), SysMqMessage::getTopic, topic)
                .like(hasText(msgKey), SysMqMessage::getMsgKey, msgKey)
                .eq(hasText(serviceName), SysMqMessage::getServiceName, serviceName)
                .ge(beginTime != null, SysMqMessage::getCreateTime, beginTime)
                .le(endTime != null, SysMqMessage::getCreateTime, endTime)
                .orderByDesc(SysMqMessage::getCreateTime)
                .orderByDesc(SysMqMessage::getId);
    }

    private SysMqMessage findLocal(Long tenantId, String msgId) {
        return sysMqMessageMapper.selectOne(new LambdaQueryWrapper<SysMqMessage>()
                .eq(SysMqMessage::getTenantId, tenantId)
                .eq(SysMqMessage::getMsgId, msgId)
                .last("LIMIT 1"));
    }

    private void resetLocal(SysMqMessage message) {
        int status = message.getStatus();
        if (status != SysMqMessage.STATUS_PENDING
                && status != SysMqMessage.STATUS_FAILED
                && status != SysMqMessage.STATUS_DEAD_LETTER) {
            throw new BusinessException(400, "仅 PENDING/FAILED/DEAD_LETTER 状态的消息可重发");
        }
        message.setStatus(SysMqMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setNextRetryTime(null);
        message.setErrorMsg(null);
        message.setUpdateTime(LocalDateTime.now());
        sysMqMessageMapper.updateById(message);
    }

    private void ensureSuccess(R<Void> response) {
        if (response == null) {
            throw new BusinessException(503, "CRM 服务返回了空响应");
        }
        if (response.getCode() != 200) {
            int code = response.getCode() == 404 ? 404 : 400;
            throw new BusinessException(code, response.getMessage());
        }
    }

    private <T> R<T> callCrm(RemoteCall<T> call) {
        try {
            R<T> response = call.execute();
            if (response == null) {
                throw new BusinessException(503, "CRM 服务返回了空响应");
            }
            return response;
        } catch (FeignException ex) {
            log.warn("聚合 CRM MQ 消息失败: traceId={}, status={}, url={}, cause={}",
                    MDC.get(TraceIdFilter.MDC_KEY), ex.status(),
                    ex.request() == null ? "-" : ex.request().url(),
                    ex.getClass().getName(), ex);
            throw new BusinessException(503, "CRM 服务暂不可用");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100 || (long) page * size > 10_000) {
            throw new BusinessException(400, "分页参数不合法，size 为 1-100 且最多检索前 10000 条");
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PageResult<SysMqMessage> emptyPage(int size) {
        return new PageResult<>(List.of(), 0, size, 1);
    }

    @FunctionalInterface
    private interface RemoteCall<T> {
        R<T> execute();
    }
}
