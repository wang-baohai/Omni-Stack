package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.srm.domain.SrmRiskCalculator;
import com.omni.srm.domain.SrmRiskCalculator.RiskLevel;
import com.omni.srm.domain.SrmRiskCalculator.ScoreThreshold;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmRiskAssessment;
import com.omni.srm.entity.SrmRiskCriterion;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmRiskIndicatorType;
import com.omni.srm.entity.SrmRiskScoreThreshold;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.mapper.SrmRiskAssessmentMapper;
import com.omni.srm.mapper.SrmRiskCriterionMapper;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import com.omni.srm.mapper.SrmRiskIndicatorTypeMapper;
import com.omni.srm.mapper.SrmRiskScoreThresholdMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
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
    private final SrmRiskIndicatorTypeMapper indicatorTypeMapper;
    private final SrmRiskCriterionMapper criterionMapper;
    private final SrmRiskScoreThresholdMapper thresholdMapper;
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
        vo.setIndicators(indicators.stream().map(this::assembleIndicatorVO).toList());
        vo.setLatestAssessment(history.isEmpty() ? null : SrmViewAssembler.riskAssessment(history.getFirst()));
        vo.setHistory(history.stream().map(SrmViewAssembler::riskAssessment).toList());
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.RiskIndicatorVO> listIndicators(Long supplierId) {
        recordAccessGuard.requireSupplier(supplierId);
        return findIndicators(supplierId).stream().map(this::assembleIndicatorVO).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskIndicatorVO updateIndicator(
            Long id, SrmRequests.UpdateRiskIndicatorRequest request) {
        SrmRiskIndicator indicator = recordAccessGuard.requireRiskIndicator(id);

        // 检查 autoCalc 标记（从指标类型表读取）
        SrmRiskIndicatorType indicatorType = indicatorTypeMapper.selectOne(
                new LambdaQueryWrapper<SrmRiskIndicatorType>()
                        .eq(SrmRiskIndicatorType::getTypeCode, indicator.getIndicatorType())
                        .eq(SrmRiskIndicatorType::getStatus, 1)
                        .last("LIMIT 1"));
        if (indicatorType != null) {
            SrmRiskCalculator.requireManuallyEditable(indicatorType.getAutoCalc());
        }

        lockSupplier(indicator.getSupplierId());

        LambdaUpdateWrapper<SrmRiskIndicator> update = new LambdaUpdateWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getId, id)
                .eq(SrmRiskIndicator::getVersion, request.getVersion())
                .eq(SrmRiskIndicator::getDeleted, 0)
                .setSql("version = version + 1");

        // 如果传了 criterionId，从评分标准表读取分数和等级
        if (request.getCriterionId() != null) {
            SrmRiskCriterion criterion = criterionMapper.selectById(request.getCriterionId());
            if (criterion == null) {
                throw new BusinessException(400, "评分标准不存在");
            }
            update.set(SrmRiskIndicator::getCriterionId, request.getCriterionId());
            update.set(SrmRiskIndicator::getScore, criterion.getScore());
            update.set(SrmRiskIndicator::getRiskLevel, criterion.getRiskLevel());
            update.set(SrmRiskIndicator::getIndicatorValue, criterion.getCriterionLabel());
        } else {
            // 向后兼容：旧接口仍然支持直接传值
            if (request.getIndicatorValue() != null) {
                update.set(SrmRiskIndicator::getIndicatorValue, request.getIndicatorValue());
            }
            if (request.getRiskLevel() != null) {
                SrmRiskCalculator.parseLevel(request.getRiskLevel());
                update.set(SrmRiskIndicator::getRiskLevel, request.getRiskLevel());
            }
        }

        if (request.getRemark() != null) {
            update.set(SrmRiskIndicator::getRemark, request.getRemark());
        }
        update.set(SrmRiskIndicator::getAssessmentTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getUpdateTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getUpdateBy, ServiceIdentityContext.require().username());
        requireUpdated(riskIndicatorMapper.update(null, update));

        createAssessmentInternal(indicator.getSupplierId(), "风险指标更新后自动重算");
        return assembleIndicatorVO(recordAccessGuard.requireRiskIndicator(id));
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
        Long tenantId = ServiceIdentityContext.requireTenantId();
        riskInitializer.initialize(tenantId, supplierId);
        refreshCertificateIndicator(supplierId);
        List<SrmRiskIndicator> indicators = findIndicators(supplierId);

        // 基于总分 + 阈值表计算综合风险等级
        int totalScore = indicators.stream()
                .filter(i -> i.getScore() != null)
                .mapToInt(SrmRiskIndicator::getScore)
                .sum();
        List<ScoreThreshold> thresholds = loadThresholds(tenantId);
        RiskLevel overallLevel = SrmRiskCalculator.computeFromScore(totalScore, thresholds);

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
        assessment.setAssessorUserId(ServiceIdentityContext.require().userId());
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

        // 查找CERTIFICATE类型对应的评分标准（按风险等级匹配）
        Long criterionId = findCertificateCriterionId(certificateRisk.level());
        int score = certificateRisk.level() == RiskLevel.RED ? 3
                : certificateRisk.level() == RiskLevel.YELLOW ? 2 : 1;

        LambdaUpdateWrapper<SrmRiskIndicator> update = new LambdaUpdateWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getId, indicator.getId())
                .eq(SrmRiskIndicator::getVersion, indicator.getVersion())
                .eq(SrmRiskIndicator::getDeleted, 0)
                .set(SrmRiskIndicator::getRiskLevel, certificateRisk.level().name())
                .set(SrmRiskIndicator::getScore, score)
                .set(SrmRiskIndicator::getCriterionId, criterionId)
                .set(SrmRiskIndicator::getIndicatorValue,
                        certificateRisk.expiryDate() == null ? null : certificateRisk.expiryDate().toString())
                .set(SrmRiskIndicator::getAssessmentTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getRemark, certificateRisk.remark())
                .set(SrmRiskIndicator::getUpdateTime, LocalDateTime.now())
                .set(SrmRiskIndicator::getUpdateBy, ServiceIdentityContext.require().username())
                .setSql("version = version + 1");
        requireUpdated(riskIndicatorMapper.update(null, update));
    }

    private Long findCertificateCriterionId(RiskLevel level) {
        SrmRiskIndicatorType certType = indicatorTypeMapper.selectOne(
                new LambdaQueryWrapper<SrmRiskIndicatorType>()
                        .eq(SrmRiskIndicatorType::getTypeCode, CERTIFICATE)
                        .last("LIMIT 1"));
        if (certType == null) {
            return null;
        }
        SrmRiskCriterion criterion = criterionMapper.selectOne(
                new LambdaQueryWrapper<SrmRiskCriterion>()
                        .eq(SrmRiskCriterion::getIndicatorTypeId, certType.getId())
                        .eq(SrmRiskCriterion::getRiskLevel, level.name())
                        .last("LIMIT 1"));
        return criterion != null ? criterion.getId() : null;
    }

    private List<ScoreThreshold> loadThresholds(Long tenantId) {
        return thresholdMapper.selectList(
                new LambdaQueryWrapper<SrmRiskScoreThreshold>()
                        .eq(SrmRiskScoreThreshold::getTenantId, tenantId))
                .stream()
                .map(t -> new ScoreThreshold(t.getRiskLevel(), t.getMinScore(), t.getMaxScore()))
                .toList();
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

    /** 装配风险指标 VO，包含指标类型名称和可选评分标准。 */
    private SrmViews.RiskIndicatorVO assembleIndicatorVO(SrmRiskIndicator entity) {
        SrmViews.RiskIndicatorVO vo = SrmViewAssembler.riskIndicator(entity);
        // 填充指标类型名称和 autoCalc 标记
        SrmRiskIndicatorType type = indicatorTypeMapper.selectOne(
                new LambdaQueryWrapper<SrmRiskIndicatorType>()
                        .eq(SrmRiskIndicatorType::getTypeCode, entity.getIndicatorType())
                        .last("LIMIT 1"));
        if (type != null) {
            vo.setIndicatorTypeName(type.getTypeName());
            vo.setAutoCalc(type.getAutoCalc());
        }
        // 加载该指标类型下的可选评分标准
        if (type != null) {
            List<SrmRiskCriterion> criteria = criterionMapper.selectList(
                    new LambdaQueryWrapper<SrmRiskCriterion>()
                            .eq(SrmRiskCriterion::getIndicatorTypeId, type.getId())
                            .eq(SrmRiskCriterion::getStatus, 1)
                            .orderByAsc(SrmRiskCriterion::getSort));
            vo.setCriteria(criteria.stream().map(c -> {
                SrmViews.RiskCriterionVO cv = new SrmViews.RiskCriterionVO();
                cv.setId(c.getId());
                cv.setIndicatorTypeId(c.getIndicatorTypeId());
                cv.setCriterionLabel(c.getCriterionLabel());
                cv.setScore(c.getScore());
                cv.setRiskLevel(c.getRiskLevel());
                cv.setSort(c.getSort());
                cv.setStatus(c.getStatus());
                return cv;
            }).toList());
        }
        return vo;
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
