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
import java.util.List;

/**
 * 服务间供应商只读查询服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class InternalSupplierServiceImpl implements InternalSupplierService {

    private static final int MAX_LIMIT = 100;

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
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return runInTenant(tenantId, () -> supplierMapper.selectList(Wrappers.<SrmSupplier>lambdaQuery()
                        .eq(SrmSupplier::getTenantId, tenantId)
                        .eq(SrmSupplier::getDeleted, 0)
                        .eq(status != null && !status.isBlank(), SrmSupplier::getStatus, status)
                        .eq(categoryCode != null && !categoryCode.isBlank(),
                                SrmSupplier::getCategoryCode, categoryCode)
                        .orderByAsc(SrmSupplier::getName)
                        .last("LIMIT " + safeLimit))
                .stream().map(this::toSummary).toList());
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
