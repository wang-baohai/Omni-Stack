package com.omni.asset.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.asset.consumer.ProcurementGoodsReceiptConsumer;
import com.omni.asset.dto.AssetDomainEvent;
import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstInboxEvent;
import com.omni.asset.mapper.AssetReceiptImportMapper;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstInboxEventMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Procurement 收货资产化导入服务测试。
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class ProcurementAssetImportServiceImplTest {

    private static final Long TENANT_ID = 41L;

    @Mock private AstInboxEventMapper inboxMapper;
    @Mock private AssetReceiptImportMapper importMapper;
    @Mock private AstAssetHistoryMapper historyMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private ObjectMapper objectMapper;
    private ProcurementAssetImportServiceImpl service;

    /** 初始化测试服务与租户上下文。 */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ProcurementAssetImportServiceImpl(
                inboxMapper, importMapper, historyMapper,
                reliableMessageRelay, objectMapper);
        ServiceIdentityContext.set(new ServiceRequestIdentity(
                0L, TENANT_ID, "test-procurement-event"));
    }

    /** 清理 ThreadLocal，避免测试线程污染。 */
    @AfterEach
    void tearDown() {
        ServiceIdentityContext.clear();
    }

    /** 验证一行多单位会生成独立资产、历史与单个 Outbox 批事件。 */
    @Test
    void should_create_one_asset_per_unit_when_passed_asset_line_received() throws Exception {
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "event-create", new BigDecimal("3.000000"), true, "PASS", 3L);
        when(inboxMapper.selectForUpdate(anyString(), anyString()))
                .thenReturn(inbox(event, "RECEIVED"));
        when(inboxMapper.markProcessed(any(AstInboxEvent.class))).thenReturn(1);
        AtomicReference<AstAsset> insertedAsset = new AtomicReference<>();
        when(importMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            insertedAsset.set(invocation.getArgument(0));
            return 1;
        });
        when(importMapper.selectForUpdateBySource(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> insertedAsset.get());
        when(historyMapper.insert(any(AstAssetHistory.class))).thenReturn(1);

        ProcurementAssetContracts.ImportResult result = service.importEvent(event);

        assertThat(result.getCreatedCount()).isEqualTo(3);
        assertThat(result.getDuplicateCount()).isZero();
        assertThat(result.getIgnoredLineCount()).isZero();
        ArgumentCaptor<AstAsset> assetCaptor = ArgumentCaptor.forClass(AstAsset.class);
        verify(importMapper, times(3)).insertIdempotent(assetCaptor.capture());
        assertThat(assetCaptor.getAllValues())
                .extracting(AstAsset::getSourceUnitSequence)
                .containsExactly(1, 2, 3);
        assertThat(assetCaptor.getAllValues())
                .allSatisfy(asset -> {
                    assertThat(asset.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(asset.getOwnerUserId()).isEqualTo(701L);
                    assertThat(asset.getOwnerUnitId()).isEqualTo(801L);
                    assertThat(asset.getPurchaseAmount())
                            .isEqualByComparingTo("100.000000");
                    assertThat(asset.getAssetNo()).isEqualTo("AST-" + asset.getId());
                });
        verify(historyMapper, times(3)).insert(any(AstAssetHistory.class));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(reliableMessageRelay).send(
                eq("asset-domain-out-0"), eventCaptor.capture(), eq(TENANT_ID), anyString());
        assertThat(eventCaptor.getValue()).isInstanceOf(AssetDomainEvent.class);
        assertThat(((AssetDomainEvent) eventCaptor.getValue()).getEventType())
                .isEqualTo("asset.created.v1");
    }

    /** 验证已处理 eventId 重放不会再次触碰资产来源表。 */
    @Test
    void should_return_replayed_when_same_event_already_processed() throws Exception {
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "event-replay", BigDecimal.ONE, true, "PASS", 1L);
        when(inboxMapper.selectForUpdate(anyString(), anyString()))
                .thenReturn(inbox(event, "PROCESSED"));

        ProcurementAssetContracts.ImportResult result = service.importEvent(event);

        assertThat(result.isReplayed()).isTrue();
        assertThat(result.getCreatedCount()).isZero();
        verify(importMapper, never()).insertIdempotent(any());
        verify(reliableMessageRelay, never())
                .send(anyString(), any(), anyLong(), anyString());
    }

    /** 验证无效资产化行只被忽略，不会创建资产。 */
    @Test
    void should_ignore_line_when_quality_not_passed_or_not_asset_managed() throws Exception {
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "event-ignore", new BigDecimal("1.500000"), false, "PENDING", 0L);
        when(inboxMapper.selectForUpdate(anyString(), anyString()))
                .thenReturn(inbox(event, "RECEIVED"));
        when(inboxMapper.markProcessed(any(AstInboxEvent.class))).thenReturn(1);

        ProcurementAssetContracts.ImportResult result = service.importEvent(event);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getIgnoredLineCount()).isEqualTo(1);
        verify(importMapper, never()).insertIdempotent(any());
        verify(reliableMessageRelay, never())
                .send(anyString(), any(), anyLong(), anyString());
    }

    /** 验证缺少资产化标志时失败关闭，不登记成功 Inbox。 */
    @Test
    void should_fail_closed_when_asset_managed_flag_missing() {
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "event-invalid", BigDecimal.ONE, null, "PASS", 1L);

        assertThatThrownBy(() -> service.importEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("资产化标志");

        verify(inboxMapper, never()).insertIgnore(any());
        verify(importMapper, never()).insertIdempotent(any());
    }

    /** 验证不支持的收货事件版本不会被静默当作 v1 处理。 */
    @Test
    void should_fail_closed_when_event_version_is_unsupported() {
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "event-v2", BigDecimal.ONE, true, "PASS", 1L);
        event.setEventType("procurement.goods-receipt.confirmed.v2");

        assertThatThrownBy(() -> service.importEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
    }

    /** 全局事件 ID 被其他租户占用时必须返回意图冲突。 */
    @Test
    void should_reject_cross_tenant_event_id_collision() throws Exception {
        ProcurementAssetContracts.GoodsReceiptEvent event = event(
                "event-cross-tenant", BigDecimal.ONE, true, "PASS", 1L);
        AstInboxEvent inbox = inbox(event, "RECEIVED");
        inbox.setTenantId(TENANT_ID + 1);
        when(inboxMapper.selectForUpdate(anyString(), anyString())).thenReturn(inbox);

        assertThatThrownBy(() -> service.importEvent(event))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件 ID");
        verify(importMapper, never()).insertIdempotent(any());
    }

    /** 验证实时与回扫并发处理同一来源单位时仍只创建一组资产。 */
    @Test
    void should_deduplicate_when_two_backfills_import_same_source_concurrently() throws Exception {
        ProcurementAssetContracts.AssetCandidate candidate = candidate(2L);
        Map<String, AstAsset> stored = new java.util.concurrent.ConcurrentHashMap<>();
        when(importMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            AstAsset asset = invocation.getArgument(0);
            String key = asset.getTenantId() + ":" + asset.getSourceGrLineId()
                    + ":" + asset.getSourceUnitSequence();
            return stored.putIfAbsent(key, asset) == null ? 1 : 0;
        });
        when(importMapper.selectForUpdateBySource(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> stored.get(invocation.getArgument(0) + ":"
                        + invocation.getArgument(1) + ":" + invocation.getArgument(2)));
        when(historyMapper.insert(any(AstAssetHistory.class))).thenReturn(1);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ProcurementAssetContracts.ImportResult> first = executor.submit(
                    () -> concurrentImport(candidate, start));
            Future<ProcurementAssetContracts.ImportResult> second = executor.submit(
                    () -> concurrentImport(candidate, start));
            start.countDown();
            ProcurementAssetContracts.ImportResult firstResult = first.get(10, TimeUnit.SECONDS);
            ProcurementAssetContracts.ImportResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.getCreatedCount() + secondResult.getCreatedCount()).isEqualTo(2);
            assertThat(firstResult.getDuplicateCount() + secondResult.getDuplicateCount()).isEqualTo(2);
            assertThat(stored).hasSize(2);
            verify(historyMapper, times(2)).insert(any(AstAssetHistory.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private ProcurementAssetContracts.ImportResult concurrentImport(
            ProcurementAssetContracts.AssetCandidate candidate, CountDownLatch start)
            throws InterruptedException {
        start.await(10, TimeUnit.SECONDS);
        try {
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    0L, TENANT_ID, "concurrent-backfill"));
            return service.importCandidate(TENANT_ID, candidate);
        } finally {
            ServiceIdentityContext.clear();
        }
    }

    private AstInboxEvent inbox(
            ProcurementAssetContracts.GoodsReceiptEvent event, String status) throws Exception {
        AstInboxEvent inbox = new AstInboxEvent();
        inbox.setId(901L);
        inbox.setTenantId(TENANT_ID);
        inbox.setConsumerName("asset-procurement-goods-receipt-v1");
        inbox.setEventId(event.getEventId());
        inbox.setEventType(event.getEventType());
        inbox.setSourceService("omni-procurement");
        inbox.setAggregateType("GOODS_RECEIPT");
        inbox.setAggregateId(String.valueOf(event.getPayload().getGoodsReceiptId()));
        inbox.setPayload(objectMapper.writeValueAsString(event));
        inbox.setStatus(status);
        return inbox;
    }

    private ProcurementAssetContracts.GoodsReceiptEvent event(
            String eventId, BigDecimal quantity, Boolean assetManaged,
            String qualityStatus, Long assetQuantity) {
        ProcurementAssetContracts.GoodsReceiptLine line = line(
                quantity, assetManaged, qualityStatus, assetQuantity);
        ProcurementAssetContracts.GoodsReceiptPayload payload =
                new ProcurementAssetContracts.GoodsReceiptPayload();
        payload.setGoodsReceiptId(301L);
        payload.setGrNo("GR-301");
        payload.setPurchaseOrderId(201L);
        payload.setPoNo("PO-201");
        payload.setSupplierId(101L);
        payload.setSupplierNameSnapshot("示例供应商");
        payload.setPurchaseDate(LocalDateTime.of(2026, 7, 22, 10, 30));
        payload.setCurrencyCode("CNY");
        payload.setOwnerUserId(701L);
        payload.setOwnerUnitId(801L);
        payload.setLines(List.of(line));
        ProcurementAssetContracts.GoodsReceiptEvent event =
                new ProcurementAssetContracts.GoodsReceiptEvent();
        event.setEventId(eventId);
        event.setEventType(ProcurementGoodsReceiptConsumer.CONFIRMED_EVENT);
        event.setOccurredAt(LocalDateTime.of(2026, 7, 22, 10, 31));
        event.setTenantId(TENANT_ID);
        event.setPayload(payload);
        return event;
    }

    private ProcurementAssetContracts.GoodsReceiptLine line(
            BigDecimal quantity, Boolean assetManaged,
            String qualityStatus, Long assetQuantity) {
        ProcurementAssetContracts.GoodsReceiptLine line =
                new ProcurementAssetContracts.GoodsReceiptLine();
        line.setGoodsReceiptLineId(401L);
        line.setPurchaseOrderLineId(501L);
        line.setMaterialId(601L);
        line.setMaterialCode("IT-NB-001");
        line.setMaterialNameSnapshot("商务笔记本");
        line.setCategoryCode("IT_DEVICE");
        line.setUnit("EA");
        line.setReceivedQuantity(quantity);
        line.setQualityStatus(qualityStatus);
        line.setAssetManaged(assetManaged);
        line.setAssetQuantity(assetQuantity);
        line.setUnitPrice(new BigDecimal("100.000000"));
        line.setTotalPrice(new BigDecimal("100.000000").multiply(quantity));
        return line;
    }

    private ProcurementAssetContracts.AssetCandidate candidate(Long quantity) {
        ProcurementAssetContracts.AssetCandidate candidate =
                new ProcurementAssetContracts.AssetCandidate();
        candidate.setEventId("event-backfill");
        candidate.setGoodsReceiptId(301L);
        candidate.setGrNo("GR-301");
        candidate.setPurchaseOrderId(201L);
        candidate.setPoNo("PO-201");
        candidate.setSupplierId(101L);
        candidate.setSupplierNameSnapshot("示例供应商");
        candidate.setPurchaseDate(LocalDateTime.of(2026, 7, 22, 10, 30));
        candidate.setCurrencyCode("CNY");
        candidate.setOwnerUserId(701L);
        candidate.setOwnerUnitId(801L);
        candidate.setGoodsReceiptLineId(401L);
        candidate.setPurchaseOrderLineId(501L);
        candidate.setMaterialId(601L);
        candidate.setMaterialCode("IT-NB-001");
        candidate.setMaterialNameSnapshot("商务笔记本");
        candidate.setCategoryCode("IT_DEVICE");
        candidate.setUnit("EA");
        candidate.setReceivedQuantity(BigDecimal.valueOf(quantity));
        candidate.setQualityStatus("PASS");
        candidate.setAssetManaged(true);
        candidate.setAssetQuantity(quantity);
        candidate.setUnitPrice(new BigDecimal("100.000000"));
        candidate.setTotalPrice(new BigDecimal("100.000000").multiply(
                BigDecimal.valueOf(quantity)));
        return candidate;
    }
}
