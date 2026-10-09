package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.RfqViews;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;
import com.omni.procurement.entity.ProcRfqSupplier;
import com.omni.procurement.mapper.ProcRfqLineMapper;
import com.omni.procurement.mapper.ProcRfqMapper;
import com.omni.procurement.mapper.ProcRfqSupplierMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** SRM 内部邀请租户隔离和历史只读可见性测试。 */
@ExtendWith(MockitoExtension.class)
class InternalRfqInvitationServiceImplTest {

    @Mock private ProcRfqMapper rfqMapper;
    @Mock private ProcRfqLineMapper lineMapper;
    @Mock private ProcRfqSupplierMapper supplierMapper;

    private InternalRfqInvitationServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcRfq.class, "ProcRfqMapper");
        initialize(ProcRfqLine.class, "ProcRfqLineMapper");
        initialize(ProcRfqSupplier.class, "ProcRfqSupplierMapper");
    }

    /** 初始化内部服务。 */
    @BeforeEach
    void setUp() {
        service = new InternalRfqInvitationServiceImpl(rfqMapper, lineMapper, supplierMapper);
    }

    /** 每个测试后确认内部调用没有泄漏线程上下文。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 返回当前租户中已经发送的邀请摘要。 */
    @Test
    void shouldReturnOnlyStrictlyValidTenantInvitation() {
        when(supplierMapper.selectList(any())).thenReturn(List.of(invitation(41L)));
        when(rfqMapper.selectList(any())).thenReturn(List.of(rfq()));

        List<RfqViews.InternalInvitationSummary> result = service.list(41L, 501L);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.getTenantId()).isEqualTo(41L);
            assertThat(summary.getRfqId()).isEqualTo(100L);
            assertThat(summary.getSupplierId()).isEqualTo(501L);
            assertThat(summary.getStatus()).isEqualTo(RfqStateMachine.SENT);
            assertThat(summary.getInvitationStatus()).isEqualTo(RfqStateMachine.INVITED);
        });
        assertThat(ServiceDataScopeContext.get()).isNull();
        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .isInstanceOf(BusinessException.class);
    }

    /** 即使 Mapper 异常返回其他租户邀请，服务也必须二次过滤。 */
    @Test
    void shouldRejectCrossTenantInvitationDefensively() {
        ProcRfq otherTenantRfq = rfq();
        otherTenantRfq.setTenantId(42L);
        when(supplierMapper.selectList(any())).thenReturn(List.of(invitation(42L)));
        when(rfqMapper.selectList(any())).thenReturn(List.of(otherTenantRfq));

        assertThat(service.list(41L, 501L)).isEmpty();
    }

    /** 邀请详情只包含 PII-free 头字段和完整 RFQ 行快照。 */
    @Test
    void shouldReturnPiiFreeDetailWithLineSnapshot() throws Exception {
        when(rfqMapper.selectOne(any())).thenReturn(rfq());
        when(supplierMapper.selectOne(any())).thenReturn(invitation(41L));
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));

        RfqViews.InternalInvitationDetail result = service.get(41L, 100L, 501L);
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(result);

        assertThat(result.getLines()).singleElement().satisfies(value -> {
            assertThat(value.getRfqLineId()).isEqualTo(201L);
            assertThat(value.getQuantity()).isEqualByComparingTo("2.000000");
        });
        assertThat(json).contains("\"quantity\":\"2.000000\"")
                .doesNotContain("supplierName", "ownerUserId", "ownerUnitId");
    }

    /** 已截止邀请仍必须返回，报价是否开放由 SRM 提交路径重新校验。 */
    @Test
    void shouldReturnExpiredInvitationForReadOnlyHistory() {
        ProcRfq historical = rfq();
        historical.setQuotationDeadline(LocalDateTime.now().minusDays(1));
        ProcRfqSupplier expired = invitation(41L);
        expired.setStatus(RfqStateMachine.EXPIRED);
        when(rfqMapper.selectOne(any())).thenReturn(historical);
        when(supplierMapper.selectOne(any())).thenReturn(expired);
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));

        RfqViews.InternalInvitationDetail result = service.get(41L, 100L, 501L);

        assertThat(result.getQuotationDeadline()).isBefore(LocalDateTime.now());
        assertThat(result.getInvitationStatus()).isEqualTo(RfqStateMachine.EXPIRED);
    }

    /** 取消后的已发送邀请仍可历史查看。 */
    @Test
    void shouldReturnCancelledInvitationForReadOnlyHistory() {
        ProcRfq historical = rfq();
        historical.setStatus(RfqStateMachine.CANCELLED);
        ProcRfqSupplier expired = invitation(41L);
        expired.setStatus(RfqStateMachine.EXPIRED);
        when(rfqMapper.selectOne(any())).thenReturn(historical);
        when(supplierMapper.selectOne(any())).thenReturn(expired);
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));

        RfqViews.InternalInvitationDetail result = service.get(41L, 100L, 501L);

        assertThat(result.getStatus()).isEqualTo(RfqStateMachine.CANCELLED);
        assertThat(result.getInvitationStatus()).isEqualTo(RfqStateMachine.EXPIRED);
    }

    /** 定点后的中标与未中标邀请都必须继续供供应商门户历史只读查看。 */
    @Test
    void shouldReturnAwardedAndRejectedInvitationsForReadOnlyHistory() {
        ProcRfq historical = rfq();
        historical.setStatus(RfqStateMachine.AWARDED);
        ProcRfqSupplier winner = invitation(41L);
        winner.setStatus(RfqStateMachine.AWARDED);
        ProcRfqSupplier loser = invitation(41L);
        loser.setStatus(RfqStateMachine.REJECTED);
        when(rfqMapper.selectOne(any())).thenReturn(historical, historical);
        when(supplierMapper.selectOne(any())).thenReturn(winner, loser);
        when(lineMapper.selectList(any())).thenReturn(List.of(line()));

        RfqViews.InternalInvitationDetail awarded = service.get(41L, 100L, 501L);
        RfqViews.InternalInvitationDetail rejected = service.get(41L, 100L, 501L);

        assertThat(awarded.getStatus()).isEqualTo(RfqStateMachine.AWARDED);
        assertThat(awarded.getInvitationStatus()).isEqualTo(RfqStateMachine.AWARDED);
        assertThat(rejected.getInvitationStatus()).isEqualTo(RfqStateMachine.REJECTED);
    }

    /** 从未发送的草稿邀请必须隐藏并清理内部线程上下文。 */
    @Test
    void shouldHideUnsentDraftInvitationAndClearContext() {
        ProcRfq draft = rfq();
        draft.setStatus(RfqStateMachine.DRAFT);
        draft.setSentTime(null);
        ProcRfqSupplier unsent = invitation(41L);
        unsent.setInvitedTime(null);
        when(rfqMapper.selectOne(any())).thenReturn(draft);
        when(supplierMapper.selectOne(any())).thenReturn(unsent);

        assertThatThrownBy(() -> service.get(41L, 100L, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
        assertThat(ServiceDataScopeContext.get()).isNull();
        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .isInstanceOf(BusinessException.class);
    }

    private ProcRfq rfq() {
        ProcRfq rfq = new ProcRfq();
        rfq.setId(100L);
        rfq.setTenantId(41L);
        rfq.setRfqNo("RFQ-41-100");
        rfq.setTitle("笔记本询价");
        rfq.setCurrencyCode("CNY");
        rfq.setStatus(RfqStateMachine.SENT);
        rfq.setQuotationDeadline(LocalDateTime.now().plusDays(2));
        rfq.setSentTime(LocalDateTime.now().minusHours(1));
        rfq.setDeleted(0);
        return rfq;
    }

    private ProcRfqSupplier invitation(Long tenantId) {
        ProcRfqSupplier invitation = new ProcRfqSupplier();
        invitation.setId(301L);
        invitation.setTenantId(tenantId);
        invitation.setRfqId(100L);
        invitation.setSupplierId(501L);
        invitation.setSupplierNameSnapshot("不应输出的供应商名称");
        invitation.setStatus(RfqStateMachine.INVITED);
        invitation.setInvitedTime(LocalDateTime.now().minusHours(1));
        invitation.setDeleted(0);
        return invitation;
    }

    private ProcRfqLine line() {
        ProcRfqLine line = new ProcRfqLine();
        line.setId(201L);
        line.setTenantId(41L);
        line.setRfqId(100L);
        line.setLineNo(1);
        line.setMaterialCode("NB-001");
        line.setMaterialName("笔记本电脑");
        line.setUnit("EA");
        line.setQuantity(new BigDecimal("2.000000"));
        line.setRemark("含三年保修");
        line.setDeleted(0);
        return line;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.test." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
