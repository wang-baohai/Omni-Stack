package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.auth.dto.TenantOption;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tenant service implementation using MyBatis-Plus.
 *
 * <p>Queries the {@code sys_tenant} table to provide tenant data for the login flow.
 * Only active tenants ({@code status = 1}) are returned. The result is mapped to
 * lightweight {@link TenantOption} DTOs to avoid exposing internal entity fields
 * (domain, contact info, etc.) through the public tenants endpoint.</p>
 *
 * @see TenantService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final SysTenantMapper sysTenantMapper;

    /**
     * {@inheritDoc}
     *
     * <p>Executes a selective query on {@code sys_tenant} with {@code status = 1},
     * retrieving only the columns needed for the login dropdown: {@code id},
     * {@code tenant_name}, and {@code tenant_code}. Each row is mapped to a
     * {@link TenantOption} with fields {@code id}, {@code name}, and {@code code}.</p>
     *
     * @return a non-null list of active tenant options; empty if no active tenants exist
     */
    @Override
    public List<TenantOption> listActiveTenants() {
        // Select only the needed columns to minimize data transfer
        List<SysTenant> tenants = sysTenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getStatus, 1)
                        .select(SysTenant::getId, SysTenant::getTenantName, SysTenant::getTenantCode));

        // Map entity fields to DTO fields (tenantName -> name, tenantCode -> code)
        return tenants.stream()
                .map(t -> TenantOption.builder()
                        .id(t.getId())
                        .name(t.getTenantName())
                        .code(t.getTenantCode())
                        .build())
                .toList();
    }
}
