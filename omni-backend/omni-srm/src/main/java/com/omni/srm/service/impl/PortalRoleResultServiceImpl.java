package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmStateMachine.SupplierStatus;
import com.omni.srm.dto.PortalRoleResultEvent;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierEnrollment;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmSupplierEnrollmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.service.PortalRoleResultService;
import com.omni.srm.service.support.SrmAuditSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 门户角色分配结果处理服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalRoleResultServiceImpl implements PortalRoleResultService {

    private static final String ENROLLMENT_PENDING = "PENDING_ROLE_ASSIGN";
    private static final String ENROLLMENT_FAILED = "ROLE_ASSIGN_FAILED";
    private static final String ENROLLMENT_COMPLETED = "COMPLETED";
    private static final String ENROLLMENT_CANCELLED = "CANCELLED";

    private final SrmSupplierEnrollmentMapper enrollmentMapper;
    private final SrmSupplierPortalUserMapper portalUserMapper;
    private final SrmSupplierMapper supplierMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void handle(PortalRoleResultEvent event) {
        SrmSupplierEnrollment enrollment = enrollmentMapper.selectOne(
                new LambdaQueryWrapper<SrmSupplierEnrollment>()
                        .eq(SrmSupplierEnrollment::getTenantId, event.getTenantId())
                        .eq(SrmSupplierEnrollment::getRequestId, event.getRequestId())
                        .eq(SrmSupplierEnrollment::getDeleted, 0));
        if (enrollment == null) {
            throw new BusinessException(404, "角色分配结果找不到对应入驻申请");
        }
        validateIdentity(enrollment, event);
        if (ENROLLMENT_CANCELLED.equals(enrollment.getStatus())) {
            log.info("门户入驻已取消，忽略角色结果: requestId={}", event.getRequestId());
            return;
        }
        if ("SUCCESS".equals(event.getResult())) {
            handleSuccess(enrollment, event);
        } else if ("FAILED".equals(event.getResult())) {
            handleFailure(enrollment, event);
        } else {
            throw new BusinessException(400, "未知的门户角色分配结果");
        }
    }

    private void handleSuccess(SrmSupplierEnrollment enrollment, PortalRoleResultEvent event) {
        if (ENROLLMENT_COMPLETED.equals(enrollment.getStatus())) {
            log.info("门户角色成功结果已处理: requestId={}", event.getRequestId());
            return;
        }
        if (!Set.of(ENROLLMENT_PENDING, ENROLLMENT_FAILED).contains(enrollment.getStatus())) {
            throw new BusinessException(409, "当前入驻状态不能完成角色分配");
        }

        SrmSupplierPortalUser portalUser = portalUserMapper.selectOne(
                new LambdaQueryWrapper<SrmSupplierPortalUser>()
                        .eq(SrmSupplierPortalUser::getTenantId, event.getTenantId())
                        .eq(SrmSupplierPortalUser::getUserId, event.getUserId())
                        .eq(SrmSupplierPortalUser::getDeleted, 0));
        if (portalUser == null) {
            portalUser = new SrmSupplierPortalUser();
            portalUser.setTenantId(event.getTenantId());
            portalUser.setSupplierId(event.getSupplierId());
            portalUser.setUserId(event.getUserId());
            portalUser.setStatus("ACTIVE");
            portalUser.setVersion(0);
            portalUser.setDeleted(0);
            SrmAuditSupport.created(portalUser);
            try {
                portalUserMapper.insert(portalUser);
            } catch (DuplicateKeyException exception) {
                // 唯一键负责并发仲裁；让 MQ 重投后走 COMPLETED 幂等分支。
                throw new IllegalStateException("门户用户关联正在并发处理，请重试", exception);
            }
        } else if (!event.getSupplierId().equals(portalUser.getSupplierId())) {
            throw new BusinessException(409, "门户用户已关联其他供应商");
        } else if (!"ACTIVE".equals(portalUser.getStatus())) {
            int portalRows = portalUserMapper.update(null,
                    new LambdaUpdateWrapper<SrmSupplierPortalUser>()
                            .eq(SrmSupplierPortalUser::getTenantId, event.getTenantId())
                            .eq(SrmSupplierPortalUser::getId, portalUser.getId())
                            .eq(SrmSupplierPortalUser::getSupplierId, event.getSupplierId())
                            .eq(SrmSupplierPortalUser::getUserId, event.getUserId())
                            .eq(SrmSupplierPortalUser::getVersion, portalUser.getVersion())
                            .eq(SrmSupplierPortalUser::getDeleted, 0)
                            .set(SrmSupplierPortalUser::getStatus, "ACTIVE")
                            .set(SrmSupplierPortalUser::getUpdateTime, LocalDateTime.now())
                            .set(SrmSupplierPortalUser::getUpdateBy, "portal-role-saga")
                            .setSql("version = version + 1"));
            if (portalRows != 1) {
                throw new BusinessException(409, "门户用户关联已被其他请求修改");
            }
        }

        SrmSupplier supplier = requireSupplier(event.getTenantId(), event.getSupplierId());
        if (Set.of(SupplierStatus.REGISTERING.name(), SupplierStatus.REGISTERING_FAILED.name())
                .contains(supplier.getStatus())) {
            int supplierRows = supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                    .eq(SrmSupplier::getTenantId, event.getTenantId())
                    .eq(SrmSupplier::getId, event.getSupplierId())
                    .eq(SrmSupplier::getVersion, supplier.getVersion())
                    .in(SrmSupplier::getStatus,
                            SupplierStatus.REGISTERING.name(), SupplierStatus.REGISTERING_FAILED.name())
                    .eq(SrmSupplier::getDeleted, 0)
                    .set(SrmSupplier::getStatus, SupplierStatus.PENDING_REVIEW.name())
                    .set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                    .set(SrmSupplier::getUpdateBy, "portal-role-saga")
                    .setSql("version = version + 1"));
            if (supplierRows != 1) {
                throw new BusinessException(409, "供应商状态已被其他请求修改");
            }
        } else if (!SupplierStatus.PENDING_REVIEW.name().equals(supplier.getStatus())) {
            throw new BusinessException(409, "供应商状态不能完成入驻");
        }

        int enrollmentRows = enrollmentMapper.update(null,
                new LambdaUpdateWrapper<SrmSupplierEnrollment>()
                        .eq(SrmSupplierEnrollment::getTenantId, event.getTenantId())
                        .eq(SrmSupplierEnrollment::getId, enrollment.getId())
                        .eq(SrmSupplierEnrollment::getVersion, enrollment.getVersion())
                        .in(SrmSupplierEnrollment::getStatus, ENROLLMENT_PENDING, ENROLLMENT_FAILED)
                        .eq(SrmSupplierEnrollment::getDeleted, 0)
                        .set(SrmSupplierEnrollment::getStatus, ENROLLMENT_COMPLETED)
                        .set(SrmSupplierEnrollment::getLastErrorCode, null)
                        .set(SrmSupplierEnrollment::getNextRetryTime, null)
                        .set(SrmSupplierEnrollment::getUpdateTime, LocalDateTime.now())
                        .set(SrmSupplierEnrollment::getUpdateBy, "portal-role-saga")
                        .setSql("version = version + 1"));
        if (enrollmentRows != 1) {
            throw new BusinessException(409, "入驻申请已被其他结果处理");
        }
        log.info("门户角色分配 Saga 完成: requestId={}, supplierId={}",
                event.getRequestId(), event.getSupplierId());
    }

    private void handleFailure(SrmSupplierEnrollment enrollment, PortalRoleResultEvent event) {
        if (ENROLLMENT_COMPLETED.equals(enrollment.getStatus())) {
            log.info("门户入驻已完成，忽略迟到的失败结果: requestId={}", event.getRequestId());
            return;
        }
        if (ENROLLMENT_FAILED.equals(enrollment.getStatus())) {
            log.info("门户角色失败结果已处理: requestId={}", event.getRequestId());
            return;
        }
        if (!ENROLLMENT_PENDING.equals(enrollment.getStatus())) {
            throw new BusinessException(409, "当前入驻状态不能标记角色分配失败");
        }

        SrmSupplier supplier = requireSupplier(event.getTenantId(), event.getSupplierId());
        if (SupplierStatus.REGISTERING.name().equals(supplier.getStatus())) {
            int supplierRows = supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                    .eq(SrmSupplier::getTenantId, event.getTenantId())
                    .eq(SrmSupplier::getId, event.getSupplierId())
                    .eq(SrmSupplier::getVersion, supplier.getVersion())
                    .eq(SrmSupplier::getStatus, SupplierStatus.REGISTERING.name())
                    .eq(SrmSupplier::getDeleted, 0)
                    .set(SrmSupplier::getStatus, SupplierStatus.REGISTERING_FAILED.name())
                    .set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                    .set(SrmSupplier::getUpdateBy, "portal-role-saga")
                    .setSql("version = version + 1"));
            if (supplierRows != 1) {
                throw new BusinessException(409, "供应商状态已被其他请求修改");
            }
        } else if (!SupplierStatus.REGISTERING_FAILED.name().equals(supplier.getStatus())) {
            throw new BusinessException(409, "供应商状态不能标记入驻失败");
        }

        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(retryDelaySeconds(enrollment));
        int enrollmentRows = enrollmentMapper.update(null,
                new LambdaUpdateWrapper<SrmSupplierEnrollment>()
                        .eq(SrmSupplierEnrollment::getTenantId, event.getTenantId())
                        .eq(SrmSupplierEnrollment::getId, enrollment.getId())
                        .eq(SrmSupplierEnrollment::getVersion, enrollment.getVersion())
                        .eq(SrmSupplierEnrollment::getStatus, ENROLLMENT_PENDING)
                        .eq(SrmSupplierEnrollment::getDeleted, 0)
                        .set(SrmSupplierEnrollment::getStatus, ENROLLMENT_FAILED)
                        .set(SrmSupplierEnrollment::getLastErrorCode,
                                event.getErrorCode() == null ? "ROLE_ASSIGN_FAILED" : event.getErrorCode())
                        .set(SrmSupplierEnrollment::getNextRetryTime, nextRetryTime)
                        .set(SrmSupplierEnrollment::getUpdateTime, LocalDateTime.now())
                        .set(SrmSupplierEnrollment::getUpdateBy, "portal-role-saga")
                        .setSql("version = version + 1"));
        if (enrollmentRows != 1) {
            throw new BusinessException(409, "入驻申请已被其他结果处理");
        }
        log.warn("门户角色分配 Saga 失败: requestId={}, errorCode={}",
                event.getRequestId(), event.getErrorCode());
    }

    private void validateIdentity(SrmSupplierEnrollment enrollment, PortalRoleResultEvent event) {
        if (!event.getTenantId().equals(enrollment.getTenantId())
                || !event.getSupplierId().equals(enrollment.getSupplierId())
                || !event.getUserId().equals(enrollment.getUserId())
                || !"SUPPLIER".equals(event.getRoleCode())) {
            throw new BusinessException(409, "角色分配结果与原入驻申请不一致");
        }
    }

    private SrmSupplier requireSupplier(Long tenantId, Long supplierId) {
        SrmSupplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getId, supplierId)
                .eq(SrmSupplier::getDeleted, 0));
        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        return supplier;
    }

    private long retryDelaySeconds(SrmSupplierEnrollment enrollment) {
        int retryCount = enrollment.getRetryCount() == null ? 0 : enrollment.getRetryCount();
        int exponent = Math.min(retryCount + 1, 6);
        return Math.min(600L, (1L << exponent) * 10L);
    }
}
