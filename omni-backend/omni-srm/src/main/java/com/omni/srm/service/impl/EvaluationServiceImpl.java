package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.srm.domain.SrmEvaluationPolicy;
import com.omni.srm.domain.SrmEvaluationScoreCalculator;
import com.omni.srm.domain.SrmEvaluationScoreCalculator.WeightedScore;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmEvaluation;
import com.omni.srm.entity.SrmEvaluationDimension;
import com.omni.srm.entity.SrmEvaluationItem;
import com.omni.srm.entity.SrmEvaluationTemplate;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmEvaluationDimensionMapper;
import com.omni.srm.mapper.SrmEvaluationItemMapper;
import com.omni.srm.mapper.SrmEvaluationMapper;
import com.omni.srm.mapper.SrmEvaluationTemplateMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.EvaluationService;
import com.omni.srm.service.SrmTenantInitializer;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** SRM 绩效评估服务实现。 */
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final SrmEvaluationMapper evaluationMapper;
    private final SrmEvaluationItemMapper evaluationItemMapper;
    private final SrmEvaluationTemplateMapper templateMapper;
    private final SrmEvaluationDimensionMapper dimensionMapper;
    private final SrmSupplierMapper supplierMapper;
    private final SrmRecordAccessGuard recordAccessGuard;
    private final ReliableMessageRelay reliableMessageRelay;
    private final SrmTenantInitializer tenantInitializer;

    /** {@inheritDoc} */
    @Override
    public PageResult<SrmViews.EvaluationVO> list(Long supplierId, int page, int size) {
        requirePage(page, size);
        LambdaQueryWrapper<SrmEvaluation> wrapper = new LambdaQueryWrapper<SrmEvaluation>()
                .eq(supplierId != null, SrmEvaluation::getSupplierId, supplierId)
                .orderByDesc(SrmEvaluation::getEvaluationTime);
        Page<SrmEvaluation> result = evaluationMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, String> supplierNames = loadSupplierNames(result.getRecords());
        List<SrmViews.EvaluationVO> records = result.getRecords().stream()
                .map(evaluation -> toVO(evaluation, supplierNames.get(evaluation.getSupplierId())))
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.EvaluationVO get(Long id) {
        SrmEvaluation evaluation = recordAccessGuard.requireEvaluation(id);
        return toVOWithItems(evaluation);
    }

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.EvaluationVO> supplierHistory(Long supplierId) {
        recordAccessGuard.requireSupplier(supplierId);
        List<SrmEvaluation> evaluations = evaluationMapper.selectList(new LambdaQueryWrapper<SrmEvaluation>()
                .eq(SrmEvaluation::getSupplierId, supplierId)
                .orderByDesc(SrmEvaluation::getEvaluationTime)
                .last("LIMIT 100"));
        if (evaluations.isEmpty()) {
            return List.of();
        }
        List<Long> evaluationIds = evaluations.stream().map(SrmEvaluation::getId).toList();
        Map<Long, List<SrmEvaluationItem>> itemsByEvaluation = evaluationItemMapper.selectList(
                        new LambdaQueryWrapper<SrmEvaluationItem>()
                                .in(SrmEvaluationItem::getEvaluationId, evaluationIds)
                                .orderByAsc(SrmEvaluationItem::getId)).stream()
                .collect(Collectors.groupingBy(SrmEvaluationItem::getEvaluationId));
        String supplierName = recordAccessGuard.requireSupplier(supplierId).getName();
        return evaluations.stream().map(evaluation -> {
            SrmViews.EvaluationVO vo = toVO(evaluation, supplierName);
            vo.setItems(itemsByEvaluation.getOrDefault(evaluation.getId(), List.of()).stream()
                    .map(SrmViewAssembler::evaluationItem).toList());
            return vo;
        }).toList();
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.EvaluationTemplateVO defaultTemplate() {
        SrmEvaluationTemplate template = requireDefaultTemplate();
        List<SrmEvaluationDimension> dimensions = requireDimensions(template.getId());
        SrmViews.EvaluationTemplateVO vo = new SrmViews.EvaluationTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setVersion(template.getVersion());
        vo.setDimensions(dimensions.stream().map(dimension -> {
            SrmViews.EvaluationDimensionVO dimensionVO = new SrmViews.EvaluationDimensionVO();
            dimensionVO.setId(dimension.getId());
            dimensionVO.setIndicatorName(dimension.getIndicatorName());
            dimensionVO.setWeight(dimension.getWeight());
            dimensionVO.setSort(dimension.getSort());
            return dimensionVO;
        }).toList());
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.EvaluationVO create(SrmRequests.CreateEvaluationRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        Long userId = ServiceIdentityContext.require().userId();

        SrmSupplier supplier = supplierMapper.selectVisibleForUpdate(request.getSupplierId());
        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        SrmEvaluationPolicy.requireEligible(
                supplier.getStatus(), supplier.getOwnerUserId(), supplier.getOwnerUnitId());
        SrmEvaluationTemplate template = requireDefaultTemplate();
        List<SrmEvaluationDimension> dimensions = requireDimensions(template.getId());
        Map<Long, SrmRequests.EvaluationItemInput> inputMap = validateItems(request.getItems(), dimensions);
        List<WeightedScore> weightedScores = dimensions.stream()
                .map(dimension -> new WeightedScore(
                        inputMap.get(dimension.getId()).getScore(), dimension.getWeight()))
                .toList();
        BigDecimal totalScore = SrmEvaluationScoreCalculator.calculate(weightedScores);

        // 创建评估主记录
        SrmEvaluation evaluation = new SrmEvaluation();
        evaluation.setTenantId(tenantId);
        evaluation.setSupplierId(request.getSupplierId());
        evaluation.setTemplateId(template.getId());
        evaluation.setEvaluationPeriod(request.getEvaluationPeriod());
        evaluation.setTotalScore(totalScore);
        evaluation.setEvaluatorUserId(userId);
        evaluation.setEvaluationTime(LocalDateTime.now());
        evaluation.setStatus("COMPLETED");
        evaluation.setOwnerUserId(supplier.getOwnerUserId());
        evaluation.setOwnerUnitId(supplier.getOwnerUnitId());
        evaluation.setVersion(0);
        evaluation.setDeleted(0);
        SrmAuditSupport.created(evaluation);
        evaluationMapper.insert(evaluation);

        List<SrmEvaluationItem> items = new ArrayList<>();
        for (SrmEvaluationDimension dim : dimensions) {
            SrmRequests.EvaluationItemInput input = inputMap.get(dim.getId());
            SrmEvaluationItem item = new SrmEvaluationItem();
            item.setTenantId(tenantId);
            item.setEvaluationId(evaluation.getId());
            item.setDimensionId(dim.getId());
            item.setIndicatorName(dim.getIndicatorName());
            item.setScore(input.getScore());
            item.setWeight(dim.getWeight());
            item.setRemark(input.getRemark());
            item.setVersion(0);
            item.setDeleted(0);
            SrmAuditSupport.created(item);
            evaluationItemMapper.insert(item);
            items.add(item);
        }

        String levelCode = SrmEvaluationScoreCalculator.mapLevel(totalScore);

        // 更新供应商等级 + 最近评估时间
        int updated = supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getId, supplier.getId())
                .eq(SrmSupplier::getVersion, supplier.getVersion())
                .eq(SrmSupplier::getDeleted, 0)
                .set(SrmSupplier::getLevelCode, levelCode)
                .set(SrmSupplier::getLastEvaluationTime, evaluation.getEvaluationTime())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(409, "供应商已被其他用户修改，请刷新后重试");
        }

        // 发送 Outbox 事件
        sendEvaluationCompletedEvent(evaluation, supplier, levelCode);

        // 组装返回
        SrmViews.EvaluationVO vo = SrmViewAssembler.evaluation(evaluation);
        vo.setSupplierName(supplier.getName());
        vo.setItems(items.stream().map(SrmViewAssembler::evaluationItem).toList());
        return vo;
    }

    private SrmViews.EvaluationVO toVO(SrmEvaluation evaluation, String supplierName) {
        SrmViews.EvaluationVO vo = SrmViewAssembler.evaluation(evaluation);
        vo.setSupplierName(supplierName);
        return vo;
    }

    private SrmViews.EvaluationVO toVOWithItems(SrmEvaluation evaluation) {
        SrmSupplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<SrmSupplier>()
                .eq(SrmSupplier::getId, evaluation.getSupplierId()));
        SrmViews.EvaluationVO vo = toVO(evaluation, supplier == null ? null : supplier.getName());
        List<SrmEvaluationItem> items = evaluationItemMapper.selectList(
                new LambdaQueryWrapper<SrmEvaluationItem>()
                        .eq(SrmEvaluationItem::getEvaluationId, evaluation.getId())
                        .orderByAsc(SrmEvaluationItem::getId));
        vo.setItems(items.stream().map(SrmViewAssembler::evaluationItem).toList());
        return vo;
    }

    private SrmEvaluationTemplate requireDefaultTemplate() {
        tenantInitializer.ensureInitialized();
        SrmEvaluationTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<SrmEvaluationTemplate>()
                .eq(SrmEvaluationTemplate::getStatus, 1)
                .eq(SrmEvaluationTemplate::getDefaultFlag, true)
                .orderByAsc(SrmEvaluationTemplate::getId)
                .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException(400, "当前租户未配置默认评估模板");
        }
        return template;
    }

    private List<SrmEvaluationDimension> requireDimensions(Long templateId) {
        List<SrmEvaluationDimension> dimensions = dimensionMapper.selectList(
                new LambdaQueryWrapper<SrmEvaluationDimension>()
                        .eq(SrmEvaluationDimension::getTemplateId, templateId)
                        .eq(SrmEvaluationDimension::getStatus, 1)
                        .orderByAsc(SrmEvaluationDimension::getSort));
        if (dimensions.isEmpty()) {
            throw new BusinessException(400, "评估模板无可用维度配置");
        }
        SrmEvaluationScoreCalculator.requireWeightTotal(
                dimensions.stream().map(SrmEvaluationDimension::getWeight).toList());
        return dimensions;
    }

    private Map<Long, SrmRequests.EvaluationItemInput> validateItems(
            List<SrmRequests.EvaluationItemInput> inputs, List<SrmEvaluationDimension> dimensions) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException(400, "评分明细不能为空");
        }
        Set<Long> expected = dimensions.stream().map(SrmEvaluationDimension::getId).collect(Collectors.toSet());
        Map<Long, SrmRequests.EvaluationItemInput> result = new HashMap<>();
        for (SrmRequests.EvaluationItemInput input : inputs) {
            if (input == null || input.getDimensionId() == null || input.getScore() == null) {
                throw new BusinessException(400, "评分维度和分数不能为空");
            }
            if (!expected.contains(input.getDimensionId())) {
                throw new BusinessException(400, "评分包含不属于默认模板的维度：" + input.getDimensionId());
            }
            if (result.putIfAbsent(input.getDimensionId(), input) != null) {
                throw new BusinessException(400, "同一评估维度不能重复评分：" + input.getDimensionId());
            }
        }
        if (result.size() != dimensions.size()) {
            throw new BusinessException(400, "评分必须完整覆盖默认模板的全部维度");
        }
        return result;
    }

    private Map<Long, String> loadSupplierNames(List<SrmEvaluation> evaluations) {
        Set<Long> supplierIds = evaluations.stream().map(SrmEvaluation::getSupplierId)
                .collect(Collectors.toCollection(HashSet::new));
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectByIds(supplierIds).stream()
                .collect(Collectors.toMap(SrmSupplier::getId, SrmSupplier::getName));
    }

    private void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(400, "分页参数无效，size 必须在 1 到 100 之间");
        }
    }

    private void sendEvaluationCompletedEvent(SrmEvaluation evaluation, SrmSupplier supplier, String levelCode) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType("srm.evaluation.completed.v1")
                .occurredAt(LocalDateTime.now())
                .tenantId(ServiceIdentityContext.requireTenantId())
                .producer("omni-srm")
                .aggregateType("EVALUATION")
                .aggregateId(evaluation.getId())
                .aggregateVersion(evaluation.getVersion())
                .actorUserId(evaluation.getEvaluatorUserId())
                .payload(Map.of(
                        "evaluationId", evaluation.getId(),
                        "supplierId", supplier.getId(),
                        "totalScore", evaluation.getTotalScore(),
                        "levelCode", levelCode,
                        "evaluationPeriod", evaluation.getEvaluationPeriod()))
                .build();
        reliableMessageRelay.send("srm-domain-out-0", envelope,
                ServiceIdentityContext.requireTenantId(), eventId);
    }
}
