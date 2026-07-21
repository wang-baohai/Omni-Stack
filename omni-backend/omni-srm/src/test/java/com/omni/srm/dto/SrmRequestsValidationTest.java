package com.omni.srm.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SRM 请求嵌套校验测试。 */
class SrmRequestsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 评估评分项必须触发嵌套的 1-5 分校验。 */
    @Test
    void shouldValidateNestedEvaluationScore() {
        SrmRequests.EvaluationItemInput item = new SrmRequests.EvaluationItemInput();
        item.setDimensionId(1L);
        item.setScore(BigDecimal.ZERO);
        SrmRequests.CreateEvaluationRequest request = new SrmRequests.CreateEvaluationRequest();
        request.setSupplierId(10L);
        request.setEvaluationPeriod("2026-Q3");
        request.setItems(List.of(item));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("items[0].score"));
    }

    /** 评分精度必须与 DECIMAL(3,1) 一致，避免总分和持久化明细不一致。 */
    @Test
    void shouldRejectEvaluationScoreWithExcessPrecision() {
        SrmRequests.EvaluationItemInput item = new SrmRequests.EvaluationItemInput();
        item.setDimensionId(1L);
        item.setScore(new BigDecimal("4.99"));
        SrmRequests.CreateEvaluationRequest request = new SrmRequests.CreateEvaluationRequest();
        request.setSupplierId(10L);
        request.setEvaluationPeriod("2026-Q3");
        request.setItems(List.of(item));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("items[0].score"));
    }

    /** 供应商分页必须限制每页最多一百条。 */
    @Test
    void shouldRejectOversizedSupplierPage() {
        SrmRequests.SupplierQuery query = new SrmRequests.SupplierQuery();
        query.setSize(101);
        assertThat(validator.validate(query)).isNotEmpty();
    }

    /** 供应商邮件和数据库字段长度必须在进入服务层前完成校验。 */
    @Test
    void shouldRejectInvalidSupplierEmailAndOversizedPhone() {
        SrmRequests.CreateSupplierRequest request = new SrmRequests.CreateSupplierRequest();
        request.setName("测试供应商");
        request.setSupplierType("MATERIAL");
        request.setEmail("not-an-email");
        request.setPhone("1".repeat(33));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "phone");
    }

    /** 乐观锁版本不可为负数，可选名称一旦提供就不能是纯空白。 */
    @Test
    void shouldRejectNegativeVersionAndBlankUpdateName() {
        SrmRequests.UpdateSupplierRequest request = new SrmRequests.UpdateSupplierRequest();
        request.setVersion(-1);
        request.setName("   ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("version", "name");
    }

    /** 门户入驻即使在全局宽松反序列化配置下也必须拒绝伪造可信身份字段。 */
    @Test
    void shouldRejectForgedTenantAndUserFieldsDuringEnrollmentDeserialization() {
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        for (String forgedField : List.of("tenantId", "userId")) {
            String payload = """
                    {
                      "requestId":"request-1",
                      "inviteToken":"invite-token",
                      "name":"测试供应商",
                      "creditCode":"91320000TEST",
                      "%s":999
                    }
                    """.formatted(forgedField);

            assertThatThrownBy(() -> objectMapper.readValue(payload, SrmRequests.EnrollRequest.class))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(forgedField);
        }
    }
}
