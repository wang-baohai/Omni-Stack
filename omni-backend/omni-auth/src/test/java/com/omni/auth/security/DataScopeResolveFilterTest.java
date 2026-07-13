package com.omni.auth.security;

import com.omni.auth.service.DataScopeService;
import com.omni.common.core.internal.InternalDataScopeDTO;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeResolveFilter} 单元测试。
 */
class DataScopeResolveFilterTest {

    /**
     * 每个测试后清理数据权限上下文。
     */
    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    /**
     * 内部接口应跳过普通请求级数据范围上下文。
     */
    @Test
    void should_skip_internal_api_paths() {
        DataScopeResolveFilter filter = new DataScopeResolveFilter(mock(DataScopeService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/data-scopes/7");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    /**
     * 过滤器应复用服务解析结果，并在请求完成后清理上下文。
     *
     * @throws Exception 过滤器执行异常
     */
    @Test
    void should_use_service_result_and_clear_context_after_request() throws Exception {
        DataScopeService dataScopeService = mock(DataScopeService.class);
        InternalDataScopeDTO resolved = new InternalDataScopeDTO();
        resolved.setUserId(7L);
        resolved.setTenantId(2L);
        resolved.setPrimaryUnitId(10L);
        resolved.setEffectiveScope("DEPT");
        resolved.setAccessibleUnitIds(Set.of(10L));
        when(dataScopeService.resolveDataScope(7L, 2L)).thenReturn(resolved);
        DataScopeResolveFilter filter = new DataScopeResolveFilter(dataScopeService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/list");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(DataScopeContext.get()).isNotNull();
            assertThat(DataScopeContext.get().getEffectiveScope()).isEqualTo("DEPT");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(DataScopeContext.get()).isNull();
        verify(dataScopeService).resolveDataScope(7L, 2L);
    }

    /**
     * 已识别用户但缺少租户头时应失败关闭。
     *
     * @throws Exception 过滤器执行异常
     */
    @Test
    void should_reject_authenticated_request_without_tenant_header() throws Exception {
        DataScopeService dataScopeService = mock(DataScopeService.class);
        DataScopeResolveFilter filter = new DataScopeResolveFilter(dataScopeService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/list");
        request.addHeader("X-User-Id", "7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(dataScopeService, never()).resolveDataScope(7L, null);
        verify(chain, never()).doFilter(request, response);
    }
}
