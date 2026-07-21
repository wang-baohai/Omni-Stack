package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplierBankAccount;
import com.omni.srm.mapper.SrmSupplierBankAccountMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.BankAccountService;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** SRM 供应商银行账户服务实现。 */
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    private final SrmSupplierBankAccountMapper bankAccountMapper;
    private final SrmSupplierMapper supplierMapper;
    private final SrmRecordAccessGuard accessGuard;

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.BankAccountVO> list(Long supplierId) {
        accessGuard.requireSupplier(supplierId);
        boolean pii = SrmViewAssembler.canViewPii();
        return bankAccountMapper.selectList(new LambdaQueryWrapper<SrmSupplierBankAccount>()
                        .eq(SrmSupplierBankAccount::getSupplierId, supplierId)
                        .orderByDesc(SrmSupplierBankAccount::getPrimaryFlag)
                        .orderByAsc(SrmSupplierBankAccount::getId)).stream()
                .map(entity -> SrmViewAssembler.bankAccount(entity, pii)).toList();
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.BankAccountVO get(Long supplierId, Long id) {
        return SrmViewAssembler.bankAccount(requireAccount(supplierId, id), SrmViewAssembler.canViewPii());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.BankAccountVO create(Long supplierId, SrmRequests.CreateBankAccountRequest request) {
        lockSupplier(supplierId);
        boolean primary = Boolean.TRUE.equals(request.getPrimaryFlag());
        if (primary) {
            clearPrimary(supplierId, null);
        }
        SrmSupplierBankAccount account = new SrmSupplierBankAccount();
        account.setTenantId(SrmTenantContext.requireTenantId());
        account.setSupplierId(supplierId);
        account.setAccountName(request.getAccountName());
        account.setAccountNo(request.getAccountNo());
        account.setBankName(request.getBankName());
        account.setBankBranch(request.getBankBranch());
        account.setBankCode(request.getBankCode());
        account.setPrimaryFlag(primary);
        account.setStatus(1);
        account.setVersion(0);
        account.setDeleted(0);
        SrmAuditSupport.created(account);
        bankAccountMapper.insert(account);
        return SrmViewAssembler.bankAccount(account, SrmViewAssembler.canViewPii());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.BankAccountVO update(Long supplierId, Long id, SrmRequests.UpdateBankAccountRequest request) {
        requireAccount(supplierId, id);
        lockSupplier(supplierId);
        if (Boolean.TRUE.equals(request.getPrimaryFlag())) {
            clearPrimary(supplierId, id);
        }
        LambdaUpdateWrapper<SrmSupplierBankAccount> update = new LambdaUpdateWrapper<SrmSupplierBankAccount>()
                .eq(SrmSupplierBankAccount::getId, id)
                .eq(SrmSupplierBankAccount::getVersion, request.getVersion())
                .eq(SrmSupplierBankAccount::getDeleted, 0)
                .setSql("version = version + 1");
        if (request.getAccountName() != null) update.set(SrmSupplierBankAccount::getAccountName, request.getAccountName());
        if (request.getAccountNo() != null) update.set(SrmSupplierBankAccount::getAccountNo, request.getAccountNo());
        if (request.getBankName() != null) update.set(SrmSupplierBankAccount::getBankName, request.getBankName());
        if (request.getBankBranch() != null) update.set(SrmSupplierBankAccount::getBankBranch, request.getBankBranch());
        if (request.getBankCode() != null) update.set(SrmSupplierBankAccount::getBankCode, request.getBankCode());
        if (request.getPrimaryFlag() != null) update.set(SrmSupplierBankAccount::getPrimaryFlag, request.getPrimaryFlag());
        update.set(SrmSupplierBankAccount::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierBankAccount::getUpdateBy, SrmTenantContext.require().username());
        if (bankAccountMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        return get(supplierId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long supplierId, Long id, Integer version) {
        requireDeleteVersion(version);
        requireAccount(supplierId, id);
        LambdaUpdateWrapper<SrmSupplierBankAccount> update = new LambdaUpdateWrapper<SrmSupplierBankAccount>()
                .eq(SrmSupplierBankAccount::getId, id)
                .eq(SrmSupplierBankAccount::getVersion, version)
                .eq(SrmSupplierBankAccount::getDeleted, 0)
                .set(SrmSupplierBankAccount::getDeleted, 1)
                .setSql("version = version + 1");
        update.set(SrmSupplierBankAccount::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierBankAccount::getUpdateBy, SrmTenantContext.require().username());
        if (bankAccountMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
    }

    private void requireDeleteVersion(Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "删除版本号必须为非负整数");
        }
    }

    private SrmSupplierBankAccount requireAccount(Long supplierId, Long id) {
        SrmSupplierBankAccount account = accessGuard.requireBankAccount(id);
        if (!supplierId.equals(account.getSupplierId())) {
            throw new BusinessException(404, "银行账户不存在");
        }
        return account;
    }

    private void lockSupplier(Long supplierId) {
        if (supplierMapper.selectVisibleForUpdate(supplierId) == null) {
            throw new BusinessException(404, "供应商不存在");
        }
    }

    private void clearPrimary(Long supplierId, Long excludeId) {
        LambdaUpdateWrapper<SrmSupplierBankAccount> update = new LambdaUpdateWrapper<SrmSupplierBankAccount>()
                .eq(SrmSupplierBankAccount::getSupplierId, supplierId)
                .eq(SrmSupplierBankAccount::getPrimaryFlag, true)
                .eq(SrmSupplierBankAccount::getDeleted, 0)
                .ne(excludeId != null, SrmSupplierBankAccount::getId, excludeId)
                .set(SrmSupplierBankAccount::getPrimaryFlag, false)
                .set(SrmSupplierBankAccount::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierBankAccount::getUpdateBy, SrmTenantContext.require().username())
                .setSql("version = version + 1");
        bankAccountMapper.update(null, update);
    }
}
