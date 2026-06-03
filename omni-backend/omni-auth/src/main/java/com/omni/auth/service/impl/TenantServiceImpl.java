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
import java.util.stream.Collectors;

/**
 * 租户服务实现，基于 MyBatis-Plus 查询。
 *
 * <p>查询 {@code sys_tenant} 表，为登录流程提供租户数据。
 * 仅返回活跃租户（{@code status = 1}）。查询结果映射为轻量级的
 * {@link TenantOption} DTO，避免在公开的租户接口中暴露
 * 内部实体字段（域名、联系信息等）。</p>
 *
 * @see TenantService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    /** 租户 Mapper */
    private final SysTenantMapper sysTenantMapper;

    /**
     * {@inheritDoc}
     *
     * <p>在 {@code sys_tenant} 表上执行条件查询（{@code status = 1}），
     * 仅获取登录下拉框所需的列：{@code id}、{@code tenant_name} 和 {@code tenant_code}。
     * 每行映射为 {@link TenantOption}，包含 {@code id}、{@code name} 和 {@code code} 字段。</p>
     *
     * @return 非 null 的活跃租户选项列表；如果没有活跃租户则返回空列表
     */
    @Override
    public List<TenantOption> listActiveTenants() {
        // 仅查询必要的列，减少数据传输量
        List<SysTenant> tenants = sysTenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getStatus, 1)
                        .select(SysTenant::getId, SysTenant::getTenantName, SysTenant::getTenantCode));

        // 将实体字段映射为 DTO 字段（tenantName -> name, tenantCode -> code）
        return tenants.stream()
                .map(t -> TenantOption.builder()
                        .id(t.getId())
                        .name(t.getTenantName())
                        .code(t.getTenantCode())
                        .build())
                .collect(Collectors.toList());
    }
}
