package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.crm.domain.ActivityStatus;
import com.omni.crm.domain.CrmStateMachine;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViewAssembler;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.dto.DomainEventEnvelope;
import com.omni.crm.entity.CrmActivity;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.crm.service.ActivityService;
import com.omni.crm.service.support.CrmAuditSupport;
import com.omni.crm.service.support.CrmOwnerResolver;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import com.omni.crm.service.support.CrmOwnerEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CRM 跟进活动应用服务实现。 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final CrmActivityMapper activityMapper;
    private final CrmLeadMapper leadMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmContactMapper contactMapper;
    private final CrmRecordAccessGuard accessGuard;
    private final ReliableMessageRelay reliableMessageRelay;
    private final CrmOwnerEnricher ownerEnricher;

    /** {@inheritDoc} */
    @Override
    public PageResult<CrmViews.ActivityVO> list(CrmRequests.ActivityQuery query) {
        Page<CrmActivity> result = activityMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                queryWrapper(query).orderByDesc(CrmActivity::getPlannedStartTime).orderByDesc(CrmActivity::getId));
        List<CrmViews.ActivityVO> records = result.getRecords().stream().map(CrmViewAssembler::activity).toList();
        enrichRootNames(records);
        ownerEnricher.enrich(records);
        ownerEnricher.enrichPerformedBy(records);
        return new PageResult<>(records,
                result.getTotal(), result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.ActivityVO> timeline(String rootType, Long rootId, int limit) {
        accessGuard.requireRootOwner(rootType, rootId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<CrmViews.ActivityVO> records = activityMapper.selectList(new LambdaQueryWrapper<CrmActivity>()
                        .eq(CrmActivity::getRootType, rootType.toUpperCase()).eq(CrmActivity::getRootId, rootId)
                        .orderByDesc(CrmActivity::getCreateTime).last("LIMIT " + safeLimit))
                .stream().map(CrmViewAssembler::activity).toList();
        enrichRootNames(records);
        ownerEnricher.enrich(records);
        ownerEnricher.enrichPerformedBy(records);
        return records;
    }

    /** {@inheritDoc} */
    @Override
    public CrmViews.ActivityVO get(Long id) {
        CrmViews.ActivityVO vo = ownerEnricher.enrichOne(CrmViewAssembler.activity(
                accessGuard.requireActivity(id), CrmViewAssembler.canViewPii()));
        enrichRootNames(List.of(vo));
        ownerEnricher.enrichPerformedBy(List.of(vo));
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ActivityVO create(CrmRequests.CreateActivityRequest request) {
        String rootType = normalizeRootType(request.getRootType());
        CrmOwnerResolver.Owner owner = accessGuard.requireRootOwner(rootType, request.getRootId());
        validateContact(rootType, request.getRootId(), request.getContactId());
        ActivityStatus status = request.getStatus() == null ? ActivityStatus.PLANNED : parseStatus(request.getStatus());
        if (status == ActivityStatus.COMPLETED && request.getCompletedTime() == null) {
            throw new BusinessException(400, "直接创建已完成活动必须填写完成时间");
        }
        if (status == ActivityStatus.CANCELLED) throw new BusinessException(400, "不能直接创建已取消活动");
        CrmActivity entity = new CrmActivity(); entity.setTenantId(ServiceIdentityContext.requireTenantId());
        entity.setRootType(rootType); entity.setRootId(request.getRootId()); entity.setContactId(request.getContactId());
        entity.setActivityType(request.getActivityType()); entity.setSubject(request.getSubject()); entity.setContent(request.getContent());
        entity.setStatus(status.name()); entity.setPlannedStartTime(request.getPlannedStartTime());
        entity.setPlannedEndTime(request.getPlannedEndTime()); entity.setCompletedTime(request.getCompletedTime());
        entity.setNextActionTime(request.getNextActionTime()); entity.setPerformedByUserId(
                status == ActivityStatus.COMPLETED ? ServiceIdentityContext.require().userId() : null);
        entity.setOwnerUserId(owner.userId()); entity.setOwnerUnitId(owner.unitId()); entity.setVersion(0); entity.setDeleted(0);
        CrmAuditSupport.created(entity); activityMapper.insert(entity);
        if (status == ActivityStatus.COMPLETED) {
            touchCompletedRoot(entity, request.getCompletedTime());
        }
        refreshRootFollowup(entity.getRootType(), entity.getRootId());
        if (status == ActivityStatus.COMPLETED) {
            publishCompletedEvent(entity, request.getCompletedTime(), entity.getVersion());
        }
        return get(entity.getId());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ActivityVO update(Long id, CrmRequests.UpdateActivityRequest request) {
        CrmActivity current = accessGuard.requireActivity(id);
        if (!ActivityStatus.PLANNED.name().equals(current.getStatus())) {
            throw new BusinessException(409, "只有已计划活动可以普通修改");
        }
        validateContact(current.getRootType(), current.getRootId(), request.getContactId());
        LambdaUpdateWrapper<CrmActivity> update = versioned(id, request.getVersion());
        setIf(update, request.getActivityType(), CrmActivity::getActivityType); setIf(update, request.getSubject(), CrmActivity::getSubject);
        setIf(update, request.getContent(), CrmActivity::getContent); setIf(update, request.getContactId(), CrmActivity::getContactId);
        setIf(update, request.getPlannedStartTime(), CrmActivity::getPlannedStartTime);
        setIf(update, request.getPlannedEndTime(), CrmActivity::getPlannedEndTime);
        setIf(update, request.getNextActionTime(), CrmActivity::getNextActionTime);
        audit(update); requireUpdated(activityMapper.update(null, update));
        refreshRootFollowup(current.getRootType(), current.getRootId());
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        CrmActivity current = accessGuard.requireActivity(id);
        if (ActivityStatus.COMPLETED.name().equals(current.getStatus())) throw new BusinessException(409, "已完成活动不可删除");
        LambdaUpdateWrapper<CrmActivity> update = versioned(id, version).set(CrmActivity::getDeleted, 1);
        audit(update); requireUpdated(activityMapper.update(null, update));
        refreshRootFollowup(current.getRootType(), current.getRootId());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ActivityVO complete(Long id, CrmRequests.CompleteActivityRequest request) {
        CrmActivity current = accessGuard.requireActivity(id);
        CrmStateMachine.requireActivityTransition(parseStatus(current.getStatus()), ActivityStatus.COMPLETED);
        LocalDateTime completed = request.getCompletedTime() == null ? LocalDateTime.now() : request.getCompletedTime();
        LambdaUpdateWrapper<CrmActivity> update = versioned(id, request.getVersion())
                .set(CrmActivity::getStatus, ActivityStatus.COMPLETED.name()).set(CrmActivity::getCompletedTime, completed)
                .set(CrmActivity::getPerformedByUserId, ServiceIdentityContext.require().userId())
                .set(CrmActivity::getNextActionTime, request.getNextActionTime());
        audit(update); requireUpdated(activityMapper.update(null, update));
        touchCompletedRoot(current, completed);
        refreshRootFollowup(current.getRootType(), current.getRootId());
        publishCompletedEvent(current, completed, request.getVersion() + 1);
        return get(id);
    }

    private void publishCompletedEvent(CrmActivity activity, LocalDateTime completed, int aggregateVersion) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope event = DomainEventEnvelope.builder().eventId(eventId).eventType("crm.activity.completed.v1")
                .occurredAt(completed).tenantId(ServiceIdentityContext.requireTenantId()).producer("omni-crm")
                .aggregateType("ACTIVITY").aggregateId(activity.getId()).aggregateVersion(aggregateVersion)
                .actorUserId(ServiceIdentityContext.require().userId())
                .payload(Map.of("activityId", activity.getId(), "rootType", activity.getRootType(),
                        "rootId", activity.getRootId())).build();
        reliableMessageRelay.send("crm-domain-out-0", event, ServiceIdentityContext.requireTenantId(), eventId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ActivityVO cancel(Long id, CrmRequests.CancelActivityRequest request) {
        CrmActivity current = accessGuard.requireActivity(id);
        CrmStateMachine.requireActivityTransition(parseStatus(current.getStatus()), ActivityStatus.CANCELLED);
        LambdaUpdateWrapper<CrmActivity> update = versioned(id, request.getVersion())
                .set(CrmActivity::getStatus, ActivityStatus.CANCELLED.name());
        audit(update); requireUpdated(activityMapper.update(null, update));
        refreshRootFollowup(current.getRootType(), current.getRootId());
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CrmViews.ActivityVO reschedule(Long id, CrmRequests.RescheduleActivityRequest request) {
        CrmActivity current = accessGuard.requireActivity(id);
        CrmStateMachine.requireActivityTransition(parseStatus(current.getStatus()), ActivityStatus.PLANNED);
        LambdaUpdateWrapper<CrmActivity> update = versioned(id, request.getVersion())
                .set(CrmActivity::getStatus, ActivityStatus.PLANNED.name())
                .set(CrmActivity::getPlannedStartTime, request.getPlannedStartTime())
                .set(CrmActivity::getPlannedEndTime, request.getPlannedEndTime())
                .set(CrmActivity::getCompletedTime, null).set(CrmActivity::getPerformedByUserId, null)
                .set(CrmActivity::getNextActionTime, null);
        audit(update); requireUpdated(activityMapper.update(null, update));
        refreshRootFollowup(current.getRootType(), current.getRootId());
        return get(id);
    }

    private LambdaQueryWrapper<CrmActivity> queryWrapper(CrmRequests.ActivityQuery query) {
        return new LambdaQueryWrapper<CrmActivity>()
                .eq(hasText(query.getRootType()), CrmActivity::getRootType, query.getRootType())
                .eq(query.getRootId() != null, CrmActivity::getRootId, query.getRootId())
                .eq(hasText(query.getStatus()), CrmActivity::getStatus, query.getStatus())
                .eq(query.getOwnerUserId() != null, CrmActivity::getOwnerUserId, query.getOwnerUserId())
                .ge(query.getFromTime() != null, CrmActivity::getPlannedStartTime, query.getFromTime())
                .le(query.getToTime() != null, CrmActivity::getPlannedStartTime, query.getToTime());
    }

    private void validateContact(String rootType, Long rootId, Long contactId) {
        if (contactId == null) return;
        CrmContact contact = accessGuard.requireContact(contactId);
        Long customerId = switch (rootType) {
            case "CUSTOMER" -> rootId;
            case "OPPORTUNITY" -> accessGuard.requireOpportunity(rootId).getCustomerId();
            default -> null;
        };
        if (customerId == null || !customerId.equals(contact.getCustomerId())) {
            throw new BusinessException(400, "联系人与活动访问根不匹配");
        }
    }

    private void touchCompletedRoot(CrmActivity activity, LocalDateTime activityTime) {
        if ("LEAD".equals(activity.getRootType())) {
            LambdaUpdateWrapper<CrmLead> update = new LambdaUpdateWrapper<CrmLead>().eq(CrmLead::getId, activity.getRootId())
                    .set(CrmLead::getLastActivityTime, activityTime)
                    .set(CrmLead::getUpdateTime, LocalDateTime.now()).setSql("version = version + 1");
            CrmLead lead = accessGuard.requireLead(activity.getRootId());
            if ("NEW".equals(lead.getStatus())) update.set(CrmLead::getStatus, "FOLLOWING");
            leadMapper.update(null, update);
        } else if ("CUSTOMER".equals(activity.getRootType())) {
            customerMapper.update(null, new LambdaUpdateWrapper<CrmCustomer>().eq(CrmCustomer::getId, activity.getRootId())
                    .set(CrmCustomer::getLastActivityTime, activityTime)
                    .set(CrmCustomer::getUpdateTime, LocalDateTime.now()).setSql("version = version + 1"));
        }
    }

    private void refreshRootFollowup(String rootType, Long rootId) {
        LocalDateTime plannedTime = activityMapper.selectEarliestPlannedTime(rootType, rootId);
        LocalDateTime nextActionTime = activityMapper.selectLatestCompletedNextActionTime(rootType, rootId);
        LocalDateTime nextFollowupTime = earlier(plannedTime, nextActionTime);
        LocalDateTime now = LocalDateTime.now();
        String operator = ServiceIdentityContext.require().username();
        if ("LEAD".equals(rootType)) {
            leadMapper.update(null, new LambdaUpdateWrapper<CrmLead>().eq(CrmLead::getId, rootId)
                    .set(CrmLead::getNextFollowupTime, nextFollowupTime).set(CrmLead::getUpdateTime, now)
                    .set(CrmLead::getUpdateBy, operator).setSql("version = version + 1"));
        } else if ("CUSTOMER".equals(rootType)) {
            customerMapper.update(null, new LambdaUpdateWrapper<CrmCustomer>().eq(CrmCustomer::getId, rootId)
                    .set(CrmCustomer::getNextFollowupTime, nextFollowupTime).set(CrmCustomer::getUpdateTime, now)
                    .set(CrmCustomer::getUpdateBy, operator).setSql("version = version + 1"));
        } else {
            opportunityMapper.update(null, new LambdaUpdateWrapper<CrmOpportunity>().eq(CrmOpportunity::getId, rootId)
                    .set(CrmOpportunity::getNextFollowupTime, nextFollowupTime).set(CrmOpportunity::getUpdateTime, now)
                    .set(CrmOpportunity::getUpdateBy, operator).setSql("version = version + 1"));
        }
    }

    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
    }

    private String normalizeRootType(String value) {
        String root = value == null ? "" : value.toUpperCase();
        if (!List.of("LEAD", "CUSTOMER", "OPPORTUNITY").contains(root)) throw new BusinessException(400, "活动访问根类型无效");
        return root;
    }

    private ActivityStatus parseStatus(String value) {
        try { return ActivityStatus.valueOf(value); }
        catch (IllegalArgumentException exception) { throw new BusinessException(400, "活动状态无效"); }
    }

    private LambdaUpdateWrapper<CrmActivity> versioned(Long id, Integer version) {
        return new LambdaUpdateWrapper<CrmActivity>().eq(CrmActivity::getId, id).eq(CrmActivity::getVersion, version)
                .eq(CrmActivity::getDeleted, 0).setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<CrmActivity> update) {
        update.set(CrmActivity::getUpdateTime, LocalDateTime.now())
                .set(CrmActivity::getUpdateBy, ServiceIdentityContext.require().username());
    }

    private <T> void setIf(LambdaUpdateWrapper<CrmActivity> update, T value,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<CrmActivity, T> column) {
        if (value != null) update.set(column, value);
    }

    private void requireUpdated(int rows) { if (rows != 1) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试"); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    /**
     * 批量填充关联对象名称，避免列表页显示裸 ID。
     * <p>按 rootType 分组查询对应的业务实体名称，避免 N+1。</p>
     */
    private void enrichRootNames(List<CrmViews.ActivityVO> records) {
        if (records == null || records.isEmpty()) return;

        // 按 rootType 分组收集 rootId
        Map<String, List<Long>> groupedIds = new java.util.HashMap<>();
        for (CrmViews.ActivityVO vo : records) {
            if (vo.getRootType() != null && vo.getRootId() != null) {
                groupedIds.computeIfAbsent(vo.getRootType(), k -> new java.util.ArrayList<>()).add(vo.getRootId());
            }
        }

        // 批量查询名称
        Map<Long, String> nameMap = new java.util.HashMap<>();
        if (groupedIds.containsKey("LEAD")) {
            leadMapper.selectNamesByIds(groupedIds.get("LEAD").stream().distinct().toList())
                    .forEach(e -> nameMap.put(e.getId(), e.getFullName()));
        }
        if (groupedIds.containsKey("CUSTOMER")) {
            customerMapper.selectNamesByIds(groupedIds.get("CUSTOMER").stream().distinct().toList())
                    .forEach(e -> nameMap.put(e.getId(), e.getName()));
        }
        if (groupedIds.containsKey("OPPORTUNITY")) {
            opportunityMapper.selectNamesByIds(groupedIds.get("OPPORTUNITY").stream().distinct().toList())
                    .forEach(e -> nameMap.put(e.getId(), e.getName()));
        }

        // 填充 rootName
        records.forEach(vo -> vo.setRootName(nameMap.get(vo.getRootId())));
    }
}
