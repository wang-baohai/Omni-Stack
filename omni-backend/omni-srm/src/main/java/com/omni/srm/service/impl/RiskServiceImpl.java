package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.srm.domain.SrmRiskCalculator;
import com.omni.srm.domain.SrmRiskCalculator.RiskLevel;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmRiskAssessment;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.mapper.SrmRiskAssessmentMapper;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.RiskService;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import com.omni.srm.service.support.SupplierRiskInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SRM 风险指标与评估服务实现。 */
@Service
@RequiredArgsConstructor
public class RiskServiceImpl implements RiskService {

    private static final String CERTIFICATE = "CERTIFICATE";

    private final SrmRiskIndicatorMapper riskIndicatorMapper;
    private final SrmRiskAssessmentMapper riskAssessmentMapper;
    private final SrmSupplierQualificationMapper qualificationMapper;
    private final SrmSupplierMapper supplierMapper;
    private final SrmRecordAccessGuard recordAccessGuard;
    private final SupplierRiskInitializer riskInitializer;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    public PageResult<SrmViews.RiskSupplierSummaryVO> listCurrentRisks(
            String riskLevel, int page, int size) {
        requirePage(page, size);
        String normalizedLevel = null;
        if (riskLevel != null && !riskLevel.isBlank()) {
            normalizedLevel = SrmRiskCalculator.parseLevel(riskLevel).name();
        }
        Page<SrmViews.RiskSupplierSummaryVO> result = riskAssessmentMapper.selectCurrentRiskPage(
                new Page<>(page, size), normalizedLevel);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.RiskProfileVO profile(Long supplierId) {
        recordAccessGuard.requireSupplier(supplierId);
        List<SrmRiskIndicator> indicators = findIndicators(supplierId);
        List<SrmRiskAssessment> history = findAssessmentHistory(supplierId);
        SrmViews.RiskProfileVO vo = new SrmViews.RiskProfileVO();
        vo.setSupplierId(supplierId);
        vo.setIndicators(indicators.stream().map(SrmViewAssembler::riskIndicator).toList());
        vo.setLatestAssessment(history.isEmpty() ? null : SrmViewAssembler.riskAssessment(history.getFirst()));
        vo.setHistory(history.stream().map(SrmViewAssembler::riskAssessment).toList());
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.RiskIndicatorVO> listIndicators(Long supplierId) {
        recordAccessGuard.requireSupplier(supplierId);
        return findIndicators(supplierId).stream().map(SrmViewAssembler::riskIndicator).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskIndicatorVO updateIndicator(
            Long id, SrmRequests.UpdateRiskIndicatorRequest request) {
        SrmRiskIndicator indicator = recordAccessGuard.requireRiskIndicator(id);
        SrmRiskCalculator.requireManuallyEditable(SrmRiskCalculator.parseType(indicator.getIndicatorType()));
        lockSupplier(indicator.getSupplierId());
        if (request.getRiskLevel() != null) {
            SrmRiskCalculator.parseLevel(request.getRiskLevel());
        }
        LambdaUpdateWrapper<SrmRiskIndicator> update = new LambdaUpdateWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getId, id)
                .eq(SrmRiskIndicator::getVersion, request.getVersion())
                .eq(SrmRiskIndicator::getDeleted, 0)
                .setSql("version = version + 1");
        if (request.getIndicatorValue() != null) {
            update.set(SrmRiskIndicator::getIndicatorValue, request.getIndicatorValue());
        }
        if (request.getRiskLevel() != null) {
            update.set(SrmRiskIndicator::getRiskLevel, request.getRiskLevel());
        }
        if (request.getRemark() != null) {
            update.set(SrmRiskIndicator::getRemark, request.getRemark());
        }
        update.set(SrmRiskIndicator::getAssessmentTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getUpdateTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getUpdateBy, SrmTenantContext.require().username());
        requireUpdated(riskIndicatorMapper.update(null, update));
        createAssessmentInternal(indicator.getSupplierId(), "风险指标更新后自动重算");
        return SrmViewAssembler.riskIndicator(recordAccessGuard.requireRiskIndicator(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskAssessmentVO createAssessment(
            Long supplierId, SrmRequests.CreateRiskAssessmentRequest request) {
        lockSupplier(supplierId);
        return SrmViewAssembler.riskAssessment(createAssessmentInternal(supplierId, request.getRemark()));
    }

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.RiskAssessmentVO> assessmentHistory(Long supplierId) {
        recordAccessGuard.requireSupplier(supplierId);
        return findAssessmentHistory(supplierId).stream().map(SrmViewAssembler::riskAssessment).toList();
    }

    private SrmRiskAssessment createAssessmentInternal(Long supplierId, String remark) {
        Long tenantId = SrmTenantContext.requireTenantId();
        riskInitializer.initialize(tenantId, supplierId);
        refreshCertificateIndicator(supplierId);
        List<SrmRiskIndicator> indicators = findIndicators(supplierId);
        RiskLevel overallLevel = SrmRiskCalculator.highest(indicators.stream().map(indicator -> {
            SrmRiskCalculator.parseType(indicator.getIndicatorType());
            return SrmRiskCalculator.parseLevel(indicator.getRiskLevel());
        }).toList());
        SrmRiskAssessment previous = riskAssessmentMapper.selectOne(
                new LambdaQueryWrapper<SrmRiskAssessment>()
                        .eq(SrmRiskAssessment::getSupplierId, supplierId)
                        .orderByDesc(SrmRiskAssessment::getAssessmentTime)
                        .orderByDesc(SrmRiskAssessment::getId)
                        .last("LIMIT 1"));

        SrmRiskAssessment assessment = new SrmRiskAssessment();
        assessment.setTenantId(tenantId);
        assessment.setSupplierId(supplierId);
        assessment.setOverallLevel(overallLevel.name());
        assessment.setAssessmentTime(LocalDateTime.now());
        assessment.setAssessorUserId(SrmTenantContext.require().userId());
        assessment.setRemark(remark);
        assessment.setVersion(0);
        assessment.setDeleted(0);
        SrmAuditSupport.created(assessment);
        riskAssessmentMapper.insert(assessment);

        if (SrmRiskCalculator.shouldNotifyRed(previous == null ? null : previous.getOverallLevel(), overallLevel)) {
            sendRiskLevelChangedEvent(previous, assessment);
        }
        return assessment;
    }

    private void refreshCertificateIndicator(Long supplierId) {
        List<SrmSupplierQualification> qualifications = qualificationMapper.selectList(
                new LambdaQueryWrapper<SrmSupplierQualification>()
                        .eq(SrmSupplierQualification::getSupplierId, supplierId));
        CertificateRisk certificateRisk = calculateCertificateRisk(qualifications);
        SrmRiskIndicator indicator = riskIndicatorMapper.selectOne(new LambdaQueryWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getSupplierId, supplierId)
                .eq(SrmRiskIndicator::getIndicatorType, CERTIFICATE)
                .last("LIMIT 1"));
        if (indicator == null) {
            throw new BusinessException(409, "供应商资质风险指标初始化失败");
        }
        LambdaUpdateWrapper<SrmRiskIndicator> update = new LambdaUpdateWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getId, indicator.getId())
                .eq(SrmRiskIndicator::getVersion, indicator.getVersion())
                .eq(SrmRiskIndicator::getDeleted, 0)
                .set(SrmRiskIndicator::getRiskLevel, certificateRisk.level().name())
                .set(SrmRiskIndicator::getIndicatorValue,
                        certificateRisk.expiryDate() == null ? null : certificateRisk.expiryDate().toString())
                .set(SrmRiskIndicator::getAssessmentTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getRemark, certificateRisk.remark())
                .set(SrmRiskIndicator::getUpdateTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getUpdateBy, SrmTenantContext.require().username())
                .setSql("version = version + 1");
        requireUpdated(riskIndicatorMapper.update(null, update));
    }

    private CertificateRisk calculateCertificateRisk(List<SrmSupplierQualification> qualifications) {
        LocalDate today = LocalDate.now();
        CertificateRisk result = new CertificateRisk(RiskLevel.GREEN, null, "暂无资质到期风险");
        for (SrmSupplierQualification qualification : qualifications) {
            if (qualification.getExpiryDate() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, qualification.getExpiryDate());
            RiskLevel level = SrmRiskCalculator.certificateLevel(today, qualification.getExpiryDate());
            String remark = days < 0 ? "资质已过期" : days <= 30
                    ? "资质将在 " + days + " 天后到期" : "资质有效";
            if (level.ordinal() > result.level().ordinal()
                    || level == result.level() && earlier(qualification.getExpiryDate(), result.expiryDate())) {
                result = new CertificateRisk(level, qualification.getExpiryDate(), remark);
            }
        }
        return result;
    }

    private boolean earlier(LocalDate candidate, LocalDate current) {
        return current == null || candidate.isBefore(current);
    }

    private List<SrmRiskIndicator> findIndicators(Long supplierId) {
        return riskIndicatorMapper.selectList(new LambdaQueryWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getSupplierId, supplierId)
                .orderByAsc(SrmRiskIndicator::getIndicatorType));
    }

    private List<SrmRiskAssessment> findAssessmentHistory(Long supplierId) {
        return riskAssessmentMapper.selectList(new LambdaQueryWrapper<SrmRiskAssessment>()
                .eq(SrmRiskAssessment::getSupplierId, supplierId)
                .orderByDesc(SrmRiskAssessment::getAssessmentTime)
                .orderByDesc(SrmRiskAssessment::getId)
                .last("LIMIT 100"));
    }

    private SrmSupplier lockSupplier(Long supplierId) {
        SrmSupplier supplier = supplierMapper.selectVisibleForUpdate(supplierId);
        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        return supplier;
    }

    private void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(400, "分页参数无效，size 必须在 1 到 100 之间");
        }
    }

    private void requireUpdated(int rows) {
        if (rows != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
    }

    private void sendRiskLevelChangedEvent(SrmRiskAssessment previous, SrmRiskAssessment current) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType("srm.risk.level-changed.v1")
                .occurredAt(LocalDateTime.now())
                .tenantId(current.getTenantId())
                .producer("omni-srm")
                .aggregateType("RISK_ASSESSMENT")
                .aggregateId(current.getId())
                .aggregateVersion(current.getVersion())
                .actorUserId(current.getAssessorUserId())
                .payload(Map.of(
                        "assessmentId", current.getId(),
                        "supplierId", current.getSupplierId(),
                        "previousLevel", previous == null ? "NONE" : previous.getOverallLevel(),
                        "overallLevel", current.getOverallLevel()))
                .build();
        reliableMessageRelay.send("srm-domain-out-0", envelope, current.getTenantId(), eventId);
    }

    /**
     * 资质风险计算结果。
     *
     * @param level 风险等级
     * @param expiryDate 代表性到期日
     * @param remark 风险说明
     */
    private record CertificateRisk(RiskLevel level, LocalDate expiryDate, String remark) {
    }
}
