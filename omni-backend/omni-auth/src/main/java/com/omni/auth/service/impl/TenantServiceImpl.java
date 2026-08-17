package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.dto.TenantOption;
import com.omni.auth.dto.UpdateTenantRequest;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.mapper.TenantProvisionMapper;
import com.omni.auth.service.TenantService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户服务实现，基于 MyBatis-Plus 提供完整的 CRUD 操作。
 *
 * <p>查询 {@code sys_tenant} 表，为登录流程提供租户数据，
 * 同时支持管理端的租户增删改查操作。</p>
 *
 * @author Omni-Stack Team
 * @see TenantService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    /** 租户 Mapper */
    private final SysTenantMapper sysTenantMapper;

    /** 租户初始化 Mapper（调用存储过程） */
    private final TenantProvisionMapper tenantProvisionMapper;

    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

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
        List<SysTenant> tenants = sysTenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getStatus, 1)
                        .select(SysTenant::getId, SysTenant::getTenantName, SysTenant::getTenantCode));

        return tenants.stream()
                .map(t -> TenantOption.builder()
                        .id(t.getId())
                        .name(t.getTenantName())
                        .code(t.getTenantCode())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用 MyBatis-Plus 分页插件，按 ID 升序排列。</p>
     */
    @Override
    public PageResult<SysTenant> listTenants(int page, int size) {
        Page<SysTenant> mpPage = sysTenantMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysTenant>()
                        .orderByAsc(SysTenant::getId));
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal(), mpPage.getSize(), mpPage.getCurrent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysTenant getById(Long id) {
        SysTenant tenant = sysTenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }
        return tenant;
    }

    /**
     * {@inheritDoc}
     *
     * <p>创建租户后自动初始化：权限树（克隆 tenant 1）、默认角色（SUPER_ADMIN / USER / EMPLOYEE /
     * TEAM_LEADER / DEPT_LEADER）、根组织单元、管理员账号和 XSS 防护配置。管理员密码由创建请求显式提供，
     * 服务端只把 BCrypt 哈希传给初始化过程。</p>
     */
    @Override
    @Transactional
    public SysTenant createTenant(CreateTenantRequest request) {
        SysTenant tenant = new SysTenant();
        tenant.setTenantCode(request.getTenantCode());
        tenant.setTenantName(request.getTenantName());
        tenant.setDomain(request.getDomain());
        tenant.setContactName(request.getContactName());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        sysTenantMapper.insert(tenant);

        // 调用存储过程一键初始化租户数据
        String adminPwd = passwordEncoder.encode(request.getAdminPassword());
        tenantProvisionMapper.initTenant(tenant.getId(), tenant.getTenantName(), adminPwd);
        log.info("已创建租户并初始化数据: {} (id={})", tenant.getTenantName(), tenant.getId());
        return tenant;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅更新非 null 字段。</p>
     */
    @Override
    @Transactional
    public SysTenant updateTenant(Long id, UpdateTenantRequest request) {
        SysTenant tenant = sysTenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }
        if (request.getTenantName() != null) {
            tenant.setTenantName(request.getTenantName());
        }
        if (request.getDomain() != null) {
            tenant.setDomain(request.getDomain());
        }
        if (request.getContactName() != null) {
            tenant.setContactName(request.getContactName());
        }
        if (request.getContactPhone() != null) {
            tenant.setContactPhone(request.getContactPhone());
        }
        if (request.getStatus() != null) {
            tenant.setStatus(request.getStatus());
        }
        sysTenantMapper.updateById(tenant);
        log.info("已更新租户: {}", tenant.getTenantName());
        return tenant;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteTenant(Long id) {
        sysTenantMapper.deleteById(id);
        log.info("已删除租户 ID: {}", id);
    }
}
