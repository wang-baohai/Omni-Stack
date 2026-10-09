package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.dto.TenantOption;
import com.omni.auth.dto.UpdateTenantRequest;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.TenantProvisionStatusEnum;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.service.TenantProvisionService;
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

    /** 模块化租户初始化协调服务 */
    private final TenantProvisionService tenantProvisionService;

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
                        .eq(SysTenant::getProvisioningStatus, TenantProvisionStatusEnum.ACTIVE)
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
     */
    @Override
    public void requireLoginAvailable(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(403, "租户不可用");
        }
        SysTenant tenant = sysTenantMapper.selectById(tenantId);
        if (tenant == null || !Integer.valueOf(1).equals(tenant.getStatus())) {
            throw new BusinessException(403, "租户不可用");
        }
        if (TenantProvisionStatusEnum.PROVISIONING == tenant.getProvisioningStatus()) {
            throw new BusinessException(409, "租户正在初始化，请稍后再试");
        }
        if (TenantProvisionStatusEnum.ACTIVE != tenant.getProvisioningStatus()) {
            throw new BusinessException(409, "租户初始化未完成，请联系管理员");
        }
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
     * <p>创建租户后由模块化协调器初始化 Auth 本地数据，并通过可靠消息请求各业务模块初始化。
     * 管理员密码只在 Auth 内编码和使用，不进入跨服务事件。</p>
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
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
        sysTenantMapper.insert(tenant);

        String adminPwd = passwordEncoder.encode(request.getAdminPassword());
        tenantProvisionService.startProvisioning(tenant, adminPwd);
        log.info("已创建租户并启动模块化初始化: {} (id={})", tenant.getTenantName(), tenant.getId());
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
