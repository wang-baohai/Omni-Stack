package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.srm.domain.SrmStateMachine;
import com.omni.srm.domain.SrmStateMachine.SupplierStatus;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmEvaluation;
import com.omni.srm.entity.SrmRiskAssessment;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierBankAccount;
import com.omni.srm.entity.SrmSupplierContact;
import com.omni.srm.entity.SrmSupplierEnrollment;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.mapper.SrmEvaluationMapper;
import com.omni.srm.mapper.SrmRiskAssessmentMapper;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import com.omni.srm.mapper.SrmSupplierBankAccountMapper;
import com.omni.srm.mapper.SrmSupplierContactMapper;
import com.omni.srm.mapper.SrmSupplierEnrollmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.SupplierService;
import com.omni.srm.service.RiskService;
import com.omni.srm.service.support.SrmAuditSupport;
import com.omni.srm.service.support.SrmOwnerEnricher;
import com.omni.srm.service.support.SrmOwnerResolver;
import com.omni.srm.service.support.SrmPermissionScopeExecutor;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import com.omni.srm.service.support.SupplierNameNormalizer;
import com.omni.srm.service.support.SupplierRiskInitializer;
import com.omni.srm.workflow.SupplierWorkflowCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** SRM 供应商应用服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SrmSupplierMapper supplierMapper;
    private final SrmSupplierContactMapper contactMapper;
    private final SrmSupplierQualificationMapper qualificationMapper;
    private final SrmSupplierBankAccountMapper bankAccountMapper;
    private final SrmEvaluationMapper evaluationMapper;
    private final SrmRiskIndicatorMapper riskIndicatorMapper;
    private final SrmRiskAssessmentMapper riskAssessmentMapper;
    private final SrmSupplierEnrollmentMapper enrollmentMapper;
    private final SrmSupplierPortalUserMapper portalUserMapper;
    private final SrmRecordAccessGuard accessGuard;
    private final SrmOwnerResolver ownerResolver;
    private final SrmOwnerEnricher ownerEnricher;
    private final SrmPermissionScopeExecutor scopeExecutor;
    private final SupplierRiskInitializer riskInitializer;
    private final ReliableMessageRelay reliableMessageRelay;
    private final RiskService riskService;
    private final SupplierWorkflowCoordinator workflowCoordinator;

    /** {@inheritDoc} */
    @Override
    public PageResult<SrmViews.SupplierVO> list(SrmRequests.SupplierQuery query) {
        if (query.getPage() == null || query.getPage() < 1 || query.getSize() == null
                || query.getSize() < 1 || query.getSize() > 100) {
            throw new BusinessException(400, "分页参数无效，size 必须在 1 到 100 之间");
        }
        String normalizedKeyword = SupplierNameNormalizer.normalize(query.getName());
        Page<SrmSupplier> result = supplierMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<SrmSupplier>()
                        .and(hasText(query.getName()), wrapper -> wrapper.like(SrmSupplier::getName, query.getName())
                                .or().like(SrmSupplier::getNormalizedName, normalizedKeyword)
                                .or().like(SrmSupplier::getSupplierNo, query.getName()))
                        .eq(hasText(query.getStatus()), SrmSupplier::getStatus, query.getStatus())
                        .eq(hasText(query.getCategoryCode()), SrmSupplier::getCategoryCode, query.getCategoryCode())
                        .eq(hasText(query.getLevelCode()), SrmSupplier::getLevelCode, query.getLevelCode())
                        .eq(query.getOwnerUserId() != null, SrmSupplier::getOwnerUserId, query.getOwnerUserId())
                        .eq(query.getOwnerUnitId() != null, SrmSupplier::getOwnerUnitId, query.getOwnerUnitId())
                        .orderByDesc(SrmSupplier::getUpdateTime).orderByDesc(SrmSupplier::getId));
        List<SrmViews.SupplierVO> records = result.getRecords().stream()
                .map(entity -> SrmViewAssembler.supplier(entity, false)).toList();
        return new PageResult<>(ownerEnricher.enrich(records), result.getTotal(),
                result.getSize(), result.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.SupplierDetailVO get(Long id) {
        SrmSupplier supplier = accessGuard.requireSupplier(id);
        boolean pii = SrmViewAssembler.canViewPii();
        SrmViews.SupplierDetailVO detail = new SrmViews.SupplierDetailVO();
        copySupplierFields(supplier, detail, pii);
        detail.setContacts(scopeExecutor.executeIfGranted("srm:contact:list", () -> contactMapper.selectList(
                new LambdaQueryWrapper<SrmSupplierContact>()
                        .eq(SrmSupplierContact::getSupplierId, id)
                        .orderByDesc(SrmSupplierContact::getPrimaryFlag).orderByAsc(SrmSupplierContact::getId)).stream()
                .map(entity -> SrmViewAssembler.contact(entity, pii)).toList(), List.of()));
        detail.setQualifications(scopeExecutor.executeIfGranted("srm:qualification:list",
                () -> qualificationMapper.selectList(new LambdaQueryWrapper<SrmSupplierQualification>()
                        .eq(SrmSupplierQualification::getSupplierId, id)
                        .orderByAsc(SrmSupplierQualification::getId)).stream()
                .map(SrmViewAssembler::qualification).toList(), List.of()));
        detail.setBankAccounts(scopeExecutor.executeIfGranted("srm:bank-account:list",
                () -> bankAccountMapper.selectList(new LambdaQueryWrapper<SrmSupplierBankAccount>()
                        .eq(SrmSupplierBankAccount::getSupplierId, id)
                        .orderByDesc(SrmSupplierBankAccount::getPrimaryFlag)
                        .orderByAsc(SrmSupplierBankAccount::getId)).stream()
                .map(entity -> SrmViewAssembler.bankAccount(entity, pii)).toList(), List.of()));
        ownerEnricher.enrichOne(detail);
        return detail;
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.SupplierOverviewVO overview(Long id) {
        SrmSupplier supplier = accessGuard.requireSupplier(id);
        boolean pii = SrmViewAssembler.canViewPii();
        SrmViews.SupplierOverviewVO overview = new SrmViews.SupplierOverviewVO();
        copySupplierFields(supplier, overview, pii);
        overview.setContacts(scopeExecutor.executeIfGranted("srm:contact:list",
                () -> contactMapper.selectList(new LambdaQueryWrapper<SrmSupplierContact>()
                        .eq(SrmSupplierContact::getSupplierId, id)
                        .orderByDesc(SrmSupplierContact::getPrimaryFlag)
                        .orderByAsc(SrmSupplierContact::getId)).stream()
                .map(entity -> SrmViewAssembler.contact(entity, pii)).toList(), List.of()));
        overview.setQualifications(scopeExecutor.executeIfGranted("srm:qualification:list",
                () -> qualificationMapper.selectList(new LambdaQueryWrapper<SrmSupplierQualification>()
                        .eq(SrmSupplierQualification::getSupplierId, id)
                        .orderByAsc(SrmSupplierQualification::getId)).stream()
                .map(SrmViewAssembler::qualification).toList(), List.of()));
        overview.setBankAccounts(scopeExecutor.executeIfGranted("srm:bank-account:list",
                () -> bankAccountMapper.selectList(new LambdaQueryWrapper<SrmSupplierBankAccount>()
                        .eq(SrmSupplierBankAccount::getSupplierId, id)
                        .orderByDesc(SrmSupplierBankAccount::getPrimaryFlag)
                        .orderByAsc(SrmSupplierBankAccount::getId)).stream()
                .map(entity -> SrmViewAssembler.bankAccount(entity, pii)).toList(), List.of()));
        overview.setRecentEvaluations(scopeExecutor.executeIfGranted("srm:evaluation:list",
                () -> evaluationMapper.selectList(new LambdaQueryWrapper<SrmEvaluation>()
                        .eq(SrmEvaluation::getSupplierId, id)
                        .orderByDesc(SrmEvaluation::getEvaluationTime).last("LIMIT 5")).stream()
                .map(SrmViewAssembler::evaluation).toList(), List.of()));
        overview.setRiskIndicators(scopeExecutor.executeIfGranted("srm:risk:list",
                () -> riskIndicatorMapper.selectList(new LambdaQueryWrapper<SrmRiskIndicator>()
                        .eq(SrmRiskIndicator::getSupplierId, id)
                        .orderByDesc(SrmRiskIndicator::getAssessmentTime)).stream()
                .map(SrmViewAssembler::riskIndicator).toList(), List.of()));
        overview.setLatestRiskAssessment(scopeExecutor.executeIfGranted("srm:risk:list", () -> {
            SrmRiskAssessment latest = riskAssessmentMapper.selectOne(
                    new LambdaQueryWrapper<SrmRiskAssessment>()
                            .eq(SrmRiskAssessment::getSupplierId, id)
                            .orderByDesc(SrmRiskAssessment::getAssessmentTime)
                            .last("LIMIT 1"));
            return latest != null ? SrmViewAssembler.riskAssessment(latest) : null;
        }, null));
        List<SrmViews.OwnedVO> owners = new ArrayList<>();
        owners.add(overview);
        overview.getRecentEvaluations().forEach(owners::add);
        ownerEnricher.enrich(owners);
        return overview;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.SupplierVO create(SrmRequests.CreateSupplierRequest request) {
        String name = request.getName().trim();
        String creditCode = normalizeCreditCode(request.getCreditCode());
        requireCreditCodeAvailable(creditCode, null);
        SrmOwnerResolver.Owner owner = ownerResolver.resolveForCreate(
                request.getOwnerUserId(), "srm:supplier:transfer");
        SrmSupplier supplier = new SrmSupplier();
        supplier.setTenantId(ServiceIdentityContext.requireTenantId());
        supplier.setSupplierNo("TMP-" + java.util.UUID.randomUUID());
        supplier.setName(name);
        supplier.setNormalizedName(SupplierNameNormalizer.normalize(name));
        supplier.setSupplierType(request.getSupplierType());
        supplier.setIndustryCode(request.getIndustryCode());
        supplier.setCreditCode(creditCode);
        supplier.setWebsite(request.getWebsite());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setRegion(request.getRegion());
        supplier.setAddress(request.getAddress());
        supplier.setCategoryCode(request.getCategoryCode());
        supplier.setLevelCode("QUALIFIED");
        supplier.setStatus(SupplierStatus.PENDING_REVIEW.name());
        supplier.setOwnerUserId(owner.userId());
        supplier.setOwnerUnitId(owner.unitId());
        supplier.setAssignedTime(LocalDateTime.now());
        supplier.setVersion(0);
        supplier.setDeleted(0);
        SrmAuditSupport.created(supplier);
        try {
            supplierMapper.insert(supplier);
        } catch (DuplicateKeyException exception) {
            throw duplicateCreditCode(exception);
        }
        String number = "S" + supplier.getTenantId() + "-" + supplier.getId();
        supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>().eq(SrmSupplier::getId, supplier.getId())
                .set(SrmSupplier::getSupplierNo, number));
        supplier.setSupplierNo(number);
        riskInitializer.initialize(supplier.getTenantId(), supplier.getId());
        sendSupplierEvent(supplier, "srm.supplier.registered.v1", null);
        prepareAndStartWorkflow(supplier);
        return ownerEnricher.enrichOne(SrmViewAssembler.supplier(supplier, SrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.SupplierVO update(Long id, SrmRequests.UpdateSupplierRequest request) {
        if (request.getOwnerUserId() != null) {
            throw new BusinessException(400, "普通更新不能修改负责人，请使用负责人转移接口");
        }
        SrmSupplier current = accessGuard.requireSupplier(id);
        LambdaUpdateWrapper<SrmSupplier> update = versioned(id, request.getVersion());
        String name = request.getName() == null ? null : request.getName().trim();
        String creditCode = request.getCreditCode() == null
                ? null : normalizeCreditCode(request.getCreditCode());
        if (request.getCreditCode() != null) {
            requireCreditCodeAvailable(creditCode, id);
        }
        if (request.getName() != null) {
            update.set(SrmSupplier::getName, name);
            update.set(SrmSupplier::getNormalizedName, SupplierNameNormalizer.normalize(name));
        }
        setIf(update, request.getSupplierType(), SrmSupplier::getSupplierType);
        setIf(update, request.getIndustryCode(), SrmSupplier::getIndustryCode);
        if (request.getCreditCode() != null) {
            update.set(SrmSupplier::getCreditCode, creditCode);
        }
        setIf(update, request.getWebsite(), SrmSupplier::getWebsite);
        setIf(update, request.getPhone(), SrmSupplier::getPhone);
        setIf(update, request.getEmail(), SrmSupplier::getEmail);
        setIf(update, request.getRegion(), SrmSupplier::getRegion);
        setIf(update, request.getAddress(), SrmSupplier::getAddress);
        setIf(update, request.getCategoryCode(), SrmSupplier::getCategoryCode);
        audit(update);
        try {
            requireUpdated(supplierMapper.update(null, update));
        } catch (DuplicateKeyException exception) {
            throw duplicateCreditCode(exception);
        }
        applyUpdateSnapshot(current, request, name, creditCode);
        return ownerEnricher.enrichOne(
                SrmViewAssembler.supplier(current, SrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.SupplierVO transferOwner(Long id, SrmRequests.TransferOwnerRequest request) {
        SrmSupplier current = accessGuard.requireSupplier(id);
        if (SrmStateMachine.parse(current.getStatus()) == SupplierStatus.ELIMINATED) {
            throw new BusinessException(409, "已淘汰供应商不能转移负责人");
        }
        if (!request.getVersion().equals(current.getVersion())) {
            throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
        }
        if (request.getOwnerUserId().equals(current.getOwnerUserId())) {
            return ownerEnricher.enrichOne(
                    SrmViewAssembler.supplier(current, SrmViewAssembler.canViewPii()));
        }

        SrmOwnerResolver.Owner owner = ownerResolver.resolveForTransfer(
                request.getOwnerUserId(), "srm:supplier:transfer");
        LocalDateTime assignedTime = LocalDateTime.now();
        LambdaUpdateWrapper<SrmSupplier> update = versioned(id, request.getVersion())
                .set(SrmSupplier::getOwnerUserId, owner.userId())
                .set(SrmSupplier::getOwnerUnitId, owner.unitId())
                .set(SrmSupplier::getAssignedTime, assignedTime);
        audit(update);
        requireUpdated(supplierMapper.update(null, update));
        syncEvaluationOwner(id, owner);
        current.setOwnerUserId(owner.userId());
        current.setOwnerUnitId(owner.unitId());
        current.setAssignedTime(assignedTime);
        current.setVersion(request.getVersion() + 1);
        return ownerEnricher.enrichOne(
                SrmViewAssembler.supplier(current, SrmViewAssembler.canViewPii()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        requireDeleteVersion(version);
        accessGuard.requireSupplier(id);
        SrmSupplier supplier = supplierMapper.selectVisibleForUpdate(id);
        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        requireDeletableSupplier(supplier);
        requireNoSupplierHistory(id);
        LocalDateTime now = LocalDateTime.now();
        String operator = ServiceIdentityContext.require().username();
        // 子资源权限继承自仍有效的 Supplier，因此必须先清理子资源，再删除聚合根。
        contactMapper.softDeleteBySupplier(id, now, operator);
        qualificationMapper.softDeleteBySupplier(id, now, operator);
        bankAccountMapper.softDeleteBySupplier(id, now, operator);
        riskIndicatorMapper.update(null, new LambdaUpdateWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getSupplierId, id)
                .eq(SrmRiskIndicator::getDeleted, 0)
                .set(SrmRiskIndicator::getDeleted, 1)
                .set(SrmRiskIndicator::getUpdateTime, now)
                .set(SrmRiskIndicator::getUpdateBy, operator)
                .setSql("version = version + 1"));
        LambdaUpdateWrapper<SrmSupplier> update = versioned(id, version).set(SrmSupplier::getDeleted, 1);
        audit(update);
        requireUpdated(supplierMapper.update(null, update));
    }

    private void requireDeletableSupplier(SrmSupplier supplier) {
        SupplierStatus status = SrmStateMachine.parse(supplier.getStatus());
        if (!Set.of(SupplierStatus.REGISTERING, SupplierStatus.REGISTERING_FAILED,
                SupplierStatus.PENDING_REVIEW, SupplierStatus.REJECTED).contains(status)) {
            throw new BusinessException(409, "已进入正式生命周期的供应商不能删除，请使用冻结、黑名单或淘汰命令");
        }
    }

    private void requireNoSupplierHistory(Long supplierId) {
        boolean hasEvaluation = evaluationMapper.selectCount(new LambdaQueryWrapper<SrmEvaluation>()
                .eq(SrmEvaluation::getSupplierId, supplierId)) > 0;
        boolean hasRiskAssessment = riskAssessmentMapper.selectCount(new LambdaQueryWrapper<SrmRiskAssessment>()
                .eq(SrmRiskAssessment::getSupplierId, supplierId)) > 0;
        boolean hasEnrollment = enrollmentMapper.selectCount(new LambdaQueryWrapper<SrmSupplierEnrollment>()
                .eq(SrmSupplierEnrollment::getSupplierId, supplierId)) > 0;
        boolean hasPortalUser = portalUserMapper.selectCount(new LambdaQueryWrapper<SrmSupplierPortalUser>()
                .eq(SrmSupplierPortalUser::getSupplierId, supplierId)) > 0;
        if (hasEvaluation || hasRiskAssessment || hasEnrollment || hasPortalUser) {
            throw new BusinessException(409, "供应商已有门户、评估或风险历史，不能删除");
        }
    }

    private void requireDeleteVersion(Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "删除版本号必须为非负整数");
        }
    }

    /** 被驳回后重新提交：REJECTED → APPROVING（启动新一轮工作流）。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO submit(Long id, SrmRequests.StatusRequest request) {
        SrmSupplier current = accessGuard.requireSupplier(id);
        if (SrmStateMachine.parse(current.getStatus()) != SupplierStatus.REJECTED) {
            throw new BusinessException(409, "仅审核驳回的供应商可由管理端重新提交");
        }
        SrmStateMachine.requireTransition(SupplierStatus.REJECTED, SupplierStatus.PENDING_REVIEW);
        LambdaUpdateWrapper<SrmSupplier> update = versioned(id, request.getVersion())
                .set(SrmSupplier::getStatus, SupplierStatus.PENDING_REVIEW.name());
        audit(update);
        requireUpdated(supplierMapper.update(null, update));
        current.setStatus(SupplierStatus.PENDING_REVIEW.name());
        current.setVersion(request.getVersion() + 1);
        prepareAndStartWorkflow(current);
        return getSimple(id);
    }

    /** 撤回审批流程：APPROVING → PENDING_REVIEW。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO withdraw(Long id, SrmRequests.StatusRequest request) {
        return terminateWorkflow(id, request, "withdraw");
    }

    /** 取消审批流程：APPROVING → PENDING_REVIEW。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO cancel(Long id, SrmRequests.StatusRequest request) {
        return terminateWorkflow(id, request, "cancel");
    }

    /** 冻结：APPROVED → SUSPENDED。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO suspend(Long id, SrmRequests.StatusRequest request) {
        return changeStatus(id, request.getVersion(), SupplierStatus.SUSPENDED, request.getReason());
    }

    /** 解冻恢复：SUSPENDED → APPROVED。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO resume(Long id, SrmRequests.StatusRequest request) {
        return changeStatus(id, request.getVersion(), SupplierStatus.APPROVED, request.getReason());
    }

    /** 从黑名单恢复：BLACKLISTED → APPROVED。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO restoreFromBlacklist(Long id, SrmRequests.StatusRequest request) {
        return changeStatus(id, request.getVersion(), SupplierStatus.APPROVED, request.getReason());
    }

    /** 加入黑名单。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO blacklist(Long id, SrmRequests.StatusRequest request) {
        return changeStatus(id, request.getVersion(), SupplierStatus.BLACKLISTED, request.getReason());
    }

    /** 淘汰。 */
    @Override
    @Transactional
    public SrmViews.SupplierVO eliminate(Long id, SrmRequests.StatusRequest request) {
        return changeStatus(id, request.getVersion(), SupplierStatus.ELIMINATED, request.getReason());
    }

    private SrmViews.SupplierVO changeStatus(Long id, Integer version, SupplierStatus target) {
        return changeStatus(id, version, target, null);
    }

    private SrmViews.SupplierVO changeStatus(Long id, Integer version, SupplierStatus target, String reason) {
        SrmSupplier current = accessGuard.requireSupplier(id);
        return changeStatus(current, id, version, target, reason);
    }

    private SrmViews.SupplierVO changeStatus(
            SrmSupplier current, Long id, Integer version, SupplierStatus target, String reason) {
        SupplierStatus previous = SrmStateMachine.parse(current.getStatus());
        SrmStateMachine.requireTransition(previous, target);
        LambdaUpdateWrapper<SrmSupplier> update = versioned(id, version).set(SrmSupplier::getStatus, target.name());
        audit(update);
        requireUpdated(supplierMapper.update(null, update));
        current.setStatus(target.name());
        current.setVersion(version + 1);
        sendSupplierStatusEvent(current, previous, target, reason);
        return getSimple(id);
    }

    private SrmViews.SupplierVO getSimple(Long id) {
        return ownerEnricher.enrichOne(SrmViewAssembler.supplier(
                accessGuard.requireSupplier(id), SrmViewAssembler.canViewPii()));
    }

    private LambdaUpdateWrapper<SrmSupplier> versioned(Long id, Integer version) {
        return new LambdaUpdateWrapper<SrmSupplier>().eq(SrmSupplier::getId, id)
                .eq(SrmSupplier::getVersion, version).eq(SrmSupplier::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<SrmSupplier> update) {
        update.set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplier::getUpdateBy, ServiceIdentityContext.require().username());
    }

    private <T> void setIf(LambdaUpdateWrapper<SrmSupplier> update, T value,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<SrmSupplier, T> column) {
        if (value != null) update.set(column, value);
    }

    private void requireUpdated(int rows) {
        if (rows != 1) throw new BusinessException(409, "记录已被其他用户修改，请刷新后重试");
    }

    /** 同步 DataScope 使用的可变 owner 权限快照；该字段不是不可变的评估业务快照。 */
    private void syncEvaluationOwner(Long supplierId, SrmOwnerResolver.Owner owner) {
        evaluationMapper.update(null, new LambdaUpdateWrapper<SrmEvaluation>()
                .eq(SrmEvaluation::getSupplierId, supplierId)
                .eq(SrmEvaluation::getDeleted, 0)
                .set(SrmEvaluation::getOwnerUserId, owner.userId())
                .set(SrmEvaluation::getOwnerUnitId, owner.unitId())
                .set(SrmEvaluation::getUpdateTime, LocalDateTime.now())
                .set(SrmEvaluation::getUpdateBy, ServiceIdentityContext.require().username())
                .setSql("version = version + 1"));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String normalizeCreditCode(String creditCode) {
        if (creditCode == null || creditCode.isBlank()) {
            return null;
        }
        return creditCode.trim().toUpperCase(Locale.ROOT);
    }

    private void requireCreditCodeAvailable(String creditCode, Long excludedSupplierId) {
        if (creditCode == null) {
            return;
        }
        Long count = supplierMapper.selectCount(new LambdaQueryWrapper<SrmSupplier>()
                .eq(SrmSupplier::getCreditCode, creditCode)
                .ne(excludedSupplierId != null, SrmSupplier::getId, excludedSupplierId));
        if (count != null && count > 0) {
            throw duplicateCreditCode(null);
        }
    }

    private BusinessException duplicateCreditCode(Throwable cause) {
        BusinessException exception = new BusinessException(409, "统一社会信用代码已存在");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private void applyUpdateSnapshot(
            SrmSupplier current, SrmRequests.UpdateSupplierRequest request, String name,
            String creditCode) {
        if (request.getName() != null) {
            current.setName(name);
            current.setNormalizedName(SupplierNameNormalizer.normalize(name));
        }
        if (request.getSupplierType() != null) current.setSupplierType(request.getSupplierType());
        if (request.getIndustryCode() != null) current.setIndustryCode(request.getIndustryCode());
        if (request.getCreditCode() != null) current.setCreditCode(creditCode);
        if (request.getWebsite() != null) current.setWebsite(request.getWebsite());
        if (request.getPhone() != null) current.setPhone(request.getPhone());
        if (request.getEmail() != null) current.setEmail(request.getEmail());
        if (request.getRegion() != null) current.setRegion(request.getRegion());
        if (request.getAddress() != null) current.setAddress(request.getAddress());
        if (request.getCategoryCode() != null) current.setCategoryCode(request.getCategoryCode());
        current.setVersion(request.getVersion() + 1);
    }

    private SrmViews.SupplierVO terminateWorkflow(
            Long id, SrmRequests.StatusRequest request, String action) {
        SrmSupplier current = accessGuard.requireSupplier(id);
        if (!SupplierStatus.APPROVING.name().equals(current.getStatus())) {
            throw new BusinessException(409, "仅审批中的供应商可以" + ("withdraw".equals(action) ? "撤回" : "取消"));
        }
        if (current.getProcessInstanceId() == null || current.getProcessInstanceId().isBlank()) {
            throw new BusinessException(409, "该供应商没有正在运行的审批流程");
        }
        workflowCoordinator.terminate(
                current.getTenantId(), current.getProcessInstanceId(),
                "withdraw".equals(action) ? "管理员撤回审批" : "管理员取消审批");
        SrmStateMachine.requireTransition(SupplierStatus.APPROVING, SupplierStatus.PENDING_REVIEW);
        LambdaUpdateWrapper<SrmSupplier> update = versioned(id, request.getVersion())
                .set(SrmSupplier::getStatus, SupplierStatus.PENDING_REVIEW.name())
                .set(SrmSupplier::getProcessInstanceId, null)
                .set(SrmSupplier::getWorkflowStartStatus, SrmStateMachine.START_NOT_STARTED);
        audit(update);
        requireUpdated(supplierMapper.update(null, update));
        return getSimple(id);
    }

    private void prepareAndStartWorkflow(SrmSupplier supplier) {
        workflowCoordinator.prepareAndStart(supplier);
    }

    private void sendSupplierStatusEvent(
            SrmSupplier supplier, SupplierStatus previous, SupplierStatus target, String reason) {
        String eventType = switch (target) {
            case APPROVED -> SupplierStatus.PENDING_REVIEW == previous ? "srm.supplier.approved.v1" : null;
            case REJECTED -> "srm.supplier.rejected.v1";
            case SUSPENDED -> "srm.supplier.suspended.v1";
            case BLACKLISTED -> "srm.supplier.blacklisted.v1";
            case ELIMINATED -> "srm.supplier.eliminated.v1";
            default -> null;
        };
        if (eventType != null) {
            sendSupplierEvent(supplier, eventType, reason);
        }
    }

    private void sendSupplierEvent(SrmSupplier supplier, String eventType, String reason) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplierId", supplier.getId());
        payload.put("status", supplier.getStatus());
        if (supplier.getLevelCode() != null) {
            payload.put("levelCode", supplier.getLevelCode());
        }
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .tenantId(supplier.getTenantId())
                .producer("omni-srm")
                .aggregateType("SUPPLIER")
                .aggregateId(supplier.getId())
                .aggregateVersion(supplier.getVersion())
                .actorUserId(ServiceIdentityContext.require().userId())
                .payload(Map.copyOf(payload))
                .build();
        reliableMessageRelay.send("srm-domain-out-0", envelope, supplier.getTenantId(), eventId);
    }

    /** 将供应商实体字段复制到 SupplierOverviewVO/DetailVO。 */
    private void copySupplierFields(SrmSupplier entity, SrmViews.SupplierVO target, boolean pii) {
        SrmViews.SupplierVO src = SrmViewAssembler.supplier(entity, pii);
        target.setId(src.getId());
        target.setSupplierNo(src.getSupplierNo());
        target.setName(src.getName());
        target.setSupplierType(src.getSupplierType());
        target.setIndustryCode(src.getIndustryCode());
        target.setCreditCode(src.getCreditCode());
        target.setWebsite(src.getWebsite());
        target.setPhone(src.getPhone());
        target.setEmail(src.getEmail());
        target.setRegion(src.getRegion());
        target.setAddress(src.getAddress());
        target.setCategoryCode(src.getCategoryCode());
        target.setLevelCode(src.getLevelCode());
        target.setStatus(src.getStatus());
        target.setAssignedTime(src.getAssignedTime());
        target.setLastEvaluationTime(src.getLastEvaluationTime());
        target.setVersion(src.getVersion());
        target.setCreateTime(src.getCreateTime());
        target.setCreateBy(src.getCreateBy());
        target.setOwnerUserId(src.getOwnerUserId());
        target.setOwnerUnitId(src.getOwnerUnitId());
    }
}
