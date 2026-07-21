package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmStateMachine;
import com.omni.srm.domain.SrmStateMachine.SupplierStatus;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierEnrollment;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmSupplierEnrollmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.PortalInviteService;
import com.omni.srm.service.SupplierPortalService;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SupplierNameNormalizer;
import com.omni.srm.service.support.SupplierRiskInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SRM 供应商门户服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class SupplierPortalServiceImpl implements SupplierPortalService {

    private static final String ENROLLMENT_PENDING = "PENDING_ROLE_ASSIGN";
    private static final String ENROLLMENT_FAILED = "ROLE_ASSIGN_FAILED";
    private static final String ENROLLMENT_COMPLETED = "COMPLETED";
    private static final String ENROLLMENT_CANCELLED = "CANCELLED";
    private static final String PORTAL_ROLE_CODE = "SUPPLIER";

    private final SrmSupplierMapper supplierMapper;
    private final SrmSupplierPortalUserMapper portalUserMapper;
    private final SrmSupplierEnrollmentMapper enrollmentMapper;
    private final PortalInviteService portalInviteService;
    private final SupplierRiskInitializer supplierRiskInitializer;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.EnrollmentVO enroll(SrmRequests.EnrollRequest request) {
        try {
            return SrmDataScopeContext.runAsPortal(() -> doEnroll(request));
        } catch (DuplicateKeyException exception) {
            BusinessException conflict = new BusinessException(
                    409, "入驻申请已存在或正在并发处理，请查询当前入驻进度");
            conflict.initCause(exception);
            throw conflict;
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public SrmViews.EnrollmentVO getEnrollment() {
        return SrmDataScopeContext.runAsPortal(() -> {
            SrmSupplierEnrollment enrollment = findLatestEnrollmentForCurrentUser(false);
            return enrollment == null ? null : enrollmentView(enrollment);
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.EnrollmentVO retryEnrollment() {
        return SrmDataScopeContext.runAsPortal(this::doRetryEnrollment);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public SrmViews.PortalProfileVO getProfile() {
        return SrmDataScopeContext.runAsPortal(() -> buildProfile(requirePortalSupplierId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.PortalProfileVO updateProfile(SrmRequests.UpdateProfileRequest request) {
        return SrmDataScopeContext.runAsPortal(() -> doUpdateProfile(request));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.PortalProfileVO resubmitProfile(SrmRequests.StatusRequest request) {
        return SrmDataScopeContext.runAsPortal(() -> doResubmitProfile(request));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Long getCurrentSupplierId() {
        return SrmDataScopeContext.runAsPortal(this::requirePortalSupplierId);
    }

    private SrmViews.EnrollmentVO doEnroll(SrmRequests.EnrollRequest request) {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long userId = SrmTenantContext.require().userId();
        String requestId = request.getRequestId().trim();
        String creditCode = normalizeCreditCode(request.getCreditCode());

        SrmSupplierEnrollment sameRequest = findEnrollmentByRequestId(tenantId, requestId);
        if (sameRequest != null) {
            requireSameApplicant(sameRequest, userId);
            requireSameCreditCode(sameRequest, creditCode);
            return enrollmentView(sameRequest);
        }

        SrmSupplierEnrollment activeEnrollment = findLatestEnrollmentForCurrentUser(true);
        if (activeEnrollment != null) {
            requireSameCreditCode(activeEnrollment, creditCode);
            return enrollmentView(activeEnrollment);
        }

        SrmSupplierPortalUser existingPortalUser = findPortalUser(tenantId, userId);
        if (existingPortalUser != null) {
            throw new BusinessException(409, "当前用户已关联供应商，不能重复入驻");
        }

        SrmSupplier sameCreditSupplier = supplierMapper.selectOne(new LambdaQueryWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getCreditCode, creditCode)
                .eq(SrmSupplier::getDeleted, 0));
        if (sameCreditSupplier != null) {
            throw new BusinessException(409, "该统一社会信用代码已存在入驻记录");
        }

        Long inviteId = portalInviteService.consumeInviteToken(request.getInviteToken());
        SrmSupplier supplier = createRegisteringSupplier(request, tenantId, creditCode);
        supplierRiskInitializer.initialize(tenantId, supplier.getId());
        SrmSupplierEnrollment enrollment = createEnrollment(
                tenantId, supplier.getId(), userId, inviteId, requestId);
        sendPortalRoleAssignEvent(supplier.getId(), supplier.getVersion(), tenantId, userId, requestId);
        return enrollmentView(enrollment);
    }

    private SrmViews.EnrollmentVO doRetryEnrollment() {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long userId = SrmTenantContext.require().userId();
        SrmSupplierEnrollment enrollment = findLatestEnrollmentForCurrentUser(false);
        if (enrollment == null) {
            throw new BusinessException(404, "当前用户没有入驻申请");
        }
        if (ENROLLMENT_PENDING.equals(enrollment.getStatus())
                || ENROLLMENT_COMPLETED.equals(enrollment.getStatus())) {
            return enrollmentView(enrollment);
        }
        if (!ENROLLMENT_FAILED.equals(enrollment.getStatus())) {
            throw new BusinessException(409, "当前入驻状态不允许重试");
        }

        LocalDateTime now = LocalDateTime.now();
        if (enrollment.getNextRetryTime() != null && now.isBefore(enrollment.getNextRetryTime())) {
            throw new BusinessException(429, "角色分配重试过于频繁，请在建议重试时间后再试");
        }
        String operator = operator();
        int enrollmentRows = enrollmentMapper.update(null,
                new LambdaUpdateWrapper<SrmSupplierEnrollment>()
                        .eq(SrmSupplierEnrollment::getTenantId, tenantId)
                        .eq(SrmSupplierEnrollment::getId, enrollment.getId())
                        .eq(SrmSupplierEnrollment::getUserId, userId)
                        .eq(SrmSupplierEnrollment::getStatus, ENROLLMENT_FAILED)
                        .eq(SrmSupplierEnrollment::getVersion, enrollment.getVersion())
                        .eq(SrmSupplierEnrollment::getDeleted, 0)
                        .set(SrmSupplierEnrollment::getStatus, ENROLLMENT_PENDING)
                        .set(SrmSupplierEnrollment::getLastErrorCode, null)
                        .set(SrmSupplierEnrollment::getNextRetryTime, null)
                        .set(SrmSupplierEnrollment::getUpdateTime, now)
                        .set(SrmSupplierEnrollment::getUpdateBy, operator)
                        .setSql("retry_count = retry_count + 1, version = version + 1"));
        if (enrollmentRows != 1) {
            throw new BusinessException(409, "入驻申请已被其他请求处理，请刷新后重试");
        }

        int supplierRows = supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getId, enrollment.getSupplierId())
                .eq(SrmSupplier::getStatus, SupplierStatus.REGISTERING_FAILED.name())
                .eq(SrmSupplier::getDeleted, 0)
                .set(SrmSupplier::getStatus, SupplierStatus.REGISTERING.name())
                .set(SrmSupplier::getUpdateTime, now)
                .set(SrmSupplier::getUpdateBy, operator)
                .setSql("version = version + 1"));
        SrmSupplier supplier = requireSupplier(tenantId, enrollment.getSupplierId());
        if (supplierRows != 1 && !SupplierStatus.REGISTERING.name().equals(supplier.getStatus())) {
            throw new BusinessException(409, "供应商状态不允许重试角色分配");
        }

        sendPortalRoleAssignEvent(supplier.getId(), supplier.getVersion(), tenantId, userId,
                enrollment.getRequestId());
        enrollment.setStatus(ENROLLMENT_PENDING);
        enrollment.setRetryCount(enrollment.getRetryCount() + 1);
        enrollment.setLastErrorCode(null);
        enrollment.setNextRetryTime(null);
        enrollment.setVersion(enrollment.getVersion() + 1);
        return enrollmentView(enrollment);
    }

    private SrmSupplier createRegisteringSupplier(SrmRequests.EnrollRequest request,
                                                   Long tenantId,
                                                   String creditCode) {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setTenantId(tenantId);
        supplier.setSupplierNo("TMP-" + UUID.randomUUID());
        supplier.setName(request.getName().trim());
        supplier.setNormalizedName(SupplierNameNormalizer.normalize(request.getName()));
        supplier.setSupplierType(request.getSupplierType() == null || request.getSupplierType().isBlank()
                ? "OTHER" : request.getSupplierType().trim());
        supplier.setIndustryCode(request.getIndustryCode());
        supplier.setCreditCode(creditCode);
        supplier.setWebsite(request.getWebsite());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setRegion(request.getRegion());
        supplier.setAddress(request.getAddress());
        supplier.setLevelCode("QUALIFIED");
        supplier.setStatus(SupplierStatus.REGISTERING.name());
        // 门户账号不是内部采购负责人，入驻审核前保持未分配。
        supplier.setOwnerUserId(null);
        supplier.setOwnerUnitId(null);
        supplier.setVersion(0);
        supplier.setDeleted(0);
        SrmAuditSupport.created(supplier);
        supplierMapper.insert(supplier);

        String supplierNo = "SP" + tenantId + "-" + supplier.getId();
        int rows = supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getId, supplier.getId())
                .eq(SrmSupplier::getDeleted, 0)
                .set(SrmSupplier::getSupplierNo, supplierNo));
        if (rows != 1) {
            throw new BusinessException(409, "供应商编号生成失败，请重试");
        }
        supplier.setSupplierNo(supplierNo);
        return supplier;
    }

    private SrmSupplierEnrollment createEnrollment(Long tenantId,
                                                     Long supplierId,
                                                     Long userId,
                                                     Long inviteId,
                                                     String requestId) {
        SrmSupplierEnrollment enrollment = new SrmSupplierEnrollment();
        enrollment.setTenantId(tenantId);
        enrollment.setSupplierId(supplierId);
        enrollment.setUserId(userId);
        enrollment.setRequestId(requestId);
        enrollment.setInviteId(inviteId);
        enrollment.setStatus(ENROLLMENT_PENDING);
        enrollment.setRetryCount(0);
        enrollment.setVersion(0);
        enrollment.setDeleted(0);
        SrmAuditSupport.created(enrollment);
        enrollmentMapper.insert(enrollment);
        return enrollment;
    }

    private SrmViews.PortalProfileVO doUpdateProfile(SrmRequests.UpdateProfileRequest request) {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long supplierId = requirePortalSupplierId();
        SrmSupplier supplier = requireSupplier(tenantId, supplierId);
        LambdaUpdateWrapper<SrmSupplier> update = new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getId, supplierId)
                .eq(SrmSupplier::getVersion, request.getVersion())
                .eq(SrmSupplier::getDeleted, 0)
                .setSql("version = version + 1");
        if (request.getName() != null) {
            update.set(SrmSupplier::getName, request.getName().trim());
            update.set(SrmSupplier::getNormalizedName, SupplierNameNormalizer.normalize(request.getName()));
        }
        if (request.getWebsite() != null) update.set(SrmSupplier::getWebsite, request.getWebsite());
        if (request.getPhone() != null) update.set(SrmSupplier::getPhone, request.getPhone());
        if (request.getEmail() != null) update.set(SrmSupplier::getEmail, request.getEmail());
        if (request.getRegion() != null) update.set(SrmSupplier::getRegion, request.getRegion());
        if (request.getAddress() != null) update.set(SrmSupplier::getAddress, request.getAddress());
        update.set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplier::getUpdateBy, operator());
        if (supplierMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        return buildProfile(supplierId);
    }

    private SrmViews.PortalProfileVO doResubmitProfile(SrmRequests.StatusRequest request) {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long supplierId = requirePortalSupplierId();
        SrmSupplier supplier = requireSupplier(tenantId, supplierId);
        SupplierStatus currentStatus = SrmStateMachine.parse(supplier.getStatus());
        if (SupplierStatus.REJECTED != currentStatus) {
            throw new BusinessException(409, "仅审核驳回的供应商可以重新提交");
        }
        SrmStateMachine.requireTransition(currentStatus, SupplierStatus.PENDING_REVIEW);

        LocalDateTime now = LocalDateTime.now();
        int rows = supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getId, supplierId)
                .eq(SrmSupplier::getVersion, request.getVersion())
                .eq(SrmSupplier::getStatus, SupplierStatus.REJECTED.name())
                .eq(SrmSupplier::getDeleted, 0)
                .set(SrmSupplier::getStatus, SupplierStatus.PENDING_REVIEW.name())
                .set(SrmSupplier::getUpdateTime, now)
                .set(SrmSupplier::getUpdateBy, operator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        supplier.setStatus(SupplierStatus.PENDING_REVIEW.name());
        supplier.setVersion(request.getVersion() + 1);
        return profileView(supplier);
    }

    private Long requirePortalSupplierId() {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long userId = SrmTenantContext.require().userId();
        SrmSupplierPortalUser portalUser = findPortalUser(tenantId, userId);
        if (portalUser == null || !"ACTIVE".equals(portalUser.getStatus())) {
            throw new BusinessException(403, "当前用户不是已授权的门户供应商");
        }
        return portalUser.getSupplierId();
    }

    private SrmSupplierPortalUser findPortalUser(Long tenantId, Long userId) {
        return portalUserMapper.selectOne(new LambdaQueryWrapper<SrmSupplierPortalUser>()
                .eq(SrmSupplierPortalUser::getTenantId, tenantId)
                .eq(SrmSupplierPortalUser::getUserId, userId)
                .eq(SrmSupplierPortalUser::getDeleted, 0));
    }

    private SrmSupplierEnrollment findEnrollmentByRequestId(Long tenantId, String requestId) {
        return enrollmentMapper.selectOne(new LambdaQueryWrapper<SrmSupplierEnrollment>()
                .eq(SrmSupplierEnrollment::getTenantId, tenantId)
                .eq(SrmSupplierEnrollment::getRequestId, requestId)
                .eq(SrmSupplierEnrollment::getDeleted, 0));
    }

    private SrmSupplierEnrollment findLatestEnrollmentForCurrentUser(boolean activeOnly) {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long userId = SrmTenantContext.require().userId();
        LambdaQueryWrapper<SrmSupplierEnrollment> query = new LambdaQueryWrapper<SrmSupplierEnrollment>()
                .eq(SrmSupplierEnrollment::getTenantId, tenantId)
                .eq(SrmSupplierEnrollment::getUserId, userId)
                .eq(SrmSupplierEnrollment::getDeleted, 0);
        if (activeOnly) {
            query.ne(SrmSupplierEnrollment::getStatus, ENROLLMENT_CANCELLED);
        }
        return enrollmentMapper.selectOne(query
                .orderByDesc(SrmSupplierEnrollment::getCreateTime)
                .orderByDesc(SrmSupplierEnrollment::getId)
                .last("LIMIT 1"));
    }

    private void requireSameApplicant(SrmSupplierEnrollment enrollment, Long userId) {
        if (!userId.equals(enrollment.getUserId())) {
            throw new BusinessException(409, "请求 ID 已被其他入驻申请使用");
        }
    }

    private void requireSameCreditCode(SrmSupplierEnrollment enrollment, String creditCode) {
        SrmSupplier supplier = requireSupplier(enrollment.getTenantId(), enrollment.getSupplierId());
        if (!creditCode.equals(normalizeCreditCode(supplier.getCreditCode()))) {
            throw new BusinessException(409, "该幂等请求与原入驻企业不一致");
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

    private SrmViews.PortalProfileVO buildProfile(Long supplierId) {
        SrmSupplier supplier = requireSupplier(SrmTenantContext.requireTenantId(), supplierId);
        return profileView(supplier);
    }

    private SrmViews.PortalProfileVO profileView(SrmSupplier supplier) {
        SrmViews.PortalProfileVO vo = new SrmViews.PortalProfileVO();
        vo.setSupplierId(supplier.getId());
        vo.setSupplierNo(supplier.getSupplierNo());
        vo.setName(supplier.getName());
        vo.setSupplierType(supplier.getSupplierType());
        vo.setCreditCode(supplier.getCreditCode());
        vo.setWebsite(supplier.getWebsite());
        vo.setPhone(supplier.getPhone());
        vo.setEmail(supplier.getEmail());
        vo.setRegion(supplier.getRegion());
        vo.setAddress(supplier.getAddress());
        vo.setStatus(supplier.getStatus());
        vo.setVersion(supplier.getVersion());
        return vo;
    }

    private SrmViews.EnrollmentVO enrollmentView(SrmSupplierEnrollment enrollment) {
        SrmViews.EnrollmentVO vo = new SrmViews.EnrollmentVO();
        vo.setRequestId(enrollment.getRequestId());
        vo.setSupplierId(enrollment.getSupplierId());
        vo.setStatus(enrollment.getStatus());
        vo.setRetryCount(enrollment.getRetryCount());
        vo.setLastErrorCode(enrollment.getLastErrorCode());
        vo.setNextRetryTime(enrollment.getNextRetryTime());
        return vo;
    }

    private void sendPortalRoleAssignEvent(Long supplierId,
                                           Integer supplierVersion,
                                           Long tenantId,
                                           Long userId,
                                           String requestId) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType("srm.portal-role.assign-requested.v1")
                .occurredAt(LocalDateTime.now())
                .tenantId(tenantId)
                .producer("omni-srm")
                .aggregateType("SUPPLIER")
                .aggregateId(supplierId)
                .aggregateVersion(supplierVersion)
                .actorUserId(userId)
                .correlationId(requestId)
                .payload(Map.of(
                        "requestId", requestId,
                        "tenantId", tenantId,
                        "supplierId", supplierId,
                        "userId", userId,
                        "roleCode", PORTAL_ROLE_CODE,
                        "result", "REQUESTED"))
                .build();
        reliableMessageRelay.send("srm-domain-out-0", envelope, tenantId, eventId);
    }

    private String normalizeCreditCode(String creditCode) {
        return creditCode.trim().toUpperCase(Locale.ROOT);
    }

    private String operator() {
        String username = SrmTenantContext.require().username();
        return username == null || username.isBlank()
                ? String.valueOf(SrmTenantContext.require().userId()) : username;
    }
}
