package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.crm.domain.CrmStateMachine;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViewAssembler;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.dto.DomainEventEnvelope;
import com.omni.crm.entity.CrmActivity;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.entity.CrmOpportunityStageHistory;
import com.omni.crm.entity.CrmOwnerChangeLog;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmOpportunityStageHistoryMapper;
import com.omni.crm.mapper.CrmOwnerChangeLogMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.OpportunityService;
import com.omni.crm.service.support.CrmAuditSupport;
import com.omni.crm.service.support.CrmOwnerResolver;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import com.omni.crm.service.support.CrmOwnerEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CRM 商机应用服务实现。 */
@Service
@RequiredArgsConstructor
public class OpportunityServiceImpl implements OpportunityService {

    private final CrmOpportunityMapper opportunityMapper;
    private final CrmPipelineStageMapper stageMapper;
    private final CrmOpportunityStageHistoryMapper historyMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmContactMapper contactMapper;
    private final CrmActivityMapper activityMapper;
    private final CrmOwnerChangeLogMapper ownerLogMapper;
    private final CrmRecordAccessGuard accessGuard;
    private final CrmOwnerResolver ownerResolver;
    private final CrmTenantInitializer tenantInitializer;
    private final CrmOwnerEnricher ownerEnricher;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    public PageResult<CrmViews.OpportunityVO> list(CrmRequests.OpportunityQuery query) {
        tenantInitializer.ensureInitialized();
        Page<CrmOpportunity> result = opportunityMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper(query));
        return new PageResult<>(ownerEnricher.enrich(result.getRecords().stream().map(CrmViewAssembler::opportunity).toList()),
                result.getTotal(), result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.OpportunityBoardVO board(Long pipelineId, CrmRequests.OpportunityQuery query) {
        Long selectedPipeline = pipelineId == null ? tenantInitializer.ensureInitialized() : pipelineId;
        List<CrmPipelineStage> stages = stageMapper.selectList(new LambdaQueryWrapper<CrmPipelineStage>()
                .eq(CrmPipelineStage::getPipelineId, selectedPipeline).eq(CrmPipelineStage::getStatus, 1)
                .orderByAsc(CrmPipelineStage::getSort));
        if (stages.isEmpty()) throw new BusinessException(404, "销售管道不存在");
        LambdaQueryWrapper<CrmOpportunity> wrapper = wrapper(query).eq(CrmOpportunity::getPipelineId, selectedPipeline);
        List<CrmOpportunity> opportunities = opportunityMapper
                .selectPage(new Page<>(1, 100, false), wrapper).getRecords();
        List<CrmViews.OpportunityVO> opportunityViews = ownerEnricher.enrich(
                opportunities.stream().map(CrmViewAssembler::opportunity).toList());
        CrmViews.OpportunityBoardVO board = new CrmViews.OpportunityBoardVO();
        board.setStages(stages.stream().map(CrmViewAssembler::stage).toList());
        Map<Long, List<CrmViews.OpportunityVO>> grouped = new LinkedHashMap<>();
        stages.forEach(stage -> grouped.put(stage.getId(), opportunityViews.stream()
                .filter(item -> stage.getId().equals(item.getStageId())).toList()));
        board.setOpportunitiesByStage(grouped); return board;
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.OpportunityVO get(Long id) {
        return ownerEnricher.enrichOne(CrmViewAssembler.opportunity(accessGuard.requireOpportunity(id)));
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.StageHistoryVO> stageHistory(Long id) {
        accessGuard.requireOpportunity(id);
        return historyMapper.selectList(new LambdaQueryWrapper<CrmOpportunityStageHistory>()
                        .eq(CrmOpportunityStageHistory::getOpportunityId, id)
                        .orderByDesc(CrmOpportunityStageHistory::getChangedTime).orderByDesc(CrmOpportunityStageHistory::getId))
                .stream().map(CrmViewAssembler::history).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.OpportunityVO create(CrmRequests.CreateOpportunityRequest request) {
        Long defaultPipeline = tenantInitializer.ensureInitialized();
        CrmCustomer customer = accessGuard.requireCustomer(request.getCustomerId());
        if (request.getPrimaryContactId() != null) {
            CrmContact contact = accessGuard.requireContact(request.getPrimaryContactId());
            if (!customer.getId().equals(contact.getCustomerId())) throw new BusinessException(400, "联系人不属于目标客户");
        }
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCreate(request.getOwnerUserId(), "crm:opportunity:assign");
        Long pipelineId = request.getPipelineId() == null ? defaultPipeline : request.getPipelineId();
        CrmPipelineStage stage = resolveInitialStage(pipelineId, request.getStageId());
        CrmOpportunity entity = new CrmOpportunity(); entity.setTenantId(CrmTenantContext.requireTenantId());
        entity.setOpportunityNo("TMP-" + UUID.randomUUID()); entity.setName(request.getName()); entity.setCustomerId(request.getCustomerId());
        entity.setPrimaryContactId(request.getPrimaryContactId()); entity.setPipelineId(pipelineId); entity.setStageId(stage.getId());
        entity.setStatus(stage.getStageType()); entity.setAmount(request.getAmount());
        entity.setCurrencyCode(tenantInitializer.currencyCode());
        entity.setProbability(stage.getProbability()); entity.setExpectedCloseDate(request.getExpectedCloseDate());
        entity.setOwnerUserId(owner.userId()); entity.setOwnerUnitId(owner.unitId()); entity.setStageChangeTime(LocalDateTime.now());
        entity.setVersion(0); entity.setDeleted(0); CrmAuditSupport.created(entity); opportunityMapper.insert(entity);
        String number = "O" + entity.getTenantId() + "-" + entity.getId();
        opportunityMapper.update(null, new LambdaUpdateWrapper<CrmOpportunity>().eq(CrmOpportunity::getId, entity.getId())
                .set(CrmOpportunity::getOpportunityNo, number)); entity.setOpportunityNo(number);
        appendHistory(entity.getId(), null, stage.getId(), null, stage.getStageType(), "CREATE");
        return ownerEnricher.enrichOne(CrmViewAssembler.opportunity(entity));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.OpportunityVO update(Long id, CrmRequests.UpdateOpportunityRequest request) {
        CrmOpportunity current = accessGuard.requireOpportunity(id);
        if (!"OPEN".equals(current.getStatus())) throw new BusinessException(409, "已关闭商机不可普通修改");
        if (request.getPrimaryContactId() != null) {
            CrmContact contact = accessGuard.requireContact(request.getPrimaryContactId());
            if (!current.getCustomerId().equals(contact.getCustomerId())) throw new BusinessException(400, "联系人不属于商机客户");
        }
        LambdaUpdateWrapper<CrmOpportunity> update = versioned(id, request.getVersion());
        setIf(update, request.getName(), CrmOpportunity::getName); setIf(update, request.getPrimaryContactId(), CrmOpportunity::getPrimaryContactId);
        setIf(update, request.getAmount(), CrmOpportunity::getAmount); setIf(update, request.getExpectedCloseDate(), CrmOpportunity::getExpectedCloseDate);
        setIf(update, request.getNextFollowupTime(), CrmOpportunity::getNextFollowupTime);
        audit(update); requireUpdated(opportunityMapper.update(null, update)); return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        CrmOpportunity current = accessGuard.requireOpportunity(id);
        if (!"OPEN".equals(current.getStatus())) throw new BusinessException(409, "已关闭商机不可删除");
        LambdaUpdateWrapper<CrmOpportunity> update = versioned(id, version).set(CrmOpportunity::getDeleted, 1);
        audit(update); requireUpdated(opportunityMapper.update(null, update));
        activityMapper.softDeleteByRoot("OPPORTUNITY", id, LocalDateTime.now(),
                CrmTenantContext.require().username());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.OpportunityVO assign(Long id, CrmRequests.AssignRequest request) {
        CrmOpportunity current = accessGuard.requireOpportunity(id);
        CrmOwnerResolver.Owner owner = ownerResolver.resolveForCommand(request.getOwnerUserId());
        LambdaUpdateWrapper<CrmOpportunity> update = versioned(id, request.getVersion())
                .set(CrmOpportunity::getOwnerUserId, owner.userId()).set(CrmOpportunity::getOwnerUnitId, owner.unitId());
        audit(update); requireUpdated(opportunityMapper.update(null, update));
        activityMapper.update(null, new LambdaUpdateWrapper<CrmActivity>().eq(CrmActivity::getRootType, "OPPORTUNITY")
                .eq(CrmActivity::getRootId, id).set(CrmActivity::getOwnerUserId, owner.userId())
                .set(CrmActivity::getOwnerUnitId, owner.unitId()).setSql("version = version + 1"));
        CrmOwnerChangeLog log = new CrmOwnerChangeLog(); log.setTenantId(CrmTenantContext.requireTenantId());
        log.setEntityType("OPPORTUNITY"); log.setEntityId(id); log.setOldOwnerUserId(current.getOwnerUserId());
        log.setOldOwnerUnitId(current.getOwnerUnitId()); log.setNewOwnerUserId(owner.userId()); log.setNewOwnerUnitId(owner.unitId());
        log.setOperationType("ASSIGN"); log.setReason(request.getReason()); log.setOperatorUserId(CrmTenantContext.require().userId());
        log.setOperatedTime(LocalDateTime.now()); CrmAuditSupport.created(log); ownerLogMapper.insert(log);
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.OpportunityVO changeStage(Long id, CrmRequests.OpportunityStageRequest request) {
        CrmOpportunity current = opportunityMapper.selectVisibleForUpdate(id);
        if (current == null) throw new BusinessException(404, "商机不存在");
        if (!request.getVersion().equals(current.getVersion())) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        if (!"OPEN".equals(current.getStatus())) throw new BusinessException(409, "已关闭商机只能通过重开命令恢复");
        CrmPipelineStage from = stage(current.getStageId()); CrmPipelineStage target = stage(request.getStageId());
        if (from.getId().equals(target.getId())) throw new BusinessException(409, "商机已经处于目标阶段");
        if (!current.getPipelineId().equals(target.getPipelineId())) throw new BusinessException(400, "目标阶段不属于当前管道");
        CrmStateMachine.requireOpportunityOpenTransition(from.getSort(), target.getSort(), request.getReason());
        if ("LOST".equals(target.getStageType()) && !hasText(request.getLossReason())) {
            throw new BusinessException(400, "输单必须填写原因");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<CrmOpportunity> update = versioned(id, request.getVersion())
                .set(CrmOpportunity::getStageId, target.getId()).set(CrmOpportunity::getStatus, target.getStageType())
                .set(CrmOpportunity::getProbability, target.getProbability()).set(CrmOpportunity::getStageChangeTime, now)
                .set(CrmOpportunity::getLossReason, "LOST".equals(target.getStageType()) ? request.getLossReason() : null)
                .set(CrmOpportunity::getActualCloseTime, "OPEN".equals(target.getStageType()) ? null : now);
        audit(update); requireUpdated(opportunityMapper.update(null, update));
        appendHistory(id, from.getId(), target.getId(), current.getStatus(), target.getStageType(), request.getReason());
        if ("WON".equals(target.getStageType())) {
            customerMapper.activatePotentialAfterOpportunityWin(current.getCustomerId(), now,
                    CrmTenantContext.require().username());
        }
        emitStageEvent(current, target, request.getVersion() + 1, now);
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.OpportunityVO reopen(Long id, CrmRequests.VersionRequest request) {
        CrmOpportunity current = opportunityMapper.selectVisibleForUpdate(id);
        if (current == null) throw new BusinessException(404, "商机不存在");
        if ("OPEN".equals(current.getStatus())) throw new BusinessException(409, "商机当前已开放");
        CrmOpportunityStageHistory closing = historyMapper.selectOne(new LambdaQueryWrapper<CrmOpportunityStageHistory>()
                .eq(CrmOpportunityStageHistory::getOpportunityId, id)
                .in(CrmOpportunityStageHistory::getToStatus, List.of("WON", "LOST"))
                .orderByDesc(CrmOpportunityStageHistory::getChangedTime).last("LIMIT 1"));
        if (closing == null || closing.getFromStageId() == null) throw new BusinessException(409, "未找到可恢复的开放阶段");
        CrmPipelineStage openStage = stage(closing.getFromStageId());
        LambdaUpdateWrapper<CrmOpportunity> update = versioned(id, request.getVersion())
                .set(CrmOpportunity::getStageId, openStage.getId()).set(CrmOpportunity::getStatus, "OPEN")
                .set(CrmOpportunity::getProbability, openStage.getProbability()).set(CrmOpportunity::getActualCloseTime, null)
                .set(CrmOpportunity::getLossReason, null).set(CrmOpportunity::getStageChangeTime, LocalDateTime.now());
        audit(update); requireUpdated(opportunityMapper.update(null, update));
        appendHistory(id, current.getStageId(), openStage.getId(), current.getStatus(), "OPEN", "REOPEN");
        emitStageEvent(current, openStage, request.getVersion() + 1, LocalDateTime.now());
        return get(id);
    }

    private LambdaQueryWrapper<CrmOpportunity> wrapper(CrmRequests.OpportunityQuery query) {
        return new LambdaQueryWrapper<CrmOpportunity>()
                .and(hasText(query.getKeyword()), item -> item.like(CrmOpportunity::getName, query.getKeyword())
                        .or().like(CrmOpportunity::getOpportunityNo, query.getKeyword()))
                .eq(hasText(query.getStatus()), CrmOpportunity::getStatus, query.getStatus())
                .eq(query.getStageId() != null, CrmOpportunity::getStageId, query.getStageId())
                .eq(query.getOwnerUserId() != null, CrmOpportunity::getOwnerUserId, query.getOwnerUserId())
                .eq(query.getCustomerId() != null, CrmOpportunity::getCustomerId, query.getCustomerId())
                .orderByDesc(CrmOpportunity::getUpdateTime).orderByDesc(CrmOpportunity::getId);
    }

    private CrmPipelineStage resolveInitialStage(Long pipelineId, Long stageId) {
        CrmPipelineStage stage = stageId == null ? stageMapper.selectOne(new LambdaQueryWrapper<CrmPipelineStage>()
                .eq(CrmPipelineStage::getPipelineId, pipelineId).eq(CrmPipelineStage::getStageType, "OPEN")
                .eq(CrmPipelineStage::getStatus, 1).orderByAsc(CrmPipelineStage::getSort).last("LIMIT 1")) : stage(stageId);
        if (stage == null || !pipelineId.equals(stage.getPipelineId()) || !"OPEN".equals(stage.getStageType())) {
            throw new BusinessException(400, "初始阶段必须是目标管道的开放阶段");
        }
        return stage;
    }

    private CrmPipelineStage stage(Long id) {
        CrmPipelineStage stage = stageMapper.selectOne(new LambdaQueryWrapper<CrmPipelineStage>()
                .eq(CrmPipelineStage::getId, id).eq(CrmPipelineStage::getStatus, 1));
        if (stage == null) throw new BusinessException(404, "销售阶段不存在"); return stage;
    }

    private void appendHistory(Long id, Long fromStage, Long toStage, String fromStatus, String toStatus, String reason) {
        CrmOpportunityStageHistory history = new CrmOpportunityStageHistory();
        history.setTenantId(CrmTenantContext.requireTenantId()); history.setOpportunityId(id); history.setFromStageId(fromStage);
        history.setToStageId(toStage); history.setFromStatus(fromStatus); history.setToStatus(toStatus); history.setChangeReason(reason);
        history.setChangedByUserId(CrmTenantContext.require().userId()); history.setChangedTime(LocalDateTime.now());
        CrmAuditSupport.created(history); historyMapper.insert(history);
    }

    private void emitStageEvent(CrmOpportunity current, CrmPipelineStage target, Integer version, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString(); String suffix = target.getStageType().toLowerCase();
        String type = "OPEN".equals(target.getStageType()) ? "crm.opportunity.stage-changed.v1" : "crm.opportunity." + suffix + ".v1";
        DomainEventEnvelope envelope = DomainEventEnvelope.builder().eventId(eventId).eventType(type).occurredAt(now)
                .tenantId(CrmTenantContext.requireTenantId()).producer("omni-crm").aggregateType("OPPORTUNITY")
                .aggregateId(current.getId()).aggregateVersion(version).actorUserId(CrmTenantContext.require().userId())
                .payload(Map.of("opportunityId", current.getId(), "stageId", target.getId(), "status", target.getStageType())).build();
        reliableMessageRelay.send("crm-domain-out-0", envelope, CrmTenantContext.requireTenantId(), eventId);
    }

    private LambdaUpdateWrapper<CrmOpportunity> versioned(Long id, Integer version) {
        return new LambdaUpdateWrapper<CrmOpportunity>().eq(CrmOpportunity::getId, id)
                .eq(CrmOpportunity::getVersion, version).eq(CrmOpportunity::getDeleted, 0).setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<CrmOpportunity> update) {
        update.set(CrmOpportunity::getUpdateTime, LocalDateTime.now())
                .set(CrmOpportunity::getUpdateBy, CrmTenantContext.require().username());
    }

    private <T> void setIf(LambdaUpdateWrapper<CrmOpportunity> update, T value,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<CrmOpportunity, T> column) {
        if (value != null) update.set(column, value);
    }

    private void requireUpdated(int rows) { if (rows != 1) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试"); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
