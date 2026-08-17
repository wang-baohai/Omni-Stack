package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.InternalSupplierSummary;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.InternalSupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 服务间供应商只读查询服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class InternalSupplierServiceImpl implements InternalSupplierService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_BATCH_SIZE = 100;

    private final SrmSupplierMapper supplierMapper;

    /** {@inheritDoc} */
    @Override
    public InternalSupplierSummary get(Long tenantId, Long supplierId) {
        return runInTenant(tenantId, () -> {
            SrmSupplier supplier = supplierMapper.selectOne(Wrappers.<SrmSupplier>lambdaQuery()
                    .eq(SrmSupplier::getId, supplierId)
                    .eq(SrmSupplier::getTenantId, tenantId)
                    .eq(SrmSupplier::getDeleted, 0));
            if (supplier == null) {
                throw new BusinessException(404, "供应商不存在");
            }
            return toSummary(supplier);
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<InternalSupplierSummary> search(Long tenantId, String status,
                                                String categoryCode, int limit) {
        return searchOptions(tenantId, status, categoryCode, null, limit);
    }

    /** {@inheritDoc} */
    @Override
    public List<InternalSupplierSummary> searchOptions(Long tenantId, String status,
                                                       String categoryCode, String keyword,
                                                       int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return runInTenant(tenantId, () -> supplierMapper.selectList(Wrappers.<SrmSupplier>lambdaQuery()
                        .eq(SrmSupplier::getTenantId, tenantId)
                        .eq(SrmSupplier::getDeleted, 0)
                        .eq(status != null && !status.isBlank(), SrmSupplier::getStatus, status)
                        .eq(categoryCode != null && !categoryCode.isBlank(),
                                SrmSupplier::getCategoryCode, categoryCode)
                        .and(normalizedKeyword != null, query -> query
                                .like(SrmSupplier::getSupplierNo, normalizedKeyword)
                                .or()
                                .like(SrmSupplier::getName, normalizedKeyword))
                        .orderByAsc(SrmSupplier::getName)
                        .last("LIMIT " + safeLimit))
                .stream().map(this::toSummary).toList());
    }

    /** {@inheritDoc} */
    @Override
    public List<InternalSupplierSummary> batch(Long tenantId, List<Long> supplierIds) {
        List<Long> normalizedIds = normalizeSupplierIds(supplierIds);
        return runInTenant(tenantId, () -> {
            Map<Long, SrmSupplier> suppliersById = supplierMapper.selectList(
                            Wrappers.<SrmSupplier>lambdaQuery()
                                    .eq(SrmSupplier::getTenantId, tenantId)
                                    .eq(SrmSupplier::getDeleted, 0)
                                    .in(SrmSupplier::getId, normalizedIds))
                    .stream()
                    .filter(supplier -> tenantId.equals(supplier.getTenantId()))
                    .filter(supplier -> normalizedIds.contains(supplier.getId()))
                    .collect(Collectors.toMap(SrmSupplier::getId, Function.identity(),
                            (first, ignored) -> first));
            return normalizedIds.stream()
                    .map(suppliersById::get)
                    .filter(Objects::nonNull)
                    .map(this::toSummary)
                    .toList();
        });
    }

    private List<Long> normalizeSupplierIds(List<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            throw new BusinessException(400, "supplierIds 不能为空");
        }
        if (supplierIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(400, "supplierIds 包含无效值");
        }
        List<Long> normalizedIds = List.copyOf(new LinkedHashSet<>(supplierIds));
        if (normalizedIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(400, "单次批量查询不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        return normalizedIds;
    }

    private <T> T runInTenant(Long tenantId, java.util.function.Supplier<T> action) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(400, "tenantId 必须为正整数");
        }
        try {
            SrmTenantContext.set(new SrmTenantContext.RequestIdentity(0L, tenantId, "internal-service"));
            SrmDataScopeContext.set(new SrmDataScopeContext.ScopeInfo(
                    0L, tenantId, "INTERNAL", null, "TENANT", Collections.emptySet()));
            return action.get();
        } finally {
            SrmDataScopeContext.clear();
            SrmTenantContext.clear();
        }
    }

    private InternalSupplierSummary toSummary(SrmSupplier supplier) {
        return InternalSupplierSummary.builder()
                .id(supplier.getId())
                .supplierNo(supplier.getSupplierNo())
                .name(supplier.getName())
                .status(supplier.getStatus())
                .levelCode(supplier.getLevelCode())
                .categoryCode(supplier.getCategoryCode())
                .build();
    }
}
