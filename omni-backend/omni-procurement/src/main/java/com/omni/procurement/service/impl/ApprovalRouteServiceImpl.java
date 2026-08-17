package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.domain.ApprovalRoutePolicy;
import com.omni.procurement.domain.MaterialDomainPolicy;
import com.omni.procurement.dto.ApprovalRouteRequests;
import com.omni.procurement.dto.ApprovalRouteViews;
import com.omni.procurement.dto.ProcViewAssembler;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.ApprovalRouteService;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.support.ProcAuditSupport;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批路由配置服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class ApprovalRouteServiceImpl implements ApprovalRouteService {

    private final ProcTenantInitializer tenantInitializer;
    private final ProcApprovalRouteMapper routeMapper;
    private final ProcMaterialCategoryMapper categoryMapper;
    private final WorkflowInternalClient workflowInternalClient;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ApprovalRouteViews.RouteVO> page(ApprovalRouteRequests.RouteQuery query) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ProcTenantContext.requireTenantId();
        LambdaQueryWrapper<ProcApprovalRoute> wrapper = new LambdaQueryWrapper<ProcApprovalRoute>()
                .eq(ProcApprovalRoute::getTenantId, tenantId);
        String keyword = MaterialDomainPolicy.trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ProcApprovalRoute::getRouteCode, keyword)
                    .or().like(ProcApprovalRoute::getCategoryCode, keyword));
        }
        if (MaterialDomainPolicy.trimToNull(query.getCategoryCode()) != null) {
            wrapper.eq(ProcApprovalRoute::getCategoryCode,
                    ApprovalRoutePolicy.normalizeCategoryCode(query.getCategoryCode()));
        }
        if (MaterialDomainPolicy.trimToNull(query.getStatus()) != null) {
            wrapper.eq(ProcApprovalRoute::getStatus, ApprovalRoutePolicy.normalizeStatus(query.getStatus()));
        }
        wrapper.orderByAsc(ProcApprovalRoute::getPriority)
                .orderByAsc(ProcApprovalRoute::getCategoryCode)
                .orderByAsc(ProcApprovalRoute::getMinAmount)
                .orderByAsc(ProcApprovalRoute::getId);
        Page<ProcApprovalRoute> page = routeMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<ApprovalRouteViews.RouteVO> records = page.getRecords().stream()
                .map(ProcViewAssembler::route)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ApprovalRouteViews.RouteVO create(ApprovalRouteRequests.CreateRouteRequest request) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ProcTenantContext.requireTenantId();
        String routeCode = MaterialDomainPolicy.normalizeCode(request.getRouteCode(), "审批路由编码");
        String categoryCode = ApprovalRoutePolicy.normalizeCategoryCode(request.getCategoryCode());
        String status = ApprovalRoutePolicy.normalizeStatus(request.getStatus());
        ApprovalRoutePolicy.validateDefinition(request.getMinAmount(), request.getMaxAmount(),
                request.getModelVersionId());
        validateCategory(tenantId, categoryCode);
        validatePriority(request.getPriority());
        validateWorkflowModelVersion(tenantId, request.getModelVersionId());
        lockTenantRoutes(tenantId);
        validateRouteCodeUnique(tenantId, routeCode, null);
        validateNoActiveOverlap(tenantId,
                new ActiveRouteRange(categoryCode, request.getMinAmount(), request.getMaxAmount(), status),
                null);
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setTenantId(tenantId);
        route.setRouteCode(routeCode);
        route.setCategoryCode(categoryCode);
        route.setMinAmount(request.getMinAmount());
        route.setMaxAmount(request.getMaxAmount());
        route.setModelVersionId(request.getModelVersionId());
        route.setPriority(request.getPriority());
        route.setStatus(status);
        route.setVersion(0);
        route.setDeleted(0);
        ProcAuditSupport.created(route);
        try {
            routeMapper.insert(route);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "审批路由编码或金额区间冲突");
        }
        return ProcViewAssembler.route(route);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ApprovalRouteViews.RouteVO update(Long id, ApprovalRouteRequests.UpdateRouteRequest request) {
        Long tenantId = ProcTenantContext.requireTenantId();
        String categoryCode = ApprovalRoutePolicy.normalizeCategoryCode(request.getCategoryCode());
        String status = ApprovalRoutePolicy.normalizeStatus(request.getStatus());
        ApprovalRoutePolicy.validateDefinition(request.getMinAmount(), request.getMaxAmount(),
                request.getModelVersionId());
        validateCategory(tenantId, categoryCode);
        validatePriority(request.getPriority());
        validateWorkflowModelVersion(tenantId, request.getModelVersionId());
        lockTenantRoutes(tenantId);
        ProcApprovalRoute current = requireRoute(tenantId, id);
        validateRouteCodeUnique(tenantId, current.getRouteCode(), id);
        validateNoActiveOverlap(tenantId,
                new ActiveRouteRange(categoryCode, request.getMinAmount(), request.getMaxAmount(), status),
                id);
        LambdaUpdateWrapper<ProcApprovalRoute> update = versioned(tenantId, id, request.getVersion())
                .set(ProcApprovalRoute::getCategoryCode, categoryCode)
                .set(ProcApprovalRoute::getMinAmount, request.getMinAmount())
                .set(ProcApprovalRoute::getMaxAmount, request.getMaxAmount())
                .set(ProcApprovalRoute::getModelVersionId, request.getModelVersionId())
                .set(ProcApprovalRoute::getPriority, request.getPriority())
                .set(ProcApprovalRoute::getStatus, status);
        audit(update);
        try {
            requireUpdated(routeMapper.update(null, update));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "审批路由编码或金额区间冲突");
        }
        return ProcViewAssembler.route(requireRoute(tenantId, id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        Long tenantId = ProcTenantContext.requireTenantId();
        lockTenantRoutes(tenantId);
        requireRoute(tenantId, id);
        LambdaUpdateWrapper<ProcApprovalRoute> update = versioned(tenantId, id, version)
                .set(ProcApprovalRoute::getDeleted, 1);
        audit(update);
        requireUpdated(routeMapper.update(null, update));
    }

    private void validateCategory(Long tenantId, String categoryCode) {
        if (ApprovalRoutePolicy.WILDCARD_CATEGORY.equals(categoryCode)) {
            return;
        }
        ProcMaterialCategory category = categoryMapper.selectOne(
                new LambdaQueryWrapper<ProcMaterialCategory>()
                        .eq(ProcMaterialCategory::getTenantId, tenantId)
                        .eq(ProcMaterialCategory::getCategoryCode, categoryCode));
        MaterialDomainPolicy.requireActiveCategory(category);
    }

    private void lockTenantRoutes(Long tenantId) {
        if (routeMapper.lockTenantConfig(tenantId) == null) {
            throw new BusinessException(500, "采购租户配置未初始化");
        }
    }

    private void validateWorkflowModelVersion(Long tenantId, Long modelVersionId) {
        R<WorkflowContracts.ModelVersionResponse> response;
        try {
            response = workflowInternalClient.getModelVersion(tenantId, modelVersionId);
        } catch (FeignException.NotFound exception) {
            throw new BusinessException(400, "Workflow 模型版本不存在、未发布或不属于当前租户");
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 模型版本校验服务暂时不可用");
        }
        if (response != null && (response.getCode() == 400 || response.getCode() == 404)) {
            throw new BusinessException(400, "Workflow 模型版本不存在、未发布或不属于当前租户");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "Workflow 返回了无效的模型版本校验响应");
        }
        WorkflowContracts.ModelVersionResponse modelVersion = response.getData();
        if (!modelVersionId.equals(modelVersion.getId())
                || !"PUBLISHED".equals(modelVersion.getStatus())
                || modelVersion.getProcessDefinitionId() == null
                || modelVersion.getProcessDefinitionId().isBlank()) {
            throw new BusinessException(400, "Workflow 模型版本不可用于审批路由");
        }
    }

    private void validateRouteCodeUnique(Long tenantId, String routeCode, Long excludeId) {
        LambdaQueryWrapper<ProcApprovalRoute> wrapper = new LambdaQueryWrapper<ProcApprovalRoute>()
                .eq(ProcApprovalRoute::getTenantId, tenantId)
                .eq(ProcApprovalRoute::getRouteCode, routeCode);
        if (excludeId != null) {
            wrapper.ne(ProcApprovalRoute::getId, excludeId);
        }
        if (routeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "审批路由编码已存在");
        }
    }

    private void validatePriority(Integer priority) {
        if (priority == null || priority < 0) {
            throw new BusinessException(400, "审批路由优先级不能小于 0");
        }
    }

    private void validateNoActiveOverlap(Long tenantId, ActiveRouteRange range, Long excludeId) {
        if (!ApprovalRoutePolicy.ACTIVE.equals(range.status())) {
            return;
        }
        LambdaQueryWrapper<ProcApprovalRoute> wrapper = new LambdaQueryWrapper<ProcApprovalRoute>()
                .eq(ProcApprovalRoute::getTenantId, tenantId)
                .eq(ProcApprovalRoute::getCategoryCode, range.categoryCode())
                .eq(ProcApprovalRoute::getStatus, ApprovalRoutePolicy.ACTIVE);
        if (excludeId != null) {
            wrapper.ne(ProcApprovalRoute::getId, excludeId);
        }
        boolean conflict = routeMapper.selectList(wrapper).stream()
                .anyMatch(existing -> ApprovalRoutePolicy.overlaps(
                        range.minAmount(), range.maxAmount(),
                        existing.getMinAmount(), existing.getMaxAmount()));
        if (conflict) {
            throw new BusinessException(409, "同品类活动审批路由金额区间不能重叠");
        }
    }

    private ProcApprovalRoute requireRoute(Long tenantId, Long id) {
        ProcApprovalRoute route = routeMapper.selectOne(new LambdaQueryWrapper<ProcApprovalRoute>()
                .eq(ProcApprovalRoute::getTenantId, tenantId)
                .eq(ProcApprovalRoute::getId, id));
        if (route == null) {
            throw new BusinessException(404, "审批路由不存在");
        }
        return route;
    }

    private LambdaUpdateWrapper<ProcApprovalRoute> versioned(Long tenantId, Long id, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        return new LambdaUpdateWrapper<ProcApprovalRoute>()
                .eq(ProcApprovalRoute::getTenantId, tenantId)
                .eq(ProcApprovalRoute::getId, id)
                .eq(ProcApprovalRoute::getVersion, version)
                .eq(ProcApprovalRoute::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<ProcApprovalRoute> update) {
        update.set(ProcApprovalRoute::getUpdateTime, LocalDateTime.now())
                .set(ProcApprovalRoute::getUpdateBy, ProcTenantContext.require().username());
    }

    private void requireUpdated(int affected) {
        if (affected != 1) {
            throw new BusinessException(409, "审批路由已被其他请求修改");
        }
    }

    /**
     * 活动审批路由金额区间快照。
     */
    private record ActiveRouteRange(
            String categoryCode,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String status) {
    }
}
