package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetDomainEvent;
import com.omni.asset.dto.AssetRequests;
import com.omni.asset.dto.AssetViewAssembler;
import com.omni.asset.dto.AssetViews;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.AssetService;
import com.omni.asset.service.support.AssetAuditSupport;
import com.omni.asset.service.support.AssetIdentityGuard;
import com.omni.asset.service.support.AssetRecordAccessGuard;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 资产台账服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private static final String DOMAIN_BINDING = "asset-domain-out-0";
    private static final String ALLOCATED_EVENT = "asset.allocated.v1";
    private static final String RETURNED_EVENT = "asset.returned.v1";
    private static final String PRODUCER = "omni-asset";

    private final AstAssetMapper assetMapper;
    private final AstAssetHistoryMapper historyMapper;
    private final AssetRecordAccessGuard accessGuard;
    private final AssetIdentityGuard identityGuard;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<AssetViews.AssetVO> page(AssetRequests.AssetQuery query) {
        Long tenantId = AssetTenantContext.requireTenantId();
        LambdaQueryWrapper<AstAsset> wrapper = baseQuery(tenantId);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(AstAsset::getAssetNo, keyword)
                    .or().like(AstAsset::getName, keyword)
                    .or().like(AstAsset::getBrand, keyword)
                    .or().like(AstAsset::getModel, keyword));
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(AstAsset::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (trimToNull(query.getCategoryCode()) != null) {
            wrapper.eq(AstAsset::getCategoryCode, query.getCategoryCode().trim().toUpperCase());
        }
        if (query.getOwnerUnitId() != null) {
            wrapper.eq(AstAsset::getOwnerUnitId, query.getOwnerUnitId());
        }
        if (trimToNull(query.getLocationCode()) != null) {
            wrapper.eq(AstAsset::getLocationCode, query.getLocationCode().trim());
        }
        wrapper.orderByDesc(AstAsset::getCreateTime).orderByDesc(AstAsset::getId);
        return pageAssets(query.getPage(), query.getSize(), wrapper);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AssetViews.AssetVO get(Long id) {
        return AssetViewAssembler.asset(requireVisible(AssetTenantContext.requireTenantId(), id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<AssetViews.AssetVO> pageMine(AssetRequests.MyAssetQuery query) {
        AssetTenantContext.RequestIdentity identity = AssetTenantContext.require();
        LambdaQueryWrapper<AstAsset> wrapper = baseQuery(identity.tenantId())
                .eq(AstAsset::getCurrentUserId, identity.userId());
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(AstAsset::getAssetNo, keyword)
                    .or().like(AstAsset::getName, keyword));
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(AstAsset::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (trimToNull(query.getCategoryCode()) != null) {
            wrapper.eq(AstAsset::getCategoryCode, query.getCategoryCode().trim().toUpperCase());
        }
        wrapper.orderByDesc(AstAsset::getAllocatedTime).orderByDesc(AstAsset::getId);
        return pageAssets(query.getPage(), query.getSize(), wrapper);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO create(AssetRequests.CreateAssetRequest request) {
        AssetTenantContext.RequestIdentity identity = AssetTenantContext.require();
        accessGuard.requireOwnerWritable(request.getOwnerUserId(), request.getOwnerUnitId());
        identityGuard.requireActiveUserInUnit(
                identity.tenantId(), request.getOwnerUserId(), request.getOwnerUnitId());
        AstAsset asset = new AstAsset();
        asset.setTenantId(identity.tenantId());
        asset.setAssetNo("TMP-" + UUID.randomUUID().toString().replace("-", ""));
        applyCreateFields(asset, request);
        asset.setStatus(AssetStateMachine.IN_STOCK);
        asset.setOwnerUserId(request.getOwnerUserId());
        asset.setOwnerUnitId(request.getOwnerUnitId());
        asset.setVersion(0);
        asset.setDeleted(0);
        AssetAuditSupport.created(asset);
        try {
            assetMapper.insert(asset);
            if (asset.getId() == null) {
                throw new BusinessException(500, "资产主键生成失败");
            }
            String assetNo = generateAssetNo(asset.getId());
            accessGuard.requireAffected(
                    assetMapper.setAssetNoAfterInsert(asset.getTenantId(), asset.getId(), assetNo),
                    "资产编号生成冲突");
            asset.setAssetNo(assetNo);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "资产编号已存在");
        }
        appendHistory(asset, null, AssetStateMachine.IN_STOCK,
                mergeRemark("手工入库", request.getRemark()));
        return AssetViewAssembler.asset(asset);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO update(Long id, AssetRequests.UpdateAssetRequest request) {
        Long tenantId = AssetTenantContext.requireTenantId();
        accessGuard.requireOwnerWritable(request.getOwnerUserId(), request.getOwnerUnitId());
        identityGuard.requireActiveUserInUnit(
                tenantId, request.getOwnerUserId(), request.getOwnerUnitId());
        AstAsset current = requireLocked(tenantId, id);
        requireVersion(current, request.getVersion());
        requireMetadataMutable(current);
        BigDecimal purchaseAmount = normalizeAmount(request.getPurchaseAmount());
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AstAsset> update = versioned(current, request.getVersion())
                .isNull(AstAsset::getActiveOperationId)
                .set(AstAsset::getName, requiredText(request.getName(), "资产名称"))
                .set(AstAsset::getCategoryCode, normalizeCode(request.getCategoryCode(), "品类编码"))
                .set(AstAsset::getSpecification, trimToNull(request.getSpecification()))
                .set(AstAsset::getBrand, trimToNull(request.getBrand()))
                .set(AstAsset::getModel, trimToNull(request.getModel()))
                .set(AstAsset::getSupplierId, request.getSupplierId())
                .set(AstAsset::getSupplierNameSnapshot, trimToNull(request.getSupplierNameSnapshot()))
                .set(AstAsset::getPurchaseDate, request.getPurchaseDate())
                .set(AstAsset::getPurchaseAmount, purchaseAmount)
                .set(AstAsset::getCurrencyCode, normalizeCurrency(request.getCurrencyCode()))
                .set(AstAsset::getWarrantyExpiryDate, request.getWarrantyExpiryDate())
                .set(AstAsset::getExpectedLifeYears, request.getExpectedLifeYears())
                .set(AstAsset::getRemark, trimToNull(request.getRemark()))
                .set(AstAsset::getOwnerUserId, request.getOwnerUserId())
                .set(AstAsset::getOwnerUnitId, request.getOwnerUnitId());
        audit(update, now);
        accessGuard.requireAffected(assetMapper.update(null, update), "资产已被其他请求修改，请刷新后重试");
        applyUpdatedFields(current, request, purchaseAmount, now);
        appendHistory(current, current.getStatus(), current.getStatus(),
                mergeRemark("更新资产资料", request.getRemark()));
        return AssetViewAssembler.asset(current);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        Long tenantId = AssetTenantContext.requireTenantId();
        AstAsset current = requireLocked(tenantId, id);
        requireVersion(current, version);
        if (!AssetStateMachine.IN_STOCK.equals(current.getStatus())
                || current.getCurrentUserId() != null || current.getActiveOperationId() != null) {
            throw new BusinessException(409, "仅可删除未分配且无活动操作的在库资产");
        }
        if (current.getSourceGrLineId() != null) {
            throw new BusinessException(409, "采购验收入库资产不能删除");
        }
        long historyCount = historyMapper.selectCount(new LambdaQueryWrapper<AstAssetHistory>()
                .eq(AstAssetHistory::getTenantId, tenantId)
                .eq(AstAssetHistory::getAssetId, id));
        if (historyCount > 1) {
            throw new BusinessException(409, "已发生业务变更的资产不能删除");
        }
        appendHistory(current, current.getStatus(), current.getStatus(), "删除手工资产");
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AstAsset> update = versioned(current, version)
                .isNull(AstAsset::getActiveOperationId)
                .set(AstAsset::getDeleted, 1);
        audit(update, now);
        accessGuard.requireAffected(assetMapper.update(null, update), "资产已被其他请求修改，请刷新后重试");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<AssetViews.HistoryVO> history(Long id, AssetRequests.HistoryQuery query) {
        Long tenantId = AssetTenantContext.requireTenantId();
        requireVisible(tenantId, id);
        Page<AstAssetHistory> page = historyMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<AstAssetHistory>()
                        .eq(AstAssetHistory::getTenantId, tenantId)
                        .eq(AstAssetHistory::getAssetId, id)
                        .orderByDesc(AstAssetHistory::getChangedTime)
                        .orderByDesc(AstAssetHistory::getId));
        List<AssetViews.HistoryVO> records = page.getRecords().stream()
                .map(AssetViewAssembler::history)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO allocate(Long id, AssetRequests.AllocateRequest request) {
        Long tenantId = AssetTenantContext.requireTenantId();
        accessGuard.requireUnitWritable(request.getTargetUnitId());
        identityGuard.requireActiveUserInUnit(
                tenantId, request.getTargetUserId(), request.getTargetUnitId());
        AstAsset current = requireLocked(tenantId, id);
        requireVersion(current, request.getVersion());
        requireNoActiveOperation(current);
        AssetStateMachine.requireTransition(current.getStatus(), AssetStateMachine.ALLOCATED);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AstAsset> update = versioned(current, request.getVersion())
                .isNull(AstAsset::getActiveOperationId)
                .set(AstAsset::getStatus, AssetStateMachine.ALLOCATED)
                .set(AstAsset::getCurrentUserId, request.getTargetUserId())
                .set(AstAsset::getCurrentUnitId, request.getTargetUnitId())
                .set(AstAsset::getAllocatedTime, now);
        audit(update, now);
        accessGuard.requireAffected(assetMapper.update(null, update), "资产已被其他请求分配，请刷新后重试");
        String fromStatus = current.getStatus();
        current.setStatus(AssetStateMachine.ALLOCATED);
        current.setCurrentUserId(request.getTargetUserId());
        current.setCurrentUnitId(request.getTargetUnitId());
        current.setAllocatedTime(now);
        markUpdated(current, now);
        appendHistory(current, fromStatus, current.getStatus(), mergeRemark("分配资产", request.getRemark()));
        publish(current, ALLOCATED_EVENT, Map.of(
                "assetId", current.getId(),
                "assetNo", current.getAssetNo(),
                "targetUserId", request.getTargetUserId(),
                "targetUnitId", request.getTargetUnitId(),
                "status", current.getStatus()), now);
        return AssetViewAssembler.asset(current);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO accept(Long id, AssetRequests.VersionCommandRequest request) {
        Long tenantId = AssetTenantContext.requireTenantId();
        AstAsset current = requireLocked(tenantId, id);
        accessGuard.requireAssignedToCurrentUser(current);
        requireVersion(current, request.getVersion());
        requireNoActiveOperation(current);
        return transition(current, request.getVersion(), AssetStateMachine.IN_USE,
                mergeRemark("确认领用", request.getRemark()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO returnAsset(Long id, AssetRequests.VersionCommandRequest request) {
        Long tenantId = AssetTenantContext.requireTenantId();
        AstAsset current = requireLocked(tenantId, id);
        accessGuard.requireAssignedToCurrentUser(current);
        requireVersion(current, request.getVersion());
        requireNoActiveOperation(current);
        AssetStateMachine.requireTransition(current.getStatus(), AssetStateMachine.IN_STOCK);
        Long previousUserId = current.getCurrentUserId();
        Long previousUnitId = current.getCurrentUnitId();
        String fromStatus = current.getStatus();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AstAsset> update = versioned(current, request.getVersion())
                .isNull(AstAsset::getActiveOperationId)
                .set(AstAsset::getStatus, AssetStateMachine.IN_STOCK)
                .set(AstAsset::getCurrentUserId, null)
                .set(AstAsset::getCurrentUnitId, null)
                .set(AstAsset::getAllocatedTime, null);
        audit(update, now);
        accessGuard.requireAffected(assetMapper.update(null, update), "资产已被其他请求退还，请刷新后重试");
        current.setStatus(AssetStateMachine.IN_STOCK);
        current.setCurrentUserId(null);
        current.setCurrentUnitId(null);
        current.setAllocatedTime(null);
        markUpdated(current, now);
        appendHistory(current, fromStatus, current.getStatus(), mergeRemark("退还资产", request.getRemark()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", current.getId());
        payload.put("assetNo", current.getAssetNo());
        payload.put("previousUserId", previousUserId);
        payload.put("previousUnitId", previousUnitId);
        payload.put("status", current.getStatus());
        publish(current, RETURNED_EVENT, payload, now);
        return AssetViewAssembler.asset(current);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO startMaintenance(Long id, AssetRequests.VersionCommandRequest request) {
        AstAsset current = requireLocked(AssetTenantContext.requireTenantId(), id);
        requireVersion(current, request.getVersion());
        requireNoActiveOperation(current);
        return transition(current, request.getVersion(), AssetStateMachine.MAINTENANCE,
                mergeRemark("送修", request.getRemark()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetViews.AssetVO completeMaintenance(Long id, AssetRequests.VersionCommandRequest request) {
        AstAsset current = requireLocked(AssetTenantContext.requireTenantId(), id);
        requireVersion(current, request.getVersion());
        requireNoActiveOperation(current);
        return transition(current, request.getVersion(), AssetStateMachine.IN_USE,
                mergeRemark("维修完成", request.getRemark()));
    }

    private AssetViews.AssetVO transition(AstAsset current, Integer version,
                                          String targetStatus, String remark) {
        AssetStateMachine.requireTransition(current.getStatus(), targetStatus);
        String fromStatus = current.getStatus();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AstAsset> update = versioned(current, version)
                .isNull(AstAsset::getActiveOperationId)
                .set(AstAsset::getStatus, targetStatus);
        audit(update, now);
        accessGuard.requireAffected(assetMapper.update(null, update),
                "资产状态已被其他请求修改，请刷新后重试");
        current.setStatus(targetStatus);
        markUpdated(current, now);
        appendHistory(current, fromStatus, targetStatus, remark);
        return AssetViewAssembler.asset(current);
    }

    private PageResult<AssetViews.AssetVO> pageAssets(
            int pageNumber, int pageSize, LambdaQueryWrapper<AstAsset> wrapper) {
        Page<AstAsset> page = assetMapper.selectPage(new Page<>(pageNumber, pageSize), wrapper);
        List<AssetViews.AssetVO> records = page.getRecords().stream()
                .map(AssetViewAssembler::asset)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    private LambdaQueryWrapper<AstAsset> baseQuery(Long tenantId) {
        return new LambdaQueryWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, tenantId)
                .eq(AstAsset::getDeleted, 0);
    }

    private AstAsset requireVisible(Long tenantId, Long id) {
        AstAsset asset = assetMapper.selectOne(baseQuery(tenantId).eq(AstAsset::getId, id));
        return accessGuard.requireVisible(asset, "资产不存在");
    }

    private AstAsset requireLocked(Long tenantId, Long id) {
        return accessGuard.requireVisible(assetMapper.selectForUpdate(tenantId, id), "资产不存在");
    }

    private void applyCreateFields(AstAsset asset, AssetRequests.CreateAssetRequest request) {
        asset.setName(requiredText(request.getName(), "资产名称"));
        asset.setCategoryCode(normalizeCode(request.getCategoryCode(), "品类编码"));
        asset.setSpecification(trimToNull(request.getSpecification()));
        asset.setBrand(trimToNull(request.getBrand()));
        asset.setModel(trimToNull(request.getModel()));
        asset.setSupplierId(request.getSupplierId());
        asset.setSupplierNameSnapshot(trimToNull(request.getSupplierNameSnapshot()));
        asset.setPurchaseDate(request.getPurchaseDate());
        asset.setPurchaseAmount(normalizeAmount(request.getPurchaseAmount()));
        asset.setCurrencyCode(normalizeCurrency(request.getCurrencyCode()));
        asset.setLocationCode(trimToNull(request.getLocationCode()));
        asset.setWarrantyExpiryDate(request.getWarrantyExpiryDate());
        asset.setExpectedLifeYears(request.getExpectedLifeYears());
        asset.setRemark(trimToNull(request.getRemark()));
    }

    private void applyUpdatedFields(AstAsset asset, AssetRequests.UpdateAssetRequest request,
                                    BigDecimal purchaseAmount, LocalDateTime now) {
        asset.setName(requiredText(request.getName(), "资产名称"));
        asset.setCategoryCode(normalizeCode(request.getCategoryCode(), "品类编码"));
        asset.setSpecification(trimToNull(request.getSpecification()));
        asset.setBrand(trimToNull(request.getBrand()));
        asset.setModel(trimToNull(request.getModel()));
        asset.setSupplierId(request.getSupplierId());
        asset.setSupplierNameSnapshot(trimToNull(request.getSupplierNameSnapshot()));
        asset.setPurchaseDate(request.getPurchaseDate());
        asset.setPurchaseAmount(purchaseAmount);
        asset.setCurrencyCode(normalizeCurrency(request.getCurrencyCode()));
        asset.setWarrantyExpiryDate(request.getWarrantyExpiryDate());
        asset.setExpectedLifeYears(request.getExpectedLifeYears());
        asset.setRemark(trimToNull(request.getRemark()));
        asset.setOwnerUserId(request.getOwnerUserId());
        asset.setOwnerUnitId(request.getOwnerUnitId());
        markUpdated(asset, now);
    }

    private void requireMetadataMutable(AstAsset asset) {
        AssetStateMachine.requireKnown(asset.getStatus());
        if (AssetStateMachine.isTerminal(asset.getStatus())
                || AssetStateMachine.TRANSFER.equals(asset.getStatus())
                || AssetStateMachine.DISPOSAL_PENDING.equals(asset.getStatus())
                || asset.getActiveOperationId() != null) {
            throw new BusinessException(409, "当前资产状态不允许修改基础资料");
        }
    }

    private void requireNoActiveOperation(AstAsset asset) {
        if (asset.getActiveOperationId() != null || trimToNull(asset.getActiveOperationType()) != null) {
            throw new BusinessException(409, "资产存在活动中的调拨或处置操作");
        }
    }

    private void requireVersion(AstAsset asset, Integer version) {
        if (version == null || version < 0 || !Objects.equals(asset.getVersion(), version)) {
            throw new BusinessException(409, "资产已被其他请求修改，请刷新后重试");
        }
    }

    private LambdaUpdateWrapper<AstAsset> versioned(AstAsset current, Integer version) {
        return new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, current.getTenantId())
                .eq(AstAsset::getId, current.getId())
                .eq(AstAsset::getVersion, version)
                .eq(AstAsset::getStatus, current.getStatus())
                .eq(AstAsset::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<AstAsset> update, LocalDateTime now) {
        update.set(AstAsset::getUpdateTime, now)
                .set(AstAsset::getUpdateBy, AssetAuditSupport.operator());
    }

    private void markUpdated(AstAsset asset, LocalDateTime now) {
        asset.setVersion(asset.getVersion() + 1);
        asset.setUpdateTime(now);
        asset.setUpdateBy(AssetAuditSupport.operator());
    }

    private void appendHistory(AstAsset asset, String fromStatus, String toStatus, String remark) {
        AstAssetHistory history = new AstAssetHistory();
        history.setTenantId(asset.getTenantId());
        history.setAssetId(asset.getId());
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedByUserId(AssetTenantContext.require().userId());
        history.setChangedTime(LocalDateTime.now());
        history.setRemark(trimToNull(remark));
        AssetAuditSupport.created(history);
        historyMapper.insert(history);
    }

    private void publish(AstAsset asset, String eventType,
                         Map<String, Object> payload, LocalDateTime occurredAt) {
        String eventId = UUID.randomUUID().toString();
        AssetDomainEvent event = AssetDomainEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .tenantId(asset.getTenantId())
                .producer(PRODUCER)
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, asset.getTenantId(), eventId);
    }

    private String generateAssetNo(Long id) {
        return "AST-" + id;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        if (amount.signum() < 0 || amount.precision() - amount.scale() > 16) {
            throw new BusinessException(400, "采购原值超出允许范围");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(400, "采购原值最多保留 2 位小数");
        }
    }

    private String normalizeCurrency(String value) {
        String normalized = requiredText(value, "币种编码").toUpperCase();
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BusinessException(400, "币种编码格式非法");
        }
        return normalized;
    }

    private String normalizeCode(String value, String fieldName) {
        return requiredText(value, fieldName).toUpperCase();
    }

    private String requiredText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String mergeRemark(String action, String remark) {
        String normalized = trimToNull(remark);
        return normalized == null ? action : action + "：" + normalized;
    }
}
