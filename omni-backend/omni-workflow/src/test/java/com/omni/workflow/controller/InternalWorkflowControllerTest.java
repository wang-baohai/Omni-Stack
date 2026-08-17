package com.omni.workflow.controller;

import com.omni.common.mqlog.filter.InternalApiAuthFilter;
import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.service.InternalWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Workflow 内部接口控制器测试。
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class InternalWorkflowControllerTest {

    @Mock
    private InternalWorkflowService internalWorkflowService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalWorkflowController controller = new InternalWorkflowController(internalWorkflowService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new InternalApiAuthFilter("shared-secret"))
                .build();
    }

    @Test
    void shouldReturnPublishedModelVersionForAuthenticatedInternalRequest() throws Exception {
        InternalModelVersionResponse response = InternalModelVersionResponse.builder()
                .id(88L)
                .modelId(31L)
                .version(3)
                .processDefinitionId("definition-88")
                .status("PUBLISHED")
                .build();
        when(internalWorkflowService.getModelVersion(7L, 88L)).thenReturn(response);

        mockMvc.perform(get("/api/internal/workflow/model-version/88")
                        .header("X-Internal-Token", "shared-secret")
                        .header("X-Tenant-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(88))
                .andExpect(jsonPath("$.data.modelId").value(31))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.processDefinitionId").value("definition-88"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        verify(internalWorkflowService).getModelVersion(7L, 88L);
    }

    @Test
    void shouldRejectInternalRequestWithoutSharedToken() throws Exception {
        mockMvc.perform(get("/api/internal/workflow/model-version/88")
                        .header("X-Tenant-Id", "7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(internalWorkflowService, never()).getModelVersion(7L, 88L);
    }

    @Test
    void shouldReturnCurrentPublishedModelVersionByCategory() throws Exception {
        InternalModelVersionResponse response = InternalModelVersionResponse.builder()
                .id(99L)
                .modelId(41L)
                .category("SRM_SUPPLIER_ONBOARDING")
                .version(1)
                .processDefinitionId("supplier-definition")
                .status("PUBLISHED")
                .build();
        when(internalWorkflowService.getCurrentPublishedModelVersion(
                7L, "SRM_SUPPLIER_ONBOARDING")).thenReturn(response);

        mockMvc.perform(get("/api/internal/workflow/model-version/current")
                        .param("category", "SRM_SUPPLIER_ONBOARDING")
                        .header("X-Internal-Token", "shared-secret")
                        .header("X-Tenant-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.category").value("SRM_SUPPLIER_ONBOARDING"));

        verify(internalWorkflowService).getCurrentPublishedModelVersion(
                7L, "SRM_SUPPLIER_ONBOARDING");
    }
}
