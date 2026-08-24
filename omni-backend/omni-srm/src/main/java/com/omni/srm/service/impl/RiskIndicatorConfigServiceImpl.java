package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmRiskCriterion;
import com.omni.srm.entity.SrmRiskIndicatorType;
import com.omni.srm.entity.SrmRiskScoreThreshold;
import com.omni.srm.mapper.SrmRiskCriterionMapper;
import com.omni.srm.mapper.SrmRiskIndicatorTypeMapper;
import com.omni.srm.mapper.SrmRiskScoreThresholdMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.RiskIndicatorConfigService;
import com.omni.srm.service.support.SrmAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SRM 风险指标配置服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class RiskIndicatorConfigServiceImpl implements RiskIndicatorConfigService {

    private final SrmRiskIndicatorTypeMapper indicatorTypeMapper;
    private final SrmRiskCriterionMapper criterionMapper;
    private final SrmRiskScoreThresholdMapper thresholdMapper;

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.RiskIndicatorTypeVO> listIndicatorTypes() {
        List<SrmRiskIndicatorType> types = indicatorTypeMapper.selectList(
                new LambdaQueryWrapper<SrmRiskIndicatorType>()
                        .eq(SrmRiskIndicatorType::getStatus, 1)
                        .orderByAsc(SrmRiskIndicatorType::getSort)
                        .orderByAsc(SrmRiskIndicatorType::getId));
        return types.stream().map(this::toTypeVO).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskIndicatorTypeVO createIndicatorType(SrmViews.RiskIndicatorTypeVO request) {
        if (request.getTypeCode() == null || request.getTypeCode().isBlank()) {
            throw new BusinessException(400, "指标编码不能为空");
        }
        if (request.getTypeName() == null || request.getTypeName().isBlank()) {
            throw new BusinessException(400, "指标名称不能为空");
        }
        // 检查编码唯一性
        Long exists = indicatorTypeMapper.selectCount(
                new LambdaQueryWrapper<SrmRiskIndicatorType>()
                        .eq(SrmRiskIndicatorType::getTypeCode, request.getTypeCode().toUpperCase()));
        if (exists > 0) {
            throw new BusinessException(400, "指标编码已存在：" + request.getTypeCode());
        }

        SrmRiskIndicatorType entity = new SrmRiskIndicatorType();
        entity.setTenantId(ServiceIdentityContext.requireTenantId());
        entity.setTypeCode(request.getTypeCode().toUpperCase());
        entity.setTypeName(request.getTypeName());
        entity.setDescription(request.getDescription());
        entity.setSort(request.getSort() != null ? request.getSort() : 0);
        entity.setAutoCalc(request.getAutoCalc() != null ? request.getAutoCalc() : 0);
        entity.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        entity.setDeleted(0);
        SrmAuditSupport.created(entity);
        indicatorTypeMapper.insert(entity);
        return toTypeVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskIndicatorTypeVO updateIndicatorType(Long id, SrmViews.RiskIndicatorTypeVO request) {
        SrmRiskIndicatorType entity = requireIndicatorType(id);
        if (request.getTypeName() != null && !request.getTypeName().isBlank()) {
            entity.setTypeName(request.getTypeName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        SrmAuditSupport.updated(entity);
        indicatorTypeMapper.updateById(entity);
        return toTypeVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteIndicatorType(Long id) {
        SrmRiskIndicatorType entity = requireIndicatorType(id);
        // 检查是否有关联评分标准
        Long criterionCount = criterionMapper.selectCount(
                new LambdaQueryWrapper<SrmRiskCriterion>()
                        .eq(SrmRiskCriterion::getIndicatorTypeId, id));
        if (criterionCount > 0) {
            throw new BusinessException(400, "该指标类型下存在评分标准，请先删除评分标准");
        }
        indicatorTypeMapper.deleteById(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskCriterionVO createCriterion(SrmViews.RiskCriterionVO request) {
        if (request.getIndicatorTypeId() == null) {
            throw new BusinessException(400, "指标类型 ID 不能为空");
        }
        requireIndicatorType(request.getIndicatorTypeId());
        if (request.getCriterionLabel() == null || request.getCriterionLabel().isBlank()) {
            throw new BusinessException(400, "评分标准描述不能为空");
        }
        if (request.getScore() == null) {
            throw new BusinessException(400, "分值不能为空");
        }
        if (request.getRiskLevel() == null || request.getRiskLevel().isBlank()) {
            throw new BusinessException(400, "风险等级不能为空");
        }

        SrmRiskCriterion entity = new SrmRiskCriterion();
        entity.setTenantId(ServiceIdentityContext.requireTenantId());
        entity.setIndicatorTypeId(request.getIndicatorTypeId());
        entity.setCriterionLabel(request.getCriterionLabel());
        entity.setScore(request.getScore());
        entity.setRiskLevel(request.getRiskLevel().toUpperCase());
        entity.setSort(request.getSort() != null ? request.getSort() : 0);
        entity.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        entity.setDeleted(0);
        SrmAuditSupport.created(entity);
        criterionMapper.insert(entity);
        return toCriterionVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.RiskCriterionVO updateCriterion(Long id, SrmViews.RiskCriterionVO request) {
        SrmRiskCriterion entity = requireCriterion(id);
        if (request.getCriterionLabel() != null && !request.getCriterionLabel().isBlank()) {
            entity.setCriterionLabel(request.getCriterionLabel());
        }
        if (request.getScore() != null) {
            entity.setScore(request.getScore());
        }
        if (request.getRiskLevel() != null && !request.getRiskLevel().isBlank()) {
            entity.setRiskLevel(request.getRiskLevel().toUpperCase());
        }
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        SrmAuditSupport.updated(entity);
        criterionMapper.updateById(entity);
        return toCriterionVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteCriterion(Long id) {
        requireCriterion(id);
        criterionMapper.deleteById(id);
    }

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.RiskScoreThresholdVO> listThresholds() {
        return thresholdMapper.selectList(
                new LambdaQueryWrapper<SrmRiskScoreThreshold>()
                        .orderByAsc(SrmRiskScoreThreshold::getMinScore))
                .stream().map(this::toThresholdVO).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<SrmViews.RiskScoreThresholdVO> updateThresholds(List<SrmViews.RiskScoreThresholdVO> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(400, "阈值列表不能为空");
        }
        Long tenantId = ServiceIdentityContext.requireTenantId();
        // 删除现有阈值并重新插入
        thresholdMapper.delete(new LambdaQueryWrapper<SrmRiskScoreThreshold>()
                .eq(SrmRiskScoreThreshold::getTenantId, tenantId));
        for (SrmViews.RiskScoreThresholdVO vo : requests) {
            SrmRiskScoreThreshold entity = new SrmRiskScoreThreshold();
            entity.setTenantId(tenantId);
            entity.setRiskLevel(vo.getRiskLevel().toUpperCase());
            entity.setMinScore(vo.getMinScore());
            entity.setMaxScore(vo.getMaxScore());
            entity.setDeleted(0);
            SrmAuditSupport.created(entity);
            thresholdMapper.insert(entity);
        }
        return listThresholds();
    }

    // ── 私有方法 ────────────────────────────────────────

    private SrmRiskIndicatorType requireIndicatorType(Long id) {
        SrmRiskIndicatorType entity = indicatorTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "指标类型不存在");
        }
        return entity;
    }

    private SrmRiskCriterion requireCriterion(Long id) {
        SrmRiskCriterion entity = criterionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "评分标准不存在");
        }
        return entity;
    }

    private SrmViews.RiskIndicatorTypeVO toTypeVO(SrmRiskIndicatorType entity) {
        SrmViews.RiskIndicatorTypeVO vo = new SrmViews.RiskIndicatorTypeVO();
        vo.setId(entity.getId());
        vo.setTypeCode(entity.getTypeCode());
        vo.setTypeName(entity.getTypeName());
        vo.setDescription(entity.getDescription());
        vo.setSort(entity.getSort());
        vo.setAutoCalc(entity.getAutoCalc());
        vo.setStatus(entity.getStatus());
        // 加载关联评分标准
        List<SrmRiskCriterion> criteria = criterionMapper.selectList(
                new LambdaQueryWrapper<SrmRiskCriterion>()
                        .eq(SrmRiskCriterion::getIndicatorTypeId, entity.getId())
                        .eq(SrmRiskCriterion::getStatus, 1)
                        .orderByAsc(SrmRiskCriterion::getSort)
                        .orderByAsc(SrmRiskCriterion::getId));
        vo.setCriteria(criteria.stream().map(this::toCriterionVO).toList());
        return vo;
    }

    private SrmViews.RiskCriterionVO toCriterionVO(SrmRiskCriterion entity) {
        SrmViews.RiskCriterionVO vo = new SrmViews.RiskCriterionVO();
        vo.setId(entity.getId());
        vo.setIndicatorTypeId(entity.getIndicatorTypeId());
        vo.setCriterionLabel(entity.getCriterionLabel());
        vo.setScore(entity.getScore());
        vo.setRiskLevel(entity.getRiskLevel());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private SrmViews.RiskScoreThresholdVO toThresholdVO(SrmRiskScoreThreshold entity) {
        SrmViews.RiskScoreThresholdVO vo = new SrmViews.RiskScoreThresholdVO();
        vo.setId(entity.getId());
        vo.setRiskLevel(entity.getRiskLevel());
        vo.setMinScore(entity.getMinScore());
        vo.setMaxScore(entity.getMaxScore());
        return vo;
    }
}
