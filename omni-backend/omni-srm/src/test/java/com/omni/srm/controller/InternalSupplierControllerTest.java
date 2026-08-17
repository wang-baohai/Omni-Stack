package com.omni.srm.controller;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.dto.InternalSupplierBatchRequest;
import com.omni.srm.dto.InternalSupplierSummary;
import com.omni.srm.service.InternalSupplierService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 内部供应商查询控制器租户一致性测试。 */
class InternalSupplierControllerTest {

    /** 请求头与请求体租户一致时转发批量查询。 */
    @Test
    void shouldBatchQueryWhenTenantIdsMatch() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);
        InternalSupplierBatchRequest request = request(7L, List.of(3L, 1L, 3L));
        InternalSupplierSummary summary = InternalSupplierSummary.builder().id(3L).build();
        when(service.batch(7L, request.getSupplierIds())).thenReturn(List.of(summary));

        R<List<InternalSupplierSummary>> response = controller.batch(7L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(summary);
        verify(service).batch(7L, List.of(3L, 1L, 3L));
    }

    /** 请求头与请求体租户不一致时拒绝查询。 */
    @Test
    void shouldRejectMismatchedTenantIds() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);
        InternalSupplierBatchRequest request = request(8L, List.of(3L));

        assertThatThrownBy(() -> controller.batch(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
        verifyNoInteractions(service);
    }

    /** 单条查询必须校验请求头与查询参数租户一致。 */
    @Test
    void shouldGetSupplierWhenTenantIdsMatch() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);
        InternalSupplierSummary summary = InternalSupplierSummary.builder().id(3L).build();
        when(service.get(7L, 3L)).thenReturn(summary);

        R<InternalSupplierSummary> response = controller.get(7L, 3L, 7L);

        assertThat(response.getData()).isEqualTo(summary);
        verify(service).get(7L, 3L);
    }

    /** 单条查询的请求头与查询参数租户不一致时必须失败关闭。 */
    @Test
    void shouldRejectMismatchedTenantIdsForGet() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);

        assertThatThrownBy(() -> controller.get(7L, 3L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
        verifyNoInteractions(service);
    }

    /** 搜索查询必须校验请求头与查询参数租户一致。 */
    @Test
    void shouldSearchSuppliersWhenTenantIdsMatch() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);
        when(service.search(7L, "APPROVED", "IT", 20)).thenReturn(List.of());

        R<List<InternalSupplierSummary>> response = controller.search(
                7L, 7L, "APPROVED", "IT", null, 20);

        assertThat(response.getData()).isEmpty();
        verify(service).search(7L, "APPROVED", "IT", 20);
    }

    /** 搜索查询的请求头与查询参数租户不一致时必须失败关闭。 */
    @Test
    void shouldRejectMismatchedTenantIdsForSearch() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);

        assertThatThrownBy(() -> controller.search(7L, 8L, null, null, null, 50))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
        verifyNoInteractions(service);
    }

    /** 带关键词的候选搜索转发到名称或编号搜索能力。 */
    @Test
    void shouldSearchSupplierOptionsByKeyword() {
        InternalSupplierService service = mock(InternalSupplierService.class);
        InternalSupplierController controller = new InternalSupplierController(service);
        when(service.searchOptions(7L, "APPROVED", null, "云采", 30)).thenReturn(List.of());

        R<List<InternalSupplierSummary>> response = controller.search(
                7L, 7L, "APPROVED", null, "云采", 30);

        assertThat(response.getData()).isEmpty();
        verify(service).searchOptions(7L, "APPROVED", null, "云采", 30);
    }

    private InternalSupplierBatchRequest request(Long tenantId, List<Long> supplierIds) {
        InternalSupplierBatchRequest request = new InternalSupplierBatchRequest();
        request.setTenantId(tenantId);
        request.setSupplierIds(supplierIds);
        return request;
    }
}
