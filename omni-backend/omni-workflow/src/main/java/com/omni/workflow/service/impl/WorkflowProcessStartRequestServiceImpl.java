package com.omni.workflow.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.workflow.entity.WfProcessStartRequest;
import com.omni.workflow.mapper.WfProcessStartRequestMapper;
import com.omni.workflow.service.WorkflowProcessStartRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 流程启动幂等请求服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class WorkflowProcessStartRequestServiceImpl implements WorkflowProcessStartRequestService {

    private static final int REQUEST_ID_MAX_LENGTH = 64;
    private static final int BUSINESS_TYPE_MAX_LENGTH = 100;
    private static final int BUSINESS_KEY_MAX_LENGTH = 255;
    private static final int PROCESS_INSTANCE_ID_MAX_LENGTH = 64;
    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final WfProcessStartRequestMapper requestMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Reservation reserve(StartReservationRequest request) {
        if (request == null) {
            throw new BusinessException(400, "流程启动预留请求不能为空");
        }
        requirePositive(request.tenantId(), "租户 ID");
        requirePositive(request.modelVersionId(), "流程模型版本 ID");
        requirePositive(request.startUserId(), "发起人用户 ID");
        String normalizedRequestId =
                requireText(request.requestId(), REQUEST_ID_MAX_LENGTH, "请求 ID");
        String normalizedBusinessType = requireText(
                request.businessType(), BUSINESS_TYPE_MAX_LENGTH, "业务类型");
        String normalizedBusinessKey = requireText(
                request.businessKey(), BUSINESS_KEY_MAX_LENGTH, "业务主键");

        LocalDateTime now = LocalDateTime.now();
        WfProcessStartRequest candidate = new WfProcessStartRequest();
        candidate.setTenantId(request.tenantId());
        candidate.setRequestId(normalizedRequestId);
        candidate.setBusinessType(normalizedBusinessType);
        candidate.setBusinessKey(normalizedBusinessKey);
        candidate.setModelVersionId(request.modelVersionId());
        candidate.setStartUserId(request.startUserId());
        candidate.setStatus(WfProcessStartRequest.STATUS_RESERVED);
        candidate.setRetryCount(0);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);

        if (requestMapper.insertIgnore(candidate) == 1) {
            return new Reservation(candidate, true, true);
        }
        return resolveConflict(candidate);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<WfProcessStartRequest> findByRequestId(Long tenantId, String requestId) {
        requirePositive(tenantId, "租户 ID");
        String normalizedRequestId = requireText(requestId, REQUEST_ID_MAX_LENGTH, "请求 ID");
        return Optional.ofNullable(requestMapper.selectByRequestId(tenantId, normalizedRequestId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<WfProcessStartRequest> findByBusinessKey(Long tenantId,
                                                             String businessType,
                                                             String businessKey) {
        requirePositive(tenantId, "租户 ID");
        String normalizedBusinessType = requireText(
                businessType, BUSINESS_TYPE_MAX_LENGTH, "业务类型");
        String normalizedBusinessKey = requireText(
                businessKey, BUSINESS_KEY_MAX_LENGTH, "业务主键");
        return Optional.ofNullable(requestMapper.selectByBusinessKey(
                tenantId, normalizedBusinessType, normalizedBusinessKey));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Reservation retry(Long tenantId, Long id) {
        requirePositive(tenantId, "租户 ID");
        requirePositive(id, "启动记录 ID");
        WfProcessStartRequest existing = requireById(tenantId, id);
        if (!WfProcessStartRequest.STATUS_FAILED.equals(existing.getStatus())) {
            requireKnownStatus(existing);
            return new Reservation(existing, false, false);
        }

        int rows = requestMapper.reserveRetry(tenantId, id, LocalDateTime.now());
        WfProcessStartRequest current = requireById(tenantId, id);
        return new Reservation(current, rows == 1, false);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markStarted(Long tenantId, Long id, String processInstanceId) {
        requirePositive(tenantId, "租户 ID");
        requirePositive(id, "启动记录 ID");
        String normalizedProcessInstanceId = requireText(
                processInstanceId, PROCESS_INSTANCE_ID_MAX_LENGTH, "流程实例 ID");
        int rows = requestMapper.markStarted(
                tenantId, id, normalizedProcessInstanceId, LocalDateTime.now());
        if (rows == 1) {
            return;
        }

        WfProcessStartRequest existing = requireById(tenantId, id);
        if (WfProcessStartRequest.STATUS_STARTED.equals(existing.getStatus())) {
            if (normalizedProcessInstanceId.equals(existing.getProcessInstanceId())) {
                return;
            }
            throw new BusinessException(409, "启动记录已关联其他流程实例");
        }
        throw new BusinessException(409, "启动记录当前状态不允许标记成功: " + existing.getStatus());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markFailed(Long tenantId, Long id, String error) {
        requirePositive(tenantId, "租户 ID");
        requirePositive(id, "启动记录 ID");
        String normalizedError = normalizeError(error);
        int rows = requestMapper.markFailed(tenantId, id, normalizedError, LocalDateTime.now());
        if (rows == 1) {
            return;
        }

        WfProcessStartRequest existing = requireById(tenantId, id);
        if (WfProcessStartRequest.STATUS_STARTED.equals(existing.getStatus())
                || WfProcessStartRequest.STATUS_FAILED.equals(existing.getStatus())) {
            return;
        }
        throw new BusinessException(409, "启动记录当前状态不允许标记失败: " + existing.getStatus());
    }

    private Reservation resolveConflict(WfProcessStartRequest candidate) {
        WfProcessStartRequest byRequest = requestMapper.selectByRequestId(
                candidate.getTenantId(), candidate.getRequestId());
        WfProcessStartRequest byBusiness = requestMapper.selectByBusinessKey(
                candidate.getTenantId(), candidate.getBusinessType(), candidate.getBusinessKey());

        if (byRequest != null && byBusiness != null
                && !Objects.equals(byRequest.getId(), byBusiness.getId())) {
            throw new BusinessException(409, "请求 ID 与业务键分别关联了不同的流程启动记录");
        }
        if (byRequest != null) {
            if (byBusiness == null) {
                throw new BusinessException(409, "请求 ID 已被其他业务使用");
            }
            requireSamePayload(byRequest, candidate);
            return reserveFailedReplay(byRequest);
        }
        if (byBusiness != null) {
            requireSameProcessIntent(byBusiness, candidate);
            return new Reservation(byBusiness, false, false);
        }
        throw new BusinessException(409, "流程启动预留发生并发冲突，请重试");
    }

    private Reservation reserveFailedReplay(WfProcessStartRequest existing) {
        requireKnownStatus(existing);
        if (!WfProcessStartRequest.STATUS_FAILED.equals(existing.getStatus())) {
            return new Reservation(existing, false, false);
        }
        int rows = requestMapper.reserveRetry(
                existing.getTenantId(), existing.getId(), LocalDateTime.now());
        WfProcessStartRequest current = requireById(existing.getTenantId(), existing.getId());
        return new Reservation(current, rows == 1, false);
    }

    private void requireSamePayload(WfProcessStartRequest existing,
                                    WfProcessStartRequest candidate) {
        if (!Objects.equals(existing.getBusinessType(), candidate.getBusinessType())
                || !Objects.equals(existing.getBusinessKey(), candidate.getBusinessKey())) {
            throw new BusinessException(409, "请求 ID 已被其他业务使用");
        }
        requireSameProcessIntent(existing, candidate);
    }

    private void requireSameProcessIntent(WfProcessStartRequest existing,
                                          WfProcessStartRequest candidate) {
        if (!Objects.equals(existing.getModelVersionId(), candidate.getModelVersionId())
                || !Objects.equals(existing.getStartUserId(), candidate.getStartUserId())) {
            throw new BusinessException(409, "幂等重放的流程模型或发起人与原请求不一致");
        }
        requireKnownStatus(existing);
    }

    private WfProcessStartRequest requireById(Long tenantId, Long id) {
        WfProcessStartRequest request = requestMapper.selectByTenantAndId(tenantId, id);
        if (request == null) {
            throw new BusinessException(404, "流程启动记录不存在");
        }
        return request;
    }

    private void requireKnownStatus(WfProcessStartRequest request) {
        String status = request.getStatus();
        if (!WfProcessStartRequest.STATUS_RESERVED.equals(status)
                && !WfProcessStartRequest.STATUS_STARTED.equals(status)
                && !WfProcessStartRequest.STATUS_FAILED.equals(status)) {
            throw new BusinessException(409, "未知的流程启动状态: " + status);
        }
    }

    private String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(400, fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BusinessException(400, fieldName + "必须为正整数");
        }
    }

    private String normalizeError(String error) {
        String normalized = error == null || error.isBlank() ? "流程启动失败" : error.trim();
        return normalized.length() <= LAST_ERROR_MAX_LENGTH
                ? normalized : normalized.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
