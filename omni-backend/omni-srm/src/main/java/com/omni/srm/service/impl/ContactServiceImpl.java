package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplierContact;
import com.omni.srm.mapper.SrmSupplierContactMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.ContactService;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** SRM 供应商联系人服务实现。 */
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final SrmSupplierContactMapper contactMapper;
    private final SrmSupplierMapper supplierMapper;
    private final SrmRecordAccessGuard accessGuard;

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.ContactVO> list(Long supplierId) {
        accessGuard.requireSupplier(supplierId);
        boolean pii = SrmViewAssembler.canViewPii();
        return contactMapper.selectList(new LambdaQueryWrapper<SrmSupplierContact>()
                        .eq(SrmSupplierContact::getSupplierId, supplierId)
                        .orderByDesc(SrmSupplierContact::getPrimaryFlag)
                        .orderByAsc(SrmSupplierContact::getId)).stream()
                .map(entity -> SrmViewAssembler.contact(entity, pii)).toList();
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.ContactVO get(Long supplierId, Long id) {
        return SrmViewAssembler.contact(requireContact(supplierId, id), SrmViewAssembler.canViewPii());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.ContactVO create(Long supplierId, SrmRequests.CreateContactRequest request) {
        lockSupplier(supplierId);
        boolean primary = Boolean.TRUE.equals(request.getPrimaryFlag());
        if (primary) {
            clearPrimary(supplierId, null);
        }
        SrmSupplierContact contact = new SrmSupplierContact();
        contact.setTenantId(SrmTenantContext.requireTenantId());
        contact.setSupplierId(supplierId);
        contact.setName(request.getName());
        contact.setDepartment(request.getDepartment());
        contact.setJobTitle(request.getJobTitle());
        contact.setMobile(request.getMobile());
        contact.setPhone(request.getPhone());
        contact.setEmail(request.getEmail());
        contact.setDecisionRole(request.getDecisionRole());
        contact.setPrimaryFlag(primary);
        contact.setStatus(1);
        contact.setVersion(0);
        contact.setDeleted(0);
        SrmAuditSupport.created(contact);
        contactMapper.insert(contact);
        return SrmViewAssembler.contact(contact, SrmViewAssembler.canViewPii());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.ContactVO update(Long supplierId, Long id, SrmRequests.UpdateContactRequest request) {
        requireContact(supplierId, id);
        lockSupplier(supplierId);
        if (Boolean.TRUE.equals(request.getPrimaryFlag())) {
            clearPrimary(supplierId, id);
        }
        LambdaUpdateWrapper<SrmSupplierContact> update = new LambdaUpdateWrapper<SrmSupplierContact>()
                .eq(SrmSupplierContact::getId, id)
                .eq(SrmSupplierContact::getVersion, request.getVersion())
                .eq(SrmSupplierContact::getDeleted, 0)
                .setSql("version = version + 1");
        if (request.getName() != null) update.set(SrmSupplierContact::getName, request.getName());
        if (request.getDepartment() != null) update.set(SrmSupplierContact::getDepartment, request.getDepartment());
        if (request.getJobTitle() != null) update.set(SrmSupplierContact::getJobTitle, request.getJobTitle());
        if (request.getMobile() != null) update.set(SrmSupplierContact::getMobile, request.getMobile());
        if (request.getPhone() != null) update.set(SrmSupplierContact::getPhone, request.getPhone());
        if (request.getEmail() != null) update.set(SrmSupplierContact::getEmail, request.getEmail());
        if (request.getDecisionRole() != null) update.set(SrmSupplierContact::getDecisionRole, request.getDecisionRole());
        if (request.getPrimaryFlag() != null) update.set(SrmSupplierContact::getPrimaryFlag, request.getPrimaryFlag());
        update.set(SrmSupplierContact::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierContact::getUpdateBy, SrmTenantContext.require().username());
        if (contactMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        return get(supplierId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long supplierId, Long id, Integer version) {
        requireDeleteVersion(version);
        requireContact(supplierId, id);
        LambdaUpdateWrapper<SrmSupplierContact> update = new LambdaUpdateWrapper<SrmSupplierContact>()
                .eq(SrmSupplierContact::getId, id)
                .eq(SrmSupplierContact::getVersion, version)
                .eq(SrmSupplierContact::getDeleted, 0)
                .set(SrmSupplierContact::getDeleted, 1)
                .setSql("version = version + 1");
        update.set(SrmSupplierContact::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierContact::getUpdateBy, SrmTenantContext.require().username());
        if (contactMapper.update(null, update) != 1) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
    }

    private void requireDeleteVersion(Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "删除版本号必须为非负整数");
        }
    }

    private SrmSupplierContact requireContact(Long supplierId, Long id) {
        SrmSupplierContact contact = accessGuard.requireContact(id);
        if (!supplierId.equals(contact.getSupplierId())) {
            throw new BusinessException(404, "联系人不存在");
        }
        return contact;
    }

    private void lockSupplier(Long supplierId) {
        if (supplierMapper.selectVisibleForUpdate(supplierId) == null) {
            throw new BusinessException(404, "供应商不存在");
        }
    }

    private void clearPrimary(Long supplierId, Long excludeId) {
        LambdaUpdateWrapper<SrmSupplierContact> update = new LambdaUpdateWrapper<SrmSupplierContact>()
                .eq(SrmSupplierContact::getSupplierId, supplierId)
                .eq(SrmSupplierContact::getPrimaryFlag, true)
                .eq(SrmSupplierContact::getDeleted, 0)
                .ne(excludeId != null, SrmSupplierContact::getId, excludeId)
                .set(SrmSupplierContact::getPrimaryFlag, false)
                .set(SrmSupplierContact::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplierContact::getUpdateBy, SrmTenantContext.require().username())
                .setSql("version = version + 1");
        contactMapper.update(null, update);
    }
}
