package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.asset.consumer.ProcurementGoodsReceiptConsumer;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetDomainEvent;
import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstInboxEvent;
import com.omni.asset.mapper.AssetReceiptImportMapper;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstInboxEventMapper;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.ProcurementAssetImportService;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Procurement 收货资产化导入服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class ProcurementAssetImportServiceImpl implements ProcurementAssetImportService {

    private static final String CONSUMER_NAME = "asset-procurement-goods-receipt-v1";
    private static final String SOURCE_SERVICE = "omni-procurement";
    private static final String AGGREGATE_TYPE = "GOODS_RECEIPT";
    private static final String RECEIVED = "RECEIVED";
    private static final String PROCESSED = "PROCESSED";
    private static final String IGNORED = "IGNORED";
    private static final String DOMAIN_BINDING = "asset-domain-out-0";
    private static final String ASSET_CREATED_EVENT = "asset.created.v1";
    private static final String SYSTEM_USER = "procurement-event";
    private static final String BACKFILL_USER = "procurement-backfill";
    private static final String HISTORY_REMARK = "采购收货自动创建资产";
    private static final Set<String> QUALITY_STATUSES = Set.of("PASS", "FAIL", "PENDING");

    private final AstInboxEventMapper inboxMapper;
    private final AssetReceiptImportMapper importMapper;
    private final AstAssetHistoryMapper historyMapper;
    private final ReliableMessageRelay reliableMessageRelay;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProcurementAssetContracts.ImportResult importEvent(
            ProcurementAssetContracts.GoodsReceiptEvent event) {
        ImportBatch batch = validateEvent(event);
        Long tenantId = requireMatchingTenant(event.getTenantId());
        AstInboxEvent inbox = registerAndLock(event, batch.root());
        validateDuplicateIntent(inbox, event, batch.root());
        if (PROCESSED.equals(inbox.getStatus()) || IGNORED.equals(inbox.getStatus())) {
            return result(List.of(), 0, 0, true);
        }

        CreationSummary summary = createAssets(tenantId, batch, SYSTEM_USER);
        int ignoredLines = (int) batch.lines().stream().filter(line -> !line.eligible()).count();
        String inboxStatus = batch.lines().stream().anyMatch(ImportLine::eligible)
                ? PROCESSED : IGNORED;
        markInbox(inbox, inboxStatus);
        publishCreated(tenantId, event.getEventId(), batch.root(), summary.createdAssetIds());
        return result(summary.createdAssetIds(), summary.duplicateCount(), ignoredLines, false);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProcurementAssetContracts.ImportResult importCandidate(
            Long tenantId, ProcurementAssetContracts.AssetCandidate candidate) {
        requireMatchingTenant(tenantId);
        ImportBatch batch = validateCandidate(candidate);
        CreationSummary summary = createAssets(tenantId, batch, BACKFILL_USER);
        publishCreated(tenantId, candidate.getEventId(), batch.root(), summary.createdAssetIds());
        return result(summary.createdAssetIds(), summary.duplicateCount(), 0, false);
    }

    private ProcurementAssetContracts.ImportResult result(
            List<Long> createdIds, int duplicateCount, int ignoredLineCount, boolean replayed) {
        return ProcurementAssetContracts.ImportResult.builder()
                .createdAssetIds(List.copyOf(createdIds))
                .createdCount(createdIds.size())
                .duplicateCount(duplicateCount)
                .ignoredLineCount(ignoredLineCount)
                .replayed(replayed)
                .build();
    }

    private ImportBatch validateEvent(ProcurementAssetContracts.GoodsReceiptEvent event) {
        if (event == null
                || invalidText(event.getEventId(), 64)
                || !supportedEvent(event.getEventType())
                || event.getOccurredAt() == null
                || event.getTenantId() == null || event.getTenantId() <= 0
                || event.getPayload() == null) {
            throw new IllegalArgumentException("Procurement 收货事件缺少必需信封字段或版本不受支持");
        }
        ProcurementAssetContracts.GoodsReceiptPayload payload = event.getPayload();
        RootSource root = validateRoot(new RootSource(
                payload.getGoodsReceiptId(), payload.getGrNo(),
                payload.getPurchaseOrderId(), payload.getPoNo(),
                payload.getSupplierId(), payload.getSupplierNameSnapshot(),
                payload.getPurchaseDate(), payload.getCurrencyCode(),
                payload.getOwnerUserId(), payload.getOwnerUnitId()));
        if (payload.getLines() == null) {
            throw new IllegalArgumentException("Procurement 收货事件缺少行集合");
        }
        List<ImportLine> lines = payload.getLines().stream()
                .map(line -> validateLine(line, false)).toList();
        ensureUniqueLineIds(lines);
        return new ImportBatch(root, lines);
    }

    private ImportBatch validateCandidate(ProcurementAssetContracts.AssetCandidate candidate) {
        if (candidate == null || invalidText(candidate.getEventId(), 64)) {
            throw new IllegalArgumentException("历史资产候选缺少来源事件 ID");
        }
        RootSource root = validateRoot(new RootSource(
                candidate.getGoodsReceiptId(), candidate.getGrNo(),
                candidate.getPurchaseOrderId(), candidate.getPoNo(),
                candidate.getSupplierId(), candidate.getSupplierNameSnapshot(),
                candidate.getPurchaseDate(), candidate.getCurrencyCode(),
                candidate.getOwnerUserId(), candidate.getOwnerUnitId()));
        ProcurementAssetContracts.GoodsReceiptLine line =
                new ProcurementAssetContracts.GoodsReceiptLine();
        line.setGoodsReceiptLineId(candidate.getGoodsReceiptLineId());
        line.setPurchaseOrderLineId(candidate.getPurchaseOrderLineId());
        line.setMaterialId(candidate.getMaterialId());
        line.setMaterialCode(candidate.getMaterialCode());
        line.setMaterialNameSnapshot(candidate.getMaterialNameSnapshot());
        line.setCategoryCode(candidate.getCategoryCode());
        line.setUnit(candidate.getUnit());
        line.setReceivedQuantity(candidate.getReceivedQuantity());
        line.setQualityStatus(candidate.getQualityStatus());
        line.setAssetManaged(candidate.getAssetManaged());
        line.setAssetQuantity(candidate.getAssetQuantity());
        line.setUnitPrice(candidate.getUnitPrice());
        line.setTotalPrice(candidate.getTotalPrice());
        return new ImportBatch(root, List.of(validateLine(line, true)));
    }

    private RootSource validateRoot(RootSource root) {
        requirePositive(root.goodsReceiptId(), "收货单 ID");
        requireText(root.grNo(), "收货单号", 64);
        requirePositive(root.purchaseOrderId(), "采购订单 ID");
        requireText(root.poNo(), "采购订单号", 64);
        requirePositive(root.supplierId(), "供应商 ID");
        requireText(root.supplierNameSnapshot(), "供应商名称快照", 200);
        if (root.purchaseDate() == null) {
            throw new IllegalArgumentException("采购收货时间不能为空");
        }
        requireCurrency(root.currencyCode());
        requirePositive(root.ownerUserId(), "资产管理员用户 ID");
        requirePositive(root.ownerUnitId(), "资产管理部门 ID");
        return root;
    }

    private ImportLine validateLine(
            ProcurementAssetContracts.GoodsReceiptLine line, boolean requireEligible) {
        if (line == null) {
            throw new IllegalArgumentException("Procurement 收货事件行不能为空");
        }
        requirePositive(line.getGoodsReceiptLineId(), "收货行 ID");
        requirePositive(line.getPurchaseOrderLineId(), "采购订单行 ID");
        requirePositive(line.getMaterialId(), "物料 ID");
        requireText(line.getMaterialCode(), "物料编码快照", 64);
        requireText(line.getMaterialNameSnapshot(), "物料名称快照", 200);
        requireText(line.getCategoryCode(), "品类编码快照", 64);
        requireText(line.getUnit(), "计量单位快照", 32);
        requirePositiveDecimal(line.getReceivedQuantity(), "本次收货数量");
        if (!QUALITY_STATUSES.contains(line.getQualityStatus())) {
            throw new IllegalArgumentException("质检状态不受支持");
        }
        if (line.getAssetManaged() == null) {
            throw new IllegalArgumentException("资产化标志不能为空");
        }
        if (line.getAssetQuantity() == null || line.getAssetQuantity() < 0) {
            throw new IllegalArgumentException("资产数量不能为负数");
        }
        requirePositiveDecimal(line.getUnitPrice(), "采购单价");
        requirePositiveDecimal(line.getTotalPrice(), "采购行金额");
        BigDecimal expectedTotal = line.getUnitPrice().multiply(line.getReceivedQuantity());
        if (expectedTotal.compareTo(line.getTotalPrice()) != 0) {
            throw new IllegalArgumentException("采购行金额与数量、单价不一致");
        }
        boolean eligible = "PASS".equals(line.getQualityStatus())
                && Boolean.TRUE.equals(line.getAssetManaged());
        if (eligible) {
            long exactQuantity = positiveInteger(line.getReceivedQuantity());
            if (exactQuantity != line.getAssetQuantity()) {
                throw new IllegalArgumentException("资产数量与收货数量不一致");
            }
            if (exactQuantity > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("单行资产数量超过支持上限");
            }
        } else if (requireEligible) {
            throw new IllegalArgumentException("历史候选行不满足资产化条件");
        }
        return new ImportLine(
                line.getGoodsReceiptLineId(), line.getPurchaseOrderLineId(),
                line.getMaterialId(), line.getMaterialCode(),
                line.getMaterialNameSnapshot(), line.getCategoryCode(), line.getUnit(),
                line.getReceivedQuantity(), line.getQualityStatus(),
                line.getAssetManaged(), line.getAssetQuantity(), line.getUnitPrice(),
                line.getTotalPrice(), eligible);
    }

    private void ensureUniqueLineIds(List<ImportLine> lines) {
        long distinctCount = lines.stream().map(ImportLine::goodsReceiptLineId).distinct().count();
        if (distinctCount != lines.size()) {
            throw new IllegalArgumentException("Procurement 收货事件包含重复来源行");
        }
    }

    private CreationSummary createAssets(Long tenantId, ImportBatch batch, String auditUser) {
        List<Long> createdIds = new ArrayList<>();
        int duplicateCount = 0;
        for (ImportLine line : batch.lines()) {
            if (!line.eligible()) {
                continue;
            }
            int quantity = Math.toIntExact(line.assetQuantity());
            for (int sequence = 1; sequence <= quantity; sequence++) {
                AstAsset asset = buildAsset(tenantId, batch.root(), line, sequence, auditUser);
                int inserted = importMapper.insertIdempotent(asset);
                if (inserted < 0 || inserted > 2) {
                    throw new IllegalStateException("资产来源幂等写入返回了非法影响行数");
                }
                AstAsset persisted = importMapper.selectForUpdateBySource(
                        asset.getTenantId(), asset.getSourceGrLineId(),
                        asset.getSourceUnitSequence());
                if (persisted == null) {
                    throw new IllegalStateException("资产来源幂等写入后无法读取来源单位");
                }
                if (asset.getId().equals(persisted.getId())) {
                    appendCreationHistory(asset, auditUser);
                    createdIds.add(asset.getId());
                } else {
                    validateExistingIntent(asset, persisted);
                    duplicateCount++;
                }
            }
        }
        return new CreationSummary(List.copyOf(createdIds), duplicateCount);
    }

    private AstAsset buildAsset(
            Long tenantId, RootSource root, ImportLine line, int sequence, String auditUser) {
        LocalDateTime now = LocalDateTime.now();
        long assetId = IdWorker.getId();
        AstAsset asset = new AstAsset();
        asset.setId(assetId);
        asset.setTenantId(tenantId);
        asset.setAssetNo("AST-" + assetId);
        asset.setName(line.materialNameSnapshot());
        asset.setCategoryCode(line.categoryCode());
        asset.setSupplierId(root.supplierId());
        asset.setSupplierNameSnapshot(root.supplierNameSnapshot());
        asset.setSourcePoId(root.purchaseOrderId());
        asset.setSourceGrId(root.goodsReceiptId());
        asset.setSourceGrLineId(line.goodsReceiptLineId());
        asset.setSourceUnitSequence(sequence);
        asset.setSourcePoNo(root.poNo());
        asset.setSourceGrNo(root.grNo());
        asset.setPurchaseDate(root.purchaseDate().toLocalDate());
        asset.setPurchaseAmount(line.unitPrice().setScale(2, RoundingMode.HALF_UP));
        asset.setCurrencyCode(root.currencyCode());
        asset.setStatus(AssetStateMachine.IN_STOCK);
        asset.setRemark(HISTORY_REMARK);
        asset.setOwnerUserId(root.ownerUserId());
        asset.setOwnerUnitId(root.ownerUnitId());
        asset.setVersion(0);
        asset.setDeleted(0);
        asset.setCreateTime(now);
        asset.setUpdateTime(now);
        asset.setCreateBy(auditUser);
        asset.setUpdateBy(auditUser);
        return asset;
    }

    private void appendCreationHistory(AstAsset asset, String auditUser) {
        AstAssetHistory history = new AstAssetHistory();
        history.setTenantId(asset.getTenantId());
        history.setAssetId(asset.getId());
        history.setFromStatus(null);
        history.setToStatus(AssetStateMachine.IN_STOCK);
        history.setChangedByUserId(0L);
        history.setChangedTime(asset.getCreateTime());
        history.setRemark(HISTORY_REMARK);
        history.setCreateTime(asset.getCreateTime());
        history.setUpdateTime(asset.getCreateTime());
        history.setCreateBy(auditUser);
        history.setUpdateBy(auditUser);
        if (historyMapper.insert(history) != 1) {
            throw new IllegalStateException("写入资产创建历史失败");
        }
    }

    private void validateExistingIntent(AstAsset expected, AstAsset existing) {
        if (!Objects.equals(expected.getSourcePoId(), existing.getSourcePoId())
                || !Objects.equals(expected.getSourceGrId(), existing.getSourceGrId())
                || !Objects.equals(expected.getSourcePoNo(), existing.getSourcePoNo())
                || !Objects.equals(expected.getSourceGrNo(), existing.getSourceGrNo())) {
            throw new BusinessException(409, "采购来源单位已绑定不同资产意图");
        }
    }

    private AstInboxEvent registerAndLock(
            ProcurementAssetContracts.GoodsReceiptEvent event, RootSource root) {
        LocalDateTime now = LocalDateTime.now();
        AstInboxEvent candidate = new AstInboxEvent();
        candidate.setTenantId(event.getTenantId());
        candidate.setConsumerName(CONSUMER_NAME);
        candidate.setEventId(event.getEventId());
        candidate.setEventType(event.getEventType());
        candidate.setSourceService(SOURCE_SERVICE);
        candidate.setAggregateType(AGGREGATE_TYPE);
        candidate.setAggregateId(String.valueOf(root.goodsReceiptId()));
        candidate.setPayload(toJson(event));
        candidate.setStatus(RECEIVED);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        int inserted = inboxMapper.insertIgnore(candidate);
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("登记 Procurement 收货事件 Inbox 失败");
        }
        AstInboxEvent inbox = inboxMapper.selectForUpdate(CONSUMER_NAME, event.getEventId());
        if (inbox == null) {
            throw new IllegalStateException("无法锁定 Procurement 收货事件 Inbox");
        }
        return inbox;
    }

    private void validateDuplicateIntent(
            AstInboxEvent inbox,
            ProcurementAssetContracts.GoodsReceiptEvent event,
            RootSource root) {
        if (!event.getTenantId().equals(inbox.getTenantId())
                || !CONSUMER_NAME.equals(inbox.getConsumerName())
                || !event.getEventType().equals(inbox.getEventType())
                || !SOURCE_SERVICE.equals(inbox.getSourceService())
                || !AGGREGATE_TYPE.equals(inbox.getAggregateType())
                || !String.valueOf(root.goodsReceiptId()).equals(inbox.getAggregateId())
                || !sameJson(inbox.getPayload(), toJson(event))) {
            throw new BusinessException(409, "同一收货事件 ID 绑定了不同业务意图");
        }
    }

    private void markInbox(AstInboxEvent inbox, String status) {
        LocalDateTime now = LocalDateTime.now();
        inbox.setStatus(status);
        inbox.setProcessedTime(now);
        inbox.setErrorMessage(null);
        inbox.setUpdateTime(now);
        int affected = inboxMapper.markProcessed(inbox);
        if (affected != 1) {
            throw new IllegalStateException("更新 Procurement 收货事件 Inbox 失败");
        }
    }

    private void publishCreated(
            Long tenantId, String sourceEventId, RootSource root, List<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceService", SOURCE_SERVICE);
        payload.put("sourceEventId", sourceEventId);
        payload.put("goodsReceiptId", root.goodsReceiptId());
        payload.put("createdAssetCount", assetIds.size());
        payload.put("assetIds", List.copyOf(assetIds));
        AssetDomainEvent event = AssetDomainEvent.builder()
                .eventId(eventId)
                .eventType(ASSET_CREATED_EVENT)
                .occurredAt(LocalDateTime.now())
                .tenantId(tenantId)
                .producer("omni-asset")
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, tenantId, eventId);
    }

    private Long requireMatchingTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户 ID 必须为正整数");
        }
        Long contextTenantId = AssetTenantContext.requireTenantId();
        if (!tenantId.equals(contextTenantId)) {
            throw new BusinessException(403, "采购资产导入与当前租户上下文不一致");
        }
        return contextTenantId;
    }

    private boolean supportedEvent(String eventType) {
        return ProcurementGoodsReceiptConsumer.CONFIRMED_EVENT.equals(eventType)
                || ProcurementGoodsReceiptConsumer.QUALITY_PASSED_EVENT.equals(eventType);
    }

    private boolean sameJson(String left, String right) {
        try {
            JsonNode leftNode = objectMapper.readTree(left);
            JsonNode rightNode = objectMapper.readTree(right);
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Procurement 收货事件 Inbox JSON 无效", exception);
        }
    }

    private String toJson(ProcurementAssetContracts.GoodsReceiptEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Procurement 收货事件无法序列化", exception);
        }
    }

    private long positiveInteger(BigDecimal value) {
        try {
            long result = value.longValueExact();
            if (result <= 0) {
                throw new ArithmeticException("not positive");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("资产化收货数量必须为正整数", exception);
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + "必须为正整数");
        }
    }

    private void requirePositiveDecimal(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + "必须大于 0");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (invalidText(value, maxLength)) {
            throw new IllegalArgumentException(field + "不能为空且长度不能超过 " + maxLength);
        }
    }

    private void requireCurrency(String currencyCode) {
        if (invalidText(currencyCode, 3) || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("币种必须为 ISO 4217 三位大写编码");
        }
    }

    private boolean invalidText(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    /** Procurement 收货聚合根可信快照。 */
    private record RootSource(
            Long goodsReceiptId,
            String grNo,
            Long purchaseOrderId,
            String poNo,
            Long supplierId,
            String supplierNameSnapshot,
            LocalDateTime purchaseDate,
            String currencyCode,
            Long ownerUserId,
            Long ownerUnitId) {
    }

    /** 统一资产候选行。 */
    private record ImportLine(
            Long goodsReceiptLineId,
            Long purchaseOrderLineId,
            Long materialId,
            String materialCode,
            String materialNameSnapshot,
            String categoryCode,
            String unit,
            BigDecimal receivedQuantity,
            String qualityStatus,
            Boolean assetManaged,
            Long assetQuantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            boolean eligible) {
    }

    /** 一次导入批次。 */
    private record ImportBatch(RootSource root, List<ImportLine> lines) {
    }

    /** 资产创建汇总。 */
    private record CreationSummary(List<Long> createdAssetIds, int duplicateCount) {
    }
}
