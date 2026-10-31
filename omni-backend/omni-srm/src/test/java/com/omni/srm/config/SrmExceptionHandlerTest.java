package com.omni.srm.config;

import com.omni.common.core.result.R;
import com.omni.common.core.result.PageResult;
import com.omni.common.result.GlobalExceptionHandler;
import com.omni.srm.controller.RiskController;
import com.omni.srm.service.RiskService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SRM 请求反序列化异常响应测试。 */
class SrmExceptionHandlerTest {

    /** 未知身份字段触发的反序列化异常必须映射为业务码 400。 */
    @Test
    void shouldReturnBadRequestForUnreadableJson() {
        R<Void> response = new SrmExceptionHandler().handleUnreadableRequest();

        assertThat(response.getCode()).isEqualTo(400);
    }

    /** 真实方法校验代理的非法页码/页大小必须返回400，并且不调用业务服务。 */
    @Test
    void shouldRejectInvalidRiskPagingBeforeServiceCall() throws Exception {
        try (ValidatorFactory validators = Validation.buildDefaultValidatorFactory()) {
            RiskService service = mock(RiskService.class);
            MockMvc mvc = riskControllerMvc(service, validators);

            mvc.perform(get("/api/srm/risk/list").param("page", "0"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
            mvc.perform(get("/api/srm/risk/list").param("size", "101"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
            verifyNoInteractions(service);
        }
    }

    /** 合法分页仍以原参数进入服务，不能靠拒绝全部查询通过负例。 */
    @Test
    void shouldKeepValidRiskPagingSuccessful() throws Exception {
        try (ValidatorFactory validators = Validation.buildDefaultValidatorFactory()) {
            RiskService service = mock(RiskService.class);
            when(service.listCurrentRisks(null, 1, 10)).thenReturn(new PageResult<>(List.of(), 0, 10, 1));
            MockMvc mvc = riskControllerMvc(service, validators);

            mvc.perform(get("/api/srm/risk/list").param("page", "1").param("size", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
            verify(service).listCurrentRisks(null, 1, 10);
        }
    }

    /** 保留Controller的Validated方法代理及生产advice顺序，不直接调用异常处理方法代替HTTP链。 */
    private MockMvc riskControllerMvc(RiskService service, ValidatorFactory validators) {
        ProxyFactory proxy = new ProxyFactory(new RiskController(service));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(validators.getValidator()));
        return MockMvcBuilders.standaloneSetup(proxy.getProxy())
                .setControllerAdvice(new SrmExceptionHandler(), new GlobalExceptionHandler()).build();
    }
}
