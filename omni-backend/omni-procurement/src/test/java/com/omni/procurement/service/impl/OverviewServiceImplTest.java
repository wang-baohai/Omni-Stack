package com.omni.procurement.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.dto.OverviewRequests;
import com.omni.procurement.dto.OverviewViews;
import com.omni.procurement.mapper.ProcOverviewMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 采购概览聚合与参数校验测试。 */
@ExtendWith(MockitoExtension.class)
class OverviewServiceImplTest {

    @Mock
    private ProcOverviewMapper overviewMapper;

    private OverviewServiceImpl overviewService;

    /** 初始化 Overview 数据范围。 */
    @BeforeEach
    void setUp() {
        overviewService = new OverviewServiceImpl(overviewMapper);
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                7L, 41L, "procurement:overview:list", 12L, "SELF", Set.of(12L), null));
    }

    /** 清理线程数据范围。 */
    @AfterEach
    void clearScope() {
        ServiceDataScopeContext.clear();
    }

    /** 摘要应补齐所有订单状态零值，并保持币种金额分组。 */
    @Test
    void shouldBuildStableSummaryWithoutCombiningCurrencies() {
        OverviewViews.StatusCount confirmed = status("CONFIRMED", 2L);
        OverviewViews.StatusCount received = status("RECEIVED", 1L);
        OverviewViews.CurrencyAmount cny = amount("CNY", "120.0000");
        OverviewViews.CurrencyAmount usd = amount("USD", "8.5000");
        when(overviewMapper.countPendingApprovalRequisitions()).thenReturn(3L);
        when(overviewMapper.countWaitingQuotationRfqs()).thenReturn(4L);
        when(overviewMapper.selectPurchaseOrderStatusCounts())
                .thenReturn(List.of(confirmed, received));
        when(overviewMapper.countDraftGoodsReceipts()).thenReturn(5L);
        when(overviewMapper.selectCommittedAmountsByCurrency()).thenReturn(List.of(cny, usd));

        OverviewViews.Summary result = overviewService.summary();

        assertThat(result.getPendingApprovalRequisitionCount()).isEqualTo(3L);
        assertThat(result.getWaitingQuotationRfqCount()).isEqualTo(4L);
        assertThat(result.getDraftGoodsReceiptCount()).isEqualTo(5L);
        assertThat(result.getPurchaseOrderStatusCounts()).hasSize(7);
        assertThat(result.getPurchaseOrderStatusCounts())
                .filteredOn(value -> "DRAFT".equals(value.getStatus()))
                .singleElement().extracting(OverviewViews.StatusCount::getCount).isEqualTo(0L);
        assertThat(result.getCommittedAmountsByCurrency())
                .extracting(OverviewViews.CurrencyAmount::getCurrencyCode)
                .containsExactly("CNY", "USD");
        assertThat(result.getCommittedAmountsByCurrency())
                .extracting(OverviewViews.CurrencyAmount::getAmount)
                .containsExactly(new BigDecimal("120.0000"), new BigDecimal("8.5000"));
    }

    /** 各分析维度必须只调用对应的一条 Mapper 聚合查询并写入维度类型。 */
    @Test
    void shouldRouteSpendAnalysisToOneAggregateQuery() {
        OverviewViews.SpendItem category = spendItem("IT", "信息设备", "CNY", "100.0000");
        when(overviewMapper.selectCategorySpend(30)).thenReturn(List.of(category));
        OverviewRequests.SpendAnalysisQuery query = query(
                OverviewRequests.SpendDimension.CATEGORY, 30);

        List<OverviewViews.SpendItem> result = overviewService.spendAnalysis(query);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getDimension()).isEqualTo(OverviewRequests.SpendDimension.CATEGORY);
            assertThat(item.getDimensionKey()).isEqualTo("IT");
            assertThat(item.getCurrencyCode()).isEqualTo("CNY");
            assertThat(item.getAmount()).isEqualByComparingTo("100.0000");
        });
        verify(overviewMapper).selectCategorySpend(30);
        verify(overviewMapper, never()).selectSupplierSpend(30);
        verify(overviewMapper, never()).selectDepartmentSpend(30);
    }

    /** 服务层必须在 SQL 执行前拒绝超出 100 的 limit。 */
    @Test
    void shouldRejectSpendLimitAboveOneHundred() {
        OverviewRequests.SpendAnalysisQuery query = query(
                OverviewRequests.SpendDimension.SUPPLIER, 101);

        assertThatThrownBy(() -> overviewService.spendAnalysis(query))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);

        verify(overviewMapper, never()).selectSupplierSpend(101);
    }

    /** 缺少数据范围时必须失败关闭且不查询 Mapper。 */
    @Test
    void shouldFailClosedWithoutOverviewScope() {
        ServiceDataScopeContext.clear();

        assertThatThrownBy(() -> overviewService.summary())
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);

        verify(overviewMapper, never()).countPendingApprovalRequisitions();
    }

    private OverviewRequests.SpendAnalysisQuery query(
            OverviewRequests.SpendDimension dimension, int limit) {
        OverviewRequests.SpendAnalysisQuery query = new OverviewRequests.SpendAnalysisQuery();
        query.setDimension(dimension);
        query.setLimit(limit);
        return query;
    }

    private OverviewViews.StatusCount status(String status, Long count) {
        OverviewViews.StatusCount value = new OverviewViews.StatusCount();
        value.setStatus(status);
        value.setCount(count);
        return value;
    }

    private OverviewViews.CurrencyAmount amount(String currencyCode, String amount) {
        OverviewViews.CurrencyAmount value = new OverviewViews.CurrencyAmount();
        value.setCurrencyCode(currencyCode);
        value.setAmount(new BigDecimal(amount));
        return value;
    }

    private OverviewViews.SpendItem spendItem(
            String key, String name, String currencyCode, String amount) {
        OverviewViews.SpendItem value = new OverviewViews.SpendItem();
        value.setDimensionKey(key);
        value.setDimensionName(name);
        value.setCurrencyCode(currencyCode);
        value.setAmount(new BigDecimal(amount));
        return value;
    }
}
