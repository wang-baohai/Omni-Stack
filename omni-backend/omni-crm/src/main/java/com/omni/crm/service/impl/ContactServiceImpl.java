package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViewAssembler;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.service.ContactService;
import com.omni.crm.service.support.CrmAuditSupport;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import com.omni.crm.service.support.CrmOwnerEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** CRM 联系人应用服务实现。 */
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final CrmContactMapper contactMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmActivityMapper activityMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmRecordAccessGuard accessGuard;
    private final CrmOwnerEnricher ownerEnricher;

    /** {@inheritDoc} */
    @Override
    public PageResult<CrmViews.ContactVO> list(CrmRequests.ContactQuery query) {
        return doList(query);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<CrmViews.ContactVO> listByCustomer(Long customerId, CrmRequests.ContactQuery query) {
        accessGuard.requireCustomer(customerId);
        query.setCustomerId(customerId);
        return doList(query);
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.ContactVO get(Long id) {
        return ownerEnricher.enrichOne(CrmViewAssembler.contact(accessGuard.requireContact(id), CrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ContactVO create(Long customerId, CrmRequests.CreateContactRequest request) {
        CrmCustomer customer = customerMapper.selectVisibleForUpdate(customerId);
        if (customer == null) throw new BusinessException(404, "客户不存在");
        if (request.isPrimary()) clearPrimary(customerId);
        CrmContact contact = new CrmContact(); contact.setTenantId(CrmTenantContext.requireTenantId());
        contact.setCustomerId(customerId); contact.setName(request.getName()); contact.setDepartment(request.getDepartment());
        contact.setJobTitle(request.getJobTitle()); contact.setMobile(request.getMobile()); contact.setPhone(request.getPhone());
        contact.setEmail(request.getEmail()); contact.setDecisionRole(request.getDecisionRole());
        contact.setPrimaryFlag(request.isPrimary() ? 1 : 0); contact.setStatus(1);
        contact.setOwnerUserId(customer.getOwnerUserId()); contact.setOwnerUnitId(customer.getOwnerUnitId());
        contact.setVersion(0); contact.setDeleted(0); CrmAuditSupport.created(contact); contactMapper.insert(contact);
        return ownerEnricher.enrichOne(CrmViewAssembler.contact(contact, CrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ContactVO update(Long id, CrmRequests.UpdateContactRequest request) {
        accessGuard.requireContact(id);
        LambdaUpdateWrapper<CrmContact> update = versioned(id, request.getVersion());
        setIf(update, request.getName(), CrmContact::getName); setIf(update, request.getDepartment(), CrmContact::getDepartment);
        setIf(update, request.getJobTitle(), CrmContact::getJobTitle); setIf(update, request.getMobile(), CrmContact::getMobile);
        setIf(update, request.getPhone(), CrmContact::getPhone); setIf(update, request.getEmail(), CrmContact::getEmail);
        setIf(update, request.getDecisionRole(), CrmContact::getDecisionRole); setIf(update, request.getStatus(), CrmContact::getStatus);
        audit(update); requireUpdated(contactMapper.update(null, update)); return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        accessGuard.requireContact(id);
        LambdaUpdateWrapper<CrmContact> update = versioned(id, version).set(CrmContact::getDeleted, 1);
        audit(update); requireUpdated(contactMapper.update(null, update));
        LocalDateTime now = LocalDateTime.now();
        String operator = CrmTenantContext.require().username();
        activityMapper.clearContactReference(id, now, operator);
        opportunityMapper.clearPrimaryContactReference(id, now, operator);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ContactVO setPrimary(Long id, Integer version) {
        CrmContact contact = accessGuard.requireContact(id);
        CrmCustomer customer = customerMapper.selectVisibleForUpdate(contact.getCustomerId());
        if (customer == null) throw new BusinessException(404, "客户不存在");
        clearPrimary(contact.getCustomerId());
        LambdaUpdateWrapper<CrmContact> update = versioned(id, version).set(CrmContact::getPrimaryFlag, 1);
        audit(update); requireUpdated(contactMapper.update(null, update)); return get(id);
    }

    private PageResult<CrmViews.ContactVO> doList(CrmRequests.ContactQuery query) {
        Page<CrmContact> result = contactMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<CrmContact>()
                        .eq(query.getCustomerId() != null, CrmContact::getCustomerId, query.getCustomerId())
                        .and(hasText(query.getKeyword()), wrapper -> wrapper.like(CrmContact::getName, query.getKeyword())
                                .or().like(CrmContact::getDepartment, query.getKeyword()))
                        .eq(query.getStatus() != null, CrmContact::getStatus, query.getStatus())
                        .orderByDesc(CrmContact::getPrimaryFlag).orderByDesc(CrmContact::getUpdateTime));
        List<CrmViews.ContactVO> records = result.getRecords().stream()
                .map(entity -> CrmViewAssembler.contact(entity, false)).toList();
        return new PageResult<>(ownerEnricher.enrich(records), result.getTotal(), result.getSize(), result.getCurrent());
    }

    private void clearPrimary(Long customerId) {
        contactMapper.clearPrimaryByCustomer(customerId, LocalDateTime.now(),
                CrmTenantContext.require().username());
    }

    private LambdaUpdateWrapper<CrmContact> versioned(Long id, Integer version) {
        return new LambdaUpdateWrapper<CrmContact>().eq(CrmContact::getId, id).eq(CrmContact::getVersion, version)
                .eq(CrmContact::getDeleted, 0).setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<CrmContact> update) {
        update.set(CrmContact::getUpdateTime, LocalDateTime.now())
                .set(CrmContact::getUpdateBy, CrmTenantContext.require().username());
    }

    private <T> void setIf(LambdaUpdateWrapper<CrmContact> update, T value,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<CrmContact, T> column) {
        if (value != null) update.set(column, value);
    }

    private void requireUpdated(int rows) {
        if (rows != 1) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
