package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.QualificationService;
import com.omni.srm.service.RiskService;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** SRM 供应商资质服务实现。 */
@Service
@RequiredArgsConstructor
public class QualificationServiceImpl implements QualificationService {

    private static final Set<String> ASSESSABLE_STATUSES = Set.of(
            "APPROVED", "SUSPENDED", "BLACKLISTED");

    private final SrmSupplierQualificationMapper qualificationMapper;
    private final SrmRecordAccessGuard accessGuard;
    private final RiskService riskService;

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.QualificationVO> list(Long supplierId) {
        accessGuard.requireSupplier(supplierId);
        return qualificationMapper.selectList(new LambdaQueryWrapper<SrmSupplierQualification>()
                        .eq(SrmSupplierQualification::getSupplierId, supplierId)
                        .orderByAsc(SrmSupplierQualification::getId)).stream()
                .map(SrmViewAssembler::qualification).toList();
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.QualificationVO get(Long supplierId, Long id) {
        return SrmViewAssembler.qualification(requireQualification(supplierId, id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.QualificationVO create(Long supplierId, SrmRequests.CreateQualificationRequest request) {
        SrmSupplier supplier = accessGuard.requireSupplier(supplierId);
        validateDateRange(request.getIssueDate(), request.getExpiryDate());
        SrmSupplierQualification qualification = new SrmSupplierQualification();
        qualification.setTenantId(SrmTenantContext.requireTenantId());
        qualification.setSupplierId(supplierId);
        qualification.setQualificationName(request.getQualificationName());
        qualification.setCertificateNo(request.getCertificateNo());
        qualification.setIssuingAuthority(request.getIssuingAuthority());
        qualification.setIssueDate(request.getIssueDate());
        qualification.setExpiryDate(request.getExpiryDate());
        qualification.setStatus("ACTIVE");
        qualification.setVersion(0);
        qualification.setDeleted(0);
        SrmAuditSupport.created(qualification);
        qualificationMapper.insert(qualification);
        recalculateCertificateRisk(supplier, "供应商资质创建后自动重算");
        return SrmViewAssembler.qualification(qualification);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.QualificationVO update(Long supplierId, Long id,
                                           SrmRequests.UpdateQualificationRequest request) {
        SrmSupplier supplier = accessGuard.requireSupplier(supplierId);
        SrmSupplierQualification current = requireQualification(supplierId, id);
        LocalDate issueDate = request.getIssueDate() == null ? current.getIssueDate() : request.getIssueDate();
        LocalDate expiryDate = request.getExpiryDate() == null ? current.getExpiryDate() : request.getExpiryDate();
        validateDateRange(issueDate, expiryDate);
        LambdaUpdateWrapper<SrmSupplierQualification> update = new LambdaUpdateWrapper<SrmSupplierQualification>()
                .eq(SrmSupplierQualification::getId, id)
                .eq(SrmSupplierQualification::getVersion, request.getVersion())
                .eq(SrmSupplierQualification::getDeleted, 0)
                .setSql("version = version + 1");
        if (request.getQualificationName() != null) update.set(SrmSupplierQualification::getQualificationName, request.getQualificationName());
        if (request.getCertificateNo() != null) update.set(SrmSupplierQualification::getCertificateNo, request.getCertificateNo());
        if (request.getIssuingAuthority() != null) update.set(SrmSupplierQualification::getIssuingAuthority, request.getIssuingAuthority());
        if (request.getIssueDate() != null) update.set(SrmSupplierQualification::getIssueDate, request.getIssueDate());
        if (request.getExpiryDate() != null) update.set(SrmSupplierQualification::getExpiryDate, request.getExpiryDate());
        update.set(SrmSupplierQualification::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierQualification::getUpdateBy, SrmTenantContext.require().username());
        if (qualificationMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        recalculateCertificateRisk(supplier, "供应商资质更新后自动重算");
        return get(supplierId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long supplierId, Long id, Integer version) {
        requireDeleteVersion(version);
        SrmSupplier supplier = accessGuard.requireSupplier(supplierId);
        requireQualification(supplierId, id);
        LambdaUpdateWrapper<SrmSupplierQualification> update = new LambdaUpdateWrapper<SrmSupplierQualification>()
                .eq(SrmSupplierQualification::getId, id)
                .eq(SrmSupplierQualification::getVersion, version)
                .eq(SrmSupplierQualification::getDeleted, 0)
                .set(SrmSupplierQualification::getDeleted, 1)
                .setSql("version = version + 1");
        update.set(SrmSupplierQualification::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierQualification::getUpdateBy, SrmTenantContext.require().username());
        if (qualificationMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        recalculateCertificateRisk(supplier, "供应商资质删除后自动重算");
    }

    private void requireDeleteVersion(Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "删除版本号必须为非负整数");
        }
    }

    private SrmSupplierQualification requireQualification(Long supplierId, Long id) {
        SrmSupplierQualification qualification = accessGuard.requireQualification(id);
        if (!supplierId.equals(qualification.getSupplierId())) {
            throw new BusinessException(404, "资质不存在");
        }
        return qualification;
    }

    private void validateDateRange(LocalDate issueDate, LocalDate expiryDate) {
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate)) {
            throw new BusinessException(400, "资质到期日期不能早于签发日期");
        }
    }

    private void recalculateCertificateRisk(SrmSupplier supplier, String remark) {
        if (!ASSESSABLE_STATUSES.contains(supplier.getStatus())) {
            return;
        }
        SrmRequests.CreateRiskAssessmentRequest request = new SrmRequests.CreateRiskAssessmentRequest();
        request.setRemark(remark);
        riskService.createAssessment(supplier.getId(), request);
    }
}
