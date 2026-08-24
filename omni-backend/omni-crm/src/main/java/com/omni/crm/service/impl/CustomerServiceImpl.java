package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.crm.domain.CrmStateMachine;
import com.omni.crm.domain.CustomerStatus;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViewAssembler;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.dto.DomainEventEnvelope;
import com.omni.crm.entity.CrmActivity;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.entity.CrmLeadConversion;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.entity.CrmOwnerChangeLog;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadConversionMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmOwnerChangeLogMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.crm.security.PiiMasker;
import com.omni.crm.service.CustomerService;
import com.omni.crm.service.support.CrmAuditSupport;
import com.omni.crm.service.support.CrmOwnerResolver;
import com.omni.crm.service.support.CrmOwnerEnricher;
import com.omni.crm.service.support.CrmPermissionScopeExecutor;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** CRM 客户应用服务实现。 */
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CrmCustomerMapper customerMapper;
    private final CrmContactMapper contactMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmActivityMapper activityMapper;
    private final CrmLeadConversionMapper conversionMapper;
    private final CrmLeadMapper leadMapper;
    private final CrmOwnerChangeLogMapper ownerLogMapper;
    private final CrmRecordAccessGuard accessGuard;
    private final CrmOwnerResolver ownerResolver;
    private final CrmOwnerEnricher ownerEnricher;
    private final CrmPermissionScopeExecutor scopeExecutor;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    public PageResult<CrmViews.CustomerVO> list(CrmRequests.CustomerQuery query) {
        Page<CrmCustomer> result = customerMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<CrmCustomer>()
                        .and(hasText(query.getKeyword()), wrapper -> wrapper.like(CrmCustomer::getName, query.getKeyword())
                                .or().like(CrmCustomer::getCustomerNo, query.getKeyword()))
                        .eq(hasText(query.getStatus()), CrmCustomer::getStatus, query.getStatus())
                        .eq(query.getOwnerUserId() != null, CrmCustomer::getOwnerUserId, query.getOwnerUserId())
                        .orderByDesc(CrmCustomer::getUpdateTime).orderByDesc(CrmCustomer::getId));
        List<CrmViews.CustomerVO> records = result.getRecords().stream()
                .map(entity -> CrmViewAssembler.customer(entity, false)).toList();
        return new PageResult<>(ownerEnricher.enrich(records), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.CustomerVO get(Long id) {
        return ownerEnricher.enrichOne(CrmViewAssembler.customer(accessGuard.requireCustomer(id), CrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.CustomerOverviewVO overview(Long id) {
        CrmCustomer customer = accessGuard.requireCustomer(id);
        boolean pii = CrmViewAssembler.canViewPii();
        CrmViews.CustomerOverviewVO overview = new CrmViews.CustomerOverviewVO();
        overview.setCustomer(CrmViewAssembler.customer(customer, pii));
        overview.setContacts(scopeExecutor.executeIfGranted("crm:contact:list", () -> contactMapper.selectList(
                        new LambdaQueryWrapper<CrmContact>().eq(CrmContact::getCustomerId, id)
                                .orderByDesc(CrmContact::getPrimaryFlag).orderByAsc(CrmContact::getId)).stream()
                .map(entity -> CrmViewAssembler.contact(entity, pii)).toList(), List.of()));
        overview.setOpenOpportunities(scopeExecutor.executeIfGranted("crm:opportunity:list", () -> opportunityMapper.selectList(
                        new LambdaQueryWrapper<CrmOpportunity>().eq(CrmOpportunity::getCustomerId, id)
                                .eq(CrmOpportunity::getStatus, "OPEN").orderByDesc(CrmOpportunity::getUpdateTime)).stream()
                .map(CrmViewAssembler::opportunity).toList(), List.of()));
        overview.setRecentActivities(scopeExecutor.executeIfGranted("crm:activity:list", () -> activityMapper.selectList(
                        new LambdaQueryWrapper<CrmActivity>().eq(CrmActivity::getRootType, "CUSTOMER")
                                .eq(CrmActivity::getRootId, id).orderByDesc(CrmActivity::getCreateTime).last("LIMIT 20")).stream()
                .map(entity -> CrmViewAssembler.activity(entity, pii)).toList(), List.of()));
        List<CrmViews.OwnedVO> owners = new java.util.ArrayList<>();
        owners.add(overview.getCustomer()); owners.addAll(overview.getContacts());
        owners.addAll(overview.getOpenOpportunities()); owners.addAll(overview.getRecentActivities());
        ownerEnricher.enrich(owners);
        overview.setConvertedLeadIds(scopeExecutor.executeIfGranted("crm:lead:list", () -> visibleConvertedLeadIds(id), List.of()));
        return overview;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.CustomerVO create(CrmRequests.CreateCustomerRequest request) {
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCreate(request.getOwnerUserId(), "crm:customer:transfer");
        CrmCustomer customer = new CrmCustomer(); customer.setTenantId(ServiceIdentityContext.requireTenantId());
        customer.setCustomerNo("TMP-" + UUID.randomUUID()); customer.setName(request.getName());
        customer.setNormalizedName(normalize(request.getName())); customer.setCustomerType(request.getCustomerType());
        customer.setIndustryCode(request.getIndustryCode()); customer.setLevelCode(request.getLevelCode());
        customer.setSourceCode(request.getSourceCode()); customer.setCreditCode(request.getCreditCode());
        customer.setWebsite(request.getWebsite()); customer.setPhone(request.getPhone()); customer.setEmail(request.getEmail());
        customer.setRegion(request.getRegion()); customer.setAddress(request.getAddress()); customer.setStatus("POTENTIAL");
        customer.setOwnerUserId(owner.userId()); customer.setOwnerUnitId(owner.unitId());
        customer.setVersion(0); customer.setDeleted(0); CrmAuditSupport.created(customer); customerMapper.insert(customer);
        String number = "C" + customer.getTenantId() + "-" + customer.getId();
        customerMapper.update(null, new LambdaUpdateWrapper<CrmCustomer>().eq(CrmCustomer::getId, customer.getId())
                .set(CrmCustomer::getCustomerNo, number)); customer.setCustomerNo(number);
        return ownerEnricher.enrichOne(CrmViewAssembler.customer(customer, CrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.CustomerVO update(Long id, CrmRequests.UpdateCustomerRequest request) {
        accessGuard.requireCustomer(id);
        LambdaUpdateWrapper<CrmCustomer> update = versioned(id, request.getVersion());
        setIf(update, request.getName(), CrmCustomer::getName);
        if (request.getName() != null) update.set(CrmCustomer::getNormalizedName, normalize(request.getName()));
        setIf(update, request.getCustomerType(), CrmCustomer::getCustomerType); setIf(update, request.getIndustryCode(), CrmCustomer::getIndustryCode);
        setIf(update, request.getLevelCode(), CrmCustomer::getLevelCode); setIf(update, request.getSourceCode(), CrmCustomer::getSourceCode);
        setIf(update, request.getCreditCode(), CrmCustomer::getCreditCode); setIf(update, request.getWebsite(), CrmCustomer::getWebsite);
        setIf(update, request.getPhone(), CrmCustomer::getPhone); setIf(update, request.getEmail(), CrmCustomer::getEmail);
        setIf(update, request.getRegion(), CrmCustomer::getRegion); setIf(update, request.getAddress(), CrmCustomer::getAddress);
        audit(update); requireUpdated(customerMapper.update(null, update)); return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        accessGuard.requireCustomer(id);
        Long open = opportunityMapper.countAllOpenByCustomer(id);
        if (open > 0) throw new BusinessException(409, "客户存在开放商机，不能删除");
        LambdaUpdateWrapper<CrmCustomer> update = versioned(id, version).set(CrmCustomer::getDeleted, 1);
        audit(update); requireUpdated(customerMapper.update(null, update));
        LocalDateTime now = LocalDateTime.now();
        String operator = ServiceIdentityContext.require().username();
        activityMapper.clearContactReferencesByCustomer(id, now, operator);
        opportunityMapper.clearPrimaryContactReferencesByCustomer(id, now, operator);
        contactMapper.softDeleteByCustomer(id, now, operator);
        activityMapper.softDeleteByRoot("CUSTOMER", id, now, operator);
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.DuplicateCandidateVO> duplicateCheck(CrmRequests.CustomerDuplicateRequest request) {
        if (!hasText(request.getName()) && !hasText(request.getCreditCode()) && !hasText(request.getPhone())) return List.of();
        LambdaQueryWrapper<CrmCustomer> query = new LambdaQueryWrapper<>();
        query.and(wrapper -> {
            boolean added = false;
            if (hasText(request.getName())) { wrapper.eq(CrmCustomer::getNormalizedName, normalize(request.getName())); added = true; }
            if (hasText(request.getCreditCode())) { if (added) wrapper.or(); wrapper.eq(CrmCustomer::getCreditCode, request.getCreditCode()); added = true; }
            if (hasText(request.getPhone())) { if (added) wrapper.or(); wrapper.eq(CrmCustomer::getPhone, request.getPhone()); }
        }).last("LIMIT 10");
        return customerMapper.selectList(query).stream().map(entity -> {
            CrmViews.DuplicateCandidateVO vo = new CrmViews.DuplicateCandidateVO(); vo.setId(entity.getId());
            vo.setNumber(entity.getCustomerNo()); vo.setName(entity.getName()); vo.setMatchedBy("CUSTOMER_PROFILE");
            vo.setMaskedContact(PiiMasker.phone(entity.getPhone())); return vo;
        }).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.CustomerVO changeStatus(Long id, CrmRequests.CustomerStatusRequest request) {
        CrmCustomer current = accessGuard.requireCustomer(id);
        CustomerStatus target;
        try { target = CustomerStatus.valueOf(request.getStatus()); }
        catch (IllegalArgumentException exception) { throw new BusinessException(400, "客户状态无效"); }
        if (target == CustomerStatus.BLACKLISTED || CustomerStatus.valueOf(current.getStatus()) == CustomerStatus.BLACKLISTED) {
            throw new BusinessException(403, "黑名单状态必须使用专用命令");
        }
        CrmStateMachine.requireCustomerTransition(CustomerStatus.valueOf(current.getStatus()), target);
        return updateStatus(id, request.getVersion(), target);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.CustomerVO blacklist(Long id, CrmRequests.VersionRequest request) {
        CrmCustomer current = accessGuard.requireCustomer(id);
        CrmStateMachine.requireCustomerTransition(CustomerStatus.valueOf(current.getStatus()), CustomerStatus.BLACKLISTED);
        return updateStatus(id, request.getVersion(), CustomerStatus.BLACKLISTED);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.CustomerVO restoreFromBlacklist(Long id, CrmRequests.VersionRequest request) {
        CrmCustomer current = accessGuard.requireCustomer(id);
        CrmStateMachine.requireCustomerTransition(CustomerStatus.valueOf(current.getStatus()), CustomerStatus.ACTIVE);
        return updateStatus(id, request.getVersion(), CustomerStatus.ACTIVE);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.CustomerVO transfer(Long id, CrmRequests.TransferCustomerRequest request) {
        CrmCustomer current = customerMapper.selectVisibleForUpdate(id);
        if (current == null) throw new BusinessException(404, "客户不存在");
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCommand(request.getOwnerUserId());
        LambdaUpdateWrapper<CrmCustomer> update = versioned(id, request.getVersion())
                .set(CrmCustomer::getOwnerUserId, owner.userId()).set(CrmCustomer::getOwnerUnitId, owner.unitId());
        audit(update); requireUpdated(customerMapper.update(null, update));
        LocalDateTime now = LocalDateTime.now();
        String operator = ServiceIdentityContext.require().username();
        contactMapper.syncOwnerByCustomer(id, owner.userId(), owner.unitId(), now, operator);
        activityMapper.syncOwnerByRoot("CUSTOMER", id, owner.userId(), owner.unitId(), now, operator);
        if (request.isCascadeOpenOpportunities()) {
            List<CrmOpportunity> opportunities = opportunityMapper.selectAllOpenByCustomer(id);
            opportunityMapper.syncAllOpenOwnerByCustomer(id, owner.userId(), owner.unitId(), now, operator);
            List<Long> opportunityIds = opportunities.stream().map(CrmOpportunity::getId).toList();
            if (!opportunities.isEmpty()) {
                activityMapper.syncOwnerByOpportunityRoots(opportunityIds, owner.userId(), owner.unitId(), now, operator);
                opportunities.forEach(opportunity -> appendOwnerLog(
                        "OPPORTUNITY", opportunity.getId(), opportunity.getOwnerUserId(),
                        opportunity.getOwnerUnitId(), owner, "CUSTOMER_TRANSFER_CASCADE", request.getReason()));
            }
        }
        appendOwnerLog("CUSTOMER", id, current.getOwnerUserId(), current.getOwnerUnitId(),
                owner, "TRANSFER", request.getReason());
        publishOwnerChangedEvent(current, owner, request);
        return get(id);
    }

    private List<Long> visibleConvertedLeadIds(Long customerId) {
        List<Long> ids = conversionMapper.selectList(new LambdaQueryWrapper<CrmLeadConversion>()
                .eq(CrmLeadConversion::getCustomerId, customerId)).stream().map(CrmLeadConversion::getLeadId).toList();
        if (ids.isEmpty()) return List.of();
        return leadMapper.selectList(new LambdaQueryWrapper<CrmLead>().in(CrmLead::getId, ids))
                .stream().map(CrmLead::getId).toList();
    }

    private CrmViews.CustomerVO updateStatus(Long id, Integer version, CustomerStatus status) {
        LambdaUpdateWrapper<CrmCustomer> update = versioned(id, version).set(CrmCustomer::getStatus, status.name());
        audit(update); requireUpdated(customerMapper.update(null, update)); return get(id);
    }

    private void appendOwnerLog(String entityType, Long entityId, Long oldOwnerUserId,
                                Long oldOwnerUnitId, CrmOwnerResolver.Owner owner,
                                String operationType, String reason) {
        CrmOwnerChangeLog log = new CrmOwnerChangeLog();
        log.setTenantId(ServiceIdentityContext.requireTenantId());
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldOwnerUserId(oldOwnerUserId);
        log.setOldOwnerUnitId(oldOwnerUnitId);
        log.setNewOwnerUserId(owner.userId());
        log.setNewOwnerUnitId(owner.unitId());
        log.setOperationType(operationType);
        log.setReason(reason);
        log.setOperatorUserId(ServiceIdentityContext.require().userId());
        log.setOperatedTime(LocalDateTime.now());
        CrmAuditSupport.created(log);
        ownerLogMapper.insert(log);
    }

    private void publishOwnerChangedEvent(CrmCustomer customer, CrmOwnerResolver.Owner owner,
                                          CrmRequests.TransferCustomerRequest request) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType("crm.customer.owner-changed.v1")
                .occurredAt(LocalDateTime.now())
                .tenantId(ServiceIdentityContext.requireTenantId())
                .producer("omni-crm")
                .aggregateType("CUSTOMER")
                .aggregateId(customer.getId())
                .aggregateVersion(request.getVersion() + 1)
                .actorUserId(ServiceIdentityContext.require().userId())
                .payload(Map.of("customerId", customer.getId(),
                        "oldOwnerUserId", customer.getOwnerUserId(),
                        "oldOwnerUnitId", customer.getOwnerUnitId(),
                        "newOwnerUserId", owner.userId(),
                        "newOwnerUnitId", owner.unitId(),
                        "cascadeOpenOpportunities", request.isCascadeOpenOpportunities()))
                .build();
        reliableMessageRelay.send("crm-domain-out-0", envelope,
                ServiceIdentityContext.requireTenantId(), eventId);
    }

    private LambdaUpdateWrapper<CrmCustomer> versioned(Long id, Integer version) {
        return new LambdaUpdateWrapper<CrmCustomer>().eq(CrmCustomer::getId, id).eq(CrmCustomer::getVersion, version)
                .eq(CrmCustomer::getDeleted, 0).setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<CrmCustomer> update) {
        update.set(CrmCustomer::getUpdateTime, LocalDateTime.now())
                .set(CrmCustomer::getUpdateBy, ServiceIdentityContext.require().username());
    }

    private <T> void setIf(LambdaUpdateWrapper<CrmCustomer> update, T value,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<CrmCustomer, T> column) {
        if (value != null) update.set(column, value);
    }

    private void requireUpdated(int rows) {
        if (rows != 1) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
    }

    private String normalize(String value) { return value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
