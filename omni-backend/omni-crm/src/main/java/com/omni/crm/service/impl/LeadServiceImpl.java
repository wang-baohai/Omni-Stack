package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.crm.domain.CrmStateMachine;
import com.omni.crm.domain.LeadStatus;
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
import com.omni.crm.entity.CrmOpportunityStageHistory;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.entity.CrmOwnerChangeLog;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadConversionMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmOpportunityStageHistoryMapper;
import com.omni.crm.mapper.CrmOwnerChangeLogMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.security.PiiMasker;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.LeadService;
import com.omni.crm.service.support.CrmAuditSupport;
import com.omni.crm.service.support.CrmOwnerResolver;
import com.omni.crm.service.support.CrmOwnerEnricher;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** CRM 线索应用服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {

    private final CrmLeadMapper leadMapper;
    private final CrmLeadConversionMapper conversionMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmContactMapper contactMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmOpportunityStageHistoryMapper opportunityHistoryMapper;
    private final CrmPipelineStageMapper stageMapper;
    private final CrmActivityMapper activityMapper;
    private final CrmOwnerChangeLogMapper ownerLogMapper;
    private final CrmRecordAccessGuard accessGuard;
    private final CrmOwnerResolver ownerResolver;
    private final CrmOwnerEnricher ownerEnricher;
    private final CrmTenantInitializer tenantInitializer;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    public PageResult<CrmViews.LeadVO> list(CrmRequests.LeadQuery query) {
        Page<CrmLead> result = leadMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<CrmLead>()
                        .and(hasText(query.getKeyword()), wrapper -> wrapper
                                .like(CrmLead::getFullName, query.getKeyword())
                                .or().like(CrmLead::getCompanyName, query.getKeyword())
                                .or().like(CrmLead::getLeadNo, query.getKeyword()))
                        .eq(hasText(query.getStatus()), CrmLead::getStatus, query.getStatus())
                        .eq(query.getOwnerUserId() != null, CrmLead::getOwnerUserId, query.getOwnerUserId())
                        .eq(hasText(query.getSourceCode()), CrmLead::getSourceCode, query.getSourceCode())
                        .orderByDesc(CrmLead::getUpdateTime).orderByDesc(CrmLead::getId));
        List<CrmViews.LeadVO> records = result.getRecords().stream()
                .map(entity -> CrmViewAssembler.lead(entity, false)).toList();
        return new PageResult<>(ownerEnricher.enrich(records), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.LeadVO get(Long id) {
        return ownerEnricher.enrichOne(CrmViewAssembler.lead(accessGuard.requireLead(id), CrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.LeadVO create(CrmRequests.CreateLeadRequest request) {
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCreate(request.getOwnerUserId(), "crm:lead:assign");
        CrmLead lead = new CrmLead();
        lead.setTenantId(CrmTenantContext.requireTenantId()); lead.setLeadNo("TMP-" + UUID.randomUUID());
        lead.setFullName(request.getFullName()); lead.setCompanyName(request.getCompanyName()); lead.setJobTitle(request.getJobTitle());
        lead.setMobile(request.getMobile()); lead.setPhone(request.getPhone()); lead.setEmail(request.getEmail());
        lead.setRegion(request.getRegion()); lead.setAddress(request.getAddress()); lead.setSourceCode(request.getSourceCode());
        lead.setIndustryCode(request.getIndustryCode()); lead.setRating(request.getRating()); lead.setStatus(LeadStatus.NEW.name());
        lead.setOwnerUserId(owner.userId()); lead.setOwnerUnitId(owner.unitId()); lead.setAssignedTime(LocalDateTime.now());
        lead.setNextFollowupTime(request.getNextFollowupTime()); lead.setVersion(0); lead.setDeleted(0); CrmAuditSupport.created(lead);
        leadMapper.insert(lead);
        String leadNo = "L" + lead.getTenantId() + "-" + lead.getId();
        leadMapper.update(null, new LambdaUpdateWrapper<CrmLead>().eq(CrmLead::getId, lead.getId()).set(CrmLead::getLeadNo, leadNo));
        lead.setLeadNo(leadNo);
        publishLeadEvent("crm.lead.created.v1", lead, lead.getVersion(),
                Map.of("leadId", lead.getId(), "status", lead.getStatus(),
                        "ownerUserId", lead.getOwnerUserId(), "ownerUnitId", lead.getOwnerUnitId()));
        return ownerEnricher.enrichOne(CrmViewAssembler.lead(lead, CrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.LeadVO update(Long id, CrmRequests.UpdateLeadRequest request) {
        CrmLead current = accessGuard.requireLead(id);
        if (LeadStatus.CONVERTED.name().equals(current.getStatus())) {
            throw new BusinessException(409, "已转换线索不可修改");
        }
        LambdaUpdateWrapper<CrmLead> update = versionedLead(id, request.getVersion());
        setIf(update, request.getFullName(), CrmLead::getFullName); setIf(update, request.getCompanyName(), CrmLead::getCompanyName);
        setIf(update, request.getJobTitle(), CrmLead::getJobTitle); setIf(update, request.getMobile(), CrmLead::getMobile);
        setIf(update, request.getPhone(), CrmLead::getPhone); setIf(update, request.getEmail(), CrmLead::getEmail);
        setIf(update, request.getRegion(), CrmLead::getRegion); setIf(update, request.getAddress(), CrmLead::getAddress);
        setIf(update, request.getSourceCode(), CrmLead::getSourceCode); setIf(update, request.getIndustryCode(), CrmLead::getIndustryCode);
        setIf(update, request.getRating(), CrmLead::getRating); setIf(update, request.getNextFollowupTime(), CrmLead::getNextFollowupTime);
        audit(update);
        requireUpdated(leadMapper.update(null, update));
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, CrmRequests.VersionRequest request) {
        CrmLead lead = accessGuard.requireLead(id);
        if (LeadStatus.CONVERTED.name().equals(lead.getStatus())) {
            throw new BusinessException(409, "已转换线索不可删除");
        }
        LambdaUpdateWrapper<CrmLead> update = versionedLead(id, request.getVersion())
                .set(CrmLead::getDeleted, 1);
        audit(update); requireUpdated(leadMapper.update(null, update));
        activityMapper.softDeleteByRoot("LEAD", id, LocalDateTime.now(),
                CrmTenantContext.require().username());
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.DuplicateCandidateVO> duplicateCheck(CrmRequests.LeadDuplicateRequest request) {
        if (!hasText(request.getCompanyName()) && !hasText(request.getMobile()) && !hasText(request.getEmail())) {
            return List.of();
        }
        LambdaQueryWrapper<CrmLead> query = new LambdaQueryWrapper<>();
        query.and(wrapper -> {
            boolean added = false;
            if (hasText(request.getCompanyName())) { wrapper.eq(CrmLead::getCompanyName, request.getCompanyName()); added = true; }
            if (hasText(request.getMobile())) { if (added) wrapper.or(); wrapper.eq(CrmLead::getMobile, request.getMobile()); added = true; }
            if (hasText(request.getEmail())) { if (added) wrapper.or(); wrapper.eq(CrmLead::getEmail, request.getEmail()); }
        }).last("LIMIT 10");
        return leadMapper.selectList(query).stream().map(lead -> duplicate(lead, request)).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.LeadVO assign(Long id, CrmRequests.AssignRequest request) {
        CrmLead current = accessGuard.requireLead(id);
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCommand(request.getOwnerUserId());
        LambdaUpdateWrapper<CrmLead> update = versionedLead(id, request.getVersion())
                .set(CrmLead::getOwnerUserId, owner.userId()).set(CrmLead::getOwnerUnitId, owner.unitId())
                .set(CrmLead::getAssignedTime, LocalDateTime.now());
        audit(update); requireUpdated(leadMapper.update(null, update));
        activityMapper.syncOwnerByRoot("LEAD", id, owner.userId(), owner.unitId(), LocalDateTime.now(),
                CrmTenantContext.require().username());
        appendOwnerLog("LEAD", id, current.getOwnerUserId(), current.getOwnerUnitId(), owner, "ASSIGN", request.getReason());
        publishLeadAssignmentEvent(current, owner, request.getVersion() + 1);
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<CrmViews.LeadVO> batchAssign(CrmRequests.BatchAssignRequest request) {
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCommand(request.getOwnerUserId());
        List<CrmViews.LeadVO> results = new ArrayList<>();
        for (CrmRequests.VersionedId item : request.getItems()) {
            CrmLead current = accessGuard.requireLead(item.getId());
            LambdaUpdateWrapper<CrmLead> update = versionedLead(item.getId(), item.getVersion())
                    .set(CrmLead::getOwnerUserId, owner.userId()).set(CrmLead::getOwnerUnitId, owner.unitId())
                    .set(CrmLead::getAssignedTime, LocalDateTime.now());
            audit(update); requireUpdated(leadMapper.update(null, update));
            activityMapper.syncOwnerByRoot("LEAD", item.getId(), owner.userId(), owner.unitId(),
                    LocalDateTime.now(), CrmTenantContext.require().username());
            appendOwnerLog("LEAD", item.getId(), current.getOwnerUserId(), current.getOwnerUnitId(), owner,
                    "BATCH_ASSIGN", request.getReason());
            publishLeadAssignmentEvent(current, owner, item.getVersion() + 1);
            results.add(get(item.getId()));
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.LeadVO qualify(Long id, CrmRequests.VersionRequest request) {
        CrmLead lead = accessGuard.requireLead(id);
        CrmStateMachine.requireLeadTransition(LeadStatus.valueOf(lead.getStatus()), LeadStatus.QUALIFIED);
        return changeStatus(id, request.getVersion(), LeadStatus.QUALIFIED, null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.LeadVO disqualify(Long id, CrmRequests.DisqualifyLeadRequest request) {
        CrmLead lead = accessGuard.requireLead(id);
        CrmStateMachine.requireLeadTransition(LeadStatus.valueOf(lead.getStatus()), LeadStatus.DISQUALIFIED);
        return changeStatus(id, request.getVersion(), LeadStatus.DISQUALIFIED, request.getReason());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.LeadVO reopen(Long id, CrmRequests.VersionRequest request) {
        CrmLead lead = accessGuard.requireLead(id);
        CrmStateMachine.requireLeadTransition(LeadStatus.valueOf(lead.getStatus()), LeadStatus.FOLLOWING);
        return changeStatus(id, request.getVersion(), LeadStatus.FOLLOWING, null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ConversionResultVO convert(Long id, CrmRequests.ConvertLeadRequest request) {
        CrmLead lead = leadMapper.selectVisibleForUpdate(id);
        if (lead == null) {
            throw new BusinessException(404, "线索不存在");
        }
        CrmLeadConversion existing = conversionMapper.selectOne(new LambdaQueryWrapper<CrmLeadConversion>()
                .eq(CrmLeadConversion::getLeadId, id));
        if (existing != null) {
            return conversionResult(existing, true);
        }
        if (!LeadStatus.QUALIFIED.name().equals(lead.getStatus())) {
            throw new BusinessException(409, "只有已合格线索可以转换");
        }
        if (!request.getVersion().equals(lead.getVersion())) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        CrmCustomer customer = resolveCustomer(lead, request);
        CrmContact contact = resolveContact(lead, customer, request);
        CrmOpportunity opportunity = request.isCreateOpportunity()
                ? createOpportunity(lead, customer, contact, request) : null;
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<CrmLead> update = versionedLead(id, request.getVersion())
                .set(CrmLead::getStatus, LeadStatus.CONVERTED.name()).set(CrmLead::getConvertedTime, now)
                .set(CrmLead::getDisqualifyReason, null);
        audit(update); requireUpdated(leadMapper.update(null, update));
        activityMapper.update(null, new LambdaUpdateWrapper<CrmActivity>()
                .eq(CrmActivity::getRootType, "LEAD").eq(CrmActivity::getRootId, id)
                .set(CrmActivity::getRootType, "CUSTOMER").set(CrmActivity::getRootId, customer.getId())
                .set(CrmActivity::getOwnerUserId, customer.getOwnerUserId())
                .set(CrmActivity::getOwnerUnitId, customer.getOwnerUnitId())
                .set(CrmActivity::getUpdateTime, now)
                .set(CrmActivity::getUpdateBy, CrmTenantContext.require().username())
                .setSql("version = version + 1"));
        CrmLeadConversion conversion = new CrmLeadConversion();
        conversion.setTenantId(CrmTenantContext.requireTenantId()); conversion.setLeadId(id); conversion.setCustomerId(customer.getId());
        conversion.setContactId(contact.getId()); conversion.setOpportunityId(opportunity == null ? null : opportunity.getId());
        conversion.setConvertedByUserId(CrmTenantContext.require().userId()); conversion.setConvertedTime(now);
        CrmAuditSupport.created(conversion); conversionMapper.insert(conversion);
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leadId", id); payload.put("customerId", customer.getId()); payload.put("contactId", contact.getId());
        payload.put("opportunityId", opportunity == null ? null : opportunity.getId());
        DomainEventEnvelope envelope = DomainEventEnvelope.builder().eventId(eventId)
                .eventType("crm.lead.converted.v1").occurredAt(now).tenantId(CrmTenantContext.requireTenantId())
                .producer("omni-crm").aggregateType("LEAD").aggregateId(id)
                .aggregateVersion(request.getVersion() + 1).actorUserId(CrmTenantContext.require().userId())
                .payload(payload).build();
        reliableMessageRelay.send("crm-domain-out-0", envelope, CrmTenantContext.requireTenantId(), eventId);
        log.info("线索转换完成：tenantId={}, leadId={}, customerId={}, eventId={}",
                CrmTenantContext.requireTenantId(), id, customer.getId(), eventId);
        return conversionResult(conversion, false);
    }

    private CrmCustomer resolveCustomer(CrmLead lead, CrmRequests.ConvertLeadRequest request) {
        if ("LINK".equalsIgnoreCase(request.getCustomerMode())) {
            if (request.getCustomerId() == null) throw new BusinessException(400, "关联客户 ID 不能为空");
            CrmCustomer customer = customerMapper.selectVisibleForUpdate(request.getCustomerId());
            if (customer == null) throw new BusinessException(404, "客户不存在");
            return customer;
        }
        if (!"CREATE".equalsIgnoreCase(request.getCustomerMode())) throw new BusinessException(400, "客户转换模式无效");
        CrmCustomer customer = new CrmCustomer();
        customer.setTenantId(lead.getTenantId()); customer.setCustomerNo("TMP-" + UUID.randomUUID());
        String name = hasText(request.getCustomerName()) ? request.getCustomerName() : lead.getCompanyName();
        if (!hasText(name)) throw new BusinessException(400, "新建客户名称不能为空");
        customer.setName(name); customer.setNormalizedName(normalizeName(name)); customer.setSourceCode(lead.getSourceCode());
        customer.setIndustryCode(lead.getIndustryCode()); customer.setPhone(lead.getPhone()); customer.setEmail(lead.getEmail());
        customer.setRegion(lead.getRegion()); customer.setAddress(lead.getAddress()); customer.setStatus("POTENTIAL");
        customer.setOwnerUserId(lead.getOwnerUserId()); customer.setOwnerUnitId(lead.getOwnerUnitId());
        customer.setVersion(0); customer.setDeleted(0); CrmAuditSupport.created(customer); customerMapper.insert(customer);
        String number = "C" + customer.getTenantId() + "-" + customer.getId();
        customerMapper.update(null, new LambdaUpdateWrapper<CrmCustomer>().eq(CrmCustomer::getId, customer.getId())
                .set(CrmCustomer::getCustomerNo, number)); customer.setCustomerNo(number);
        return customer;
    }

    private CrmContact resolveContact(CrmLead lead, CrmCustomer customer, CrmRequests.ConvertLeadRequest request) {
        if ("LINK".equalsIgnoreCase(request.getContactMode())) {
            if (request.getContactId() == null) throw new BusinessException(400, "关联联系人 ID 不能为空");
            CrmContact contact = accessGuard.requireContact(request.getContactId());
            if (!customer.getId().equals(contact.getCustomerId())) throw new BusinessException(400, "联系人不属于目标客户");
            return contact;
        }
        if (!"CREATE".equalsIgnoreCase(request.getContactMode())) throw new BusinessException(400, "联系人转换模式无效");
        contactMapper.clearPrimaryByCustomer(customer.getId(), LocalDateTime.now(),
                CrmTenantContext.require().username());
        CrmContact contact = new CrmContact();
        contact.setTenantId(lead.getTenantId()); contact.setCustomerId(customer.getId());
        contact.setName(hasText(request.getContactName()) ? request.getContactName() : lead.getFullName());
        contact.setJobTitle(lead.getJobTitle()); contact.setMobile(hasText(request.getContactMobile())
                ? request.getContactMobile() : lead.getMobile());
        contact.setEmail(hasText(request.getContactEmail()) ? request.getContactEmail() : lead.getEmail());
        contact.setPhone(lead.getPhone()); contact.setPrimaryFlag(1); contact.setStatus(1);
        contact.setOwnerUserId(customer.getOwnerUserId()); contact.setOwnerUnitId(customer.getOwnerUnitId());
        contact.setVersion(0); contact.setDeleted(0); CrmAuditSupport.created(contact); contactMapper.insert(contact);
        return contact;
    }

    private CrmOpportunity createOpportunity(CrmLead lead, CrmCustomer customer, CrmContact contact,
                                             CrmRequests.ConvertLeadRequest request) {
        Long pipelineId = tenantInitializer.ensureInitialized();
        CrmPipelineStage stage = stageMapper.selectOne(new LambdaQueryWrapper<CrmPipelineStage>()
                .eq(CrmPipelineStage::getPipelineId, pipelineId).eq(CrmPipelineStage::getStageCode, "DISCOVERY"));
        if (stage == null) throw new BusinessException(500, "默认商机阶段未初始化");
        CrmOpportunity opportunity = new CrmOpportunity();
        opportunity.setTenantId(lead.getTenantId()); opportunity.setOpportunityNo("TMP-" + UUID.randomUUID());
        opportunity.setName(hasText(request.getOpportunityName()) ? request.getOpportunityName() : customer.getName() + " 商机");
        opportunity.setCustomerId(customer.getId()); opportunity.setPrimaryContactId(contact.getId()); opportunity.setSourceLeadId(lead.getId());
        opportunity.setPipelineId(pipelineId); opportunity.setStageId(stage.getId()); opportunity.setStatus("OPEN");
        opportunity.setAmount(request.getAmount() == null ? BigDecimal.ZERO : request.getAmount());
        opportunity.setCurrencyCode(tenantInitializer.currencyCode());
        opportunity.setProbability(stage.getProbability()); opportunity.setExpectedCloseDate(request.getExpectedCloseDate());
        opportunity.setStageChangeTime(LocalDateTime.now()); opportunity.setOwnerUserId(lead.getOwnerUserId());
        opportunity.setOwnerUnitId(lead.getOwnerUnitId()); opportunity.setVersion(0); opportunity.setDeleted(0);
        CrmAuditSupport.created(opportunity); opportunityMapper.insert(opportunity);
        String number = "O" + opportunity.getTenantId() + "-" + opportunity.getId();
        opportunityMapper.update(null, new LambdaUpdateWrapper<CrmOpportunity>().eq(CrmOpportunity::getId, opportunity.getId())
                .set(CrmOpportunity::getOpportunityNo, number)); opportunity.setOpportunityNo(number);
        CrmOpportunityStageHistory history = new CrmOpportunityStageHistory();
        history.setTenantId(lead.getTenantId());
        history.setOpportunityId(opportunity.getId());
        history.setToStageId(stage.getId());
        history.setToStatus("OPEN");
        history.setChangeReason("CONVERSION_CREATE");
        history.setChangedByUserId(CrmTenantContext.require().userId());
        history.setChangedTime(opportunity.getStageChangeTime());
        CrmAuditSupport.created(history);
        opportunityHistoryMapper.insert(history);
        return opportunity;
    }

    private CrmViews.LeadVO changeStatus(Long id, Integer version, LeadStatus status, String reason) {
        LambdaUpdateWrapper<CrmLead> update = versionedLead(id, version).set(CrmLead::getStatus, status.name())
                .set(CrmLead::getDisqualifyReason, reason);
        audit(update); requireUpdated(leadMapper.update(null, update)); return get(id);
    }

    private CrmViews.DuplicateCandidateVO duplicate(CrmLead lead, CrmRequests.LeadDuplicateRequest request) {
        CrmViews.DuplicateCandidateVO vo = new CrmViews.DuplicateCandidateVO();
        vo.setId(lead.getId()); vo.setNumber(lead.getLeadNo()); vo.setName(lead.getCompanyName());
        if (hasText(request.getMobile()) && request.getMobile().equals(lead.getMobile())) { vo.setMatchedBy("MOBILE"); vo.setMaskedContact(PiiMasker.phone(lead.getMobile())); }
        else if (hasText(request.getEmail()) && request.getEmail().equalsIgnoreCase(lead.getEmail())) { vo.setMatchedBy("EMAIL"); vo.setMaskedContact(PiiMasker.email(lead.getEmail())); }
        else { vo.setMatchedBy("COMPANY_NAME"); vo.setMaskedContact(null); }
        return vo;
    }

    private void appendOwnerLog(String type, Long id, Long oldUser, Long oldUnit, CrmOwnerResolver.Owner owner,
                                String operation, String reason) {
        CrmOwnerChangeLog log = new CrmOwnerChangeLog(); log.setTenantId(CrmTenantContext.requireTenantId());
        log.setEntityType(type); log.setEntityId(id); log.setOldOwnerUserId(oldUser); log.setOldOwnerUnitId(oldUnit);
        log.setNewOwnerUserId(owner.userId()); log.setNewOwnerUnitId(owner.unitId()); log.setOperationType(operation);
        log.setReason(reason); log.setOperatorUserId(CrmTenantContext.require().userId()); log.setOperatedTime(LocalDateTime.now());
        CrmAuditSupport.created(log); ownerLogMapper.insert(log);
    }

    private void publishLeadAssignmentEvent(CrmLead lead, CrmOwnerResolver.Owner owner, int aggregateVersion) {
        publishLeadEvent("crm.lead.assigned.v1", lead, aggregateVersion,
                Map.of("leadId", lead.getId(), "oldOwnerUserId", lead.getOwnerUserId(),
                        "oldOwnerUnitId", lead.getOwnerUnitId(), "newOwnerUserId", owner.userId(),
                        "newOwnerUnitId", owner.unitId()));
    }

    private void publishLeadEvent(String eventType, CrmLead lead, int aggregateVersion,
                                  Map<String, Object> payload) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .tenantId(CrmTenantContext.requireTenantId())
                .producer("omni-crm")
                .aggregateType("LEAD")
                .aggregateId(lead.getId())
                .aggregateVersion(aggregateVersion)
                .actorUserId(CrmTenantContext.require().userId())
                .payload(payload)
                .build();
        reliableMessageRelay.send("crm-domain-out-0", envelope,
                CrmTenantContext.requireTenantId(), eventId);
    }

    private CrmViews.ConversionResultVO conversionResult(CrmLeadConversion conversion, boolean replay) {
        CrmViews.ConversionResultVO vo = new CrmViews.ConversionResultVO(); vo.setConversionId(conversion.getId());
        vo.setLeadId(conversion.getLeadId()); vo.setCustomerId(conversion.getCustomerId()); vo.setContactId(conversion.getContactId());
        vo.setOpportunityId(conversion.getOpportunityId()); vo.setConvertedTime(conversion.getConvertedTime()); vo.setIdempotentReplay(replay);
        return vo;
    }

    private LambdaUpdateWrapper<CrmLead> versionedLead(Long id, Integer version) {
        return new LambdaUpdateWrapper<CrmLead>().eq(CrmLead::getId, id).eq(CrmLead::getVersion, version)
                .eq(CrmLead::getDeleted, 0).setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<CrmLead> update) {
        update.set(CrmLead::getUpdateTime, LocalDateTime.now())
                .set(CrmLead::getUpdateBy, CrmTenantContext.require().username());
    }

    private <T> void setIf(LambdaUpdateWrapper<CrmLead> update, T value,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<CrmLead, T> column) {
        if (value != null) update.set(column, value);
    }

    private void requireUpdated(int rows) {
        if (rows != 1) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
    }

    private String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
