package com.omni.asset.service.impl;

import com.omni.asset.dto.AssetOverviewRequests;
import com.omni.asset.dto.AssetOverviewViews;
import com.omni.asset.mapper.AssetOverviewMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.core.result.BusinessException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 资产概览状态补零、币种隔离与数据范围测试。 */
@ExtendWith(MockitoExtension.class)
class AssetOverviewServiceImplTest {

    @Mock private AssetOverviewMapper overviewMapper;
    private AssetOverviewServiceImpl service;

    /** 建立资产管理范围。 */
    @BeforeEach
    void setUp() {
        service = new AssetOverviewServiceImpl(overviewMapper);
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                7L, 41L, "asset:overview:list", 12L, "DEPT", Set.of(12L), null));
    }

    /** 清理线程数据范围。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
    }

    /** 摘要必须补齐零值状态并保持不同币种独立。 */
    @Test
    void shouldCompleteStatusCountsAndKeepCurrenciesSeparate() {
        AssetOverviewViews.StatusCount inStock = status("IN_STOCK", 3L);
        AssetOverviewViews.StatusCount scrapped = status("SCRAPPED", 2L);
        AssetOverviewViews.CurrencyAmount cny = amount("CNY", "12000.00");
        AssetOverviewViews.CurrencyAmount usd = amount("USD", "900.00");
        when(overviewMapper.selectStatusCounts()).thenReturn(List.of(inStock, scrapped));
        when(overviewMapper.selectAmountsByCurrency()).thenReturn(List.of(cny, usd));

        AssetOverviewViews.Summary result = service.summary();

        assertThat(result.getTotalCount()).isEqualTo(5L);
        assertThat(result.getInStockCount()).isEqualTo(3L);
        assertThat(result.getInUseCount()).isZero();
        assertThat(result.getTerminalCount()).isEqualTo(2L);
        assertThat(result.getAmountsByCurrency()).extracting(
                AssetOverviewViews.CurrencyAmount::getCurrencyCode)
                .containsExactly("CNY", "USD");
    }

    /** 分布查询必须路由到指定维度并标记响应维度。 */
    @Test
    void shouldRouteDistributionDimension() {
        AssetOverviewViews.DistributionItem item = new AssetOverviewViews.DistributionItem();
        item.setDimensionKey("12");
        item.setDimensionName("12");
        item.setCount(4L);
        item.setAmount(new BigDecimal("6000.00"));
        when(overviewMapper.selectDepartmentDistribution(10)).thenReturn(List.of(item));
        AssetOverviewRequests.DistributionQuery query = new AssetOverviewRequests.DistributionQuery();
        query.setDimension(AssetOverviewRequests.DistributionDimension.DEPARTMENT);
        query.setLimit(10);

        List<AssetOverviewViews.DistributionItem> result = service.distribution(query);

        assertThat(result).singleElement().extracting(
                AssetOverviewViews.DistributionItem::getDimension)
                .isEqualTo(AssetOverviewRequests.DistributionDimension.DEPARTMENT);
        verify(overviewMapper).selectDepartmentDistribution(10);
    }

    /** 缺失数据范围时必须失败关闭。 */
    @Test
    void shouldFailClosedWithoutDataScope() {
        ServiceDataScopeContext.clear();

        assertThatThrownBy(service::summary)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
    }

    private AssetOverviewViews.StatusCount status(String value, Long count) {
        AssetOverviewViews.StatusCount row = new AssetOverviewViews.StatusCount();
        row.setStatus(value);
        row.setCount(count);
        return row;
    }

    private AssetOverviewViews.CurrencyAmount amount(String currency, String value) {
        AssetOverviewViews.CurrencyAmount row = new AssetOverviewViews.CurrencyAmount();
        row.setCurrencyCode(currency);
        row.setAmount(new BigDecimal(value));
        return row;
    }
}
