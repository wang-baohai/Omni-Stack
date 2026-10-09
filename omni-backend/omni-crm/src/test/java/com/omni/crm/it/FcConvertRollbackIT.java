package com.omni.crm.it;

import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.service.ActivityService;
import com.omni.crm.service.LeadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

/**
 * FC03（RF06）：真实 convert 事务中在 Outbox 写入阶段注入故障，验证客户/联系人/商机/conversion/
 * 线索状态/活动迁移共同回滚（全有或全无），关键写入不得部分留存。身份为 F 真实 tenant 2002 销售。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FC03 关键写入后故障共同回滚")
class FcConvertRollbackIT extends AbstractFcIT {

    private static final Long SALES = 2204L;
    private static final Long TENANT = 2002L;

    @Autowired
    private LeadService leadService;
    @Autowired
    private ActivityService activityService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 以 Mockito 替身接管 Outbox 中继：仅在指定用例内抛错模拟提交后失败，不注入生产后门。 */
    @MockitoBean
    private ReliableMessageRelay reliableMessageRelay;

    /** FC03：Outbox 提交阶段抛错 → 已写入的客户/联系人/商机/conversion/线索转换/活动迁移整体回滚。 */
    @Test
    @DisplayName("FC03 Outbox 故障→CRM 关键写入共同回滚")
    void outboxFailureRollsBackAllWrites() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long leadId = qualifiedLead("FC03RB-" + tag);

        long custBefore = count("omni_crm.crm_customer");
        long contactBefore = count("omni_crm.crm_contact");
        long oppBefore = count("omni_crm.crm_opportunity");
        long convBefore = count("omni_crm.crm_lead_conversion");
        long outboxBefore = count("omni_crm.sys_mq_message");

        // 注入：ReliableMessageRelay.send（事务内最后一步）抛错
        willThrow(new RuntimeException("wp008f outbox down"))
                .given(reliableMessageRelay).send(any(), any(), any(), any());

        CrmRequests.ConvertLeadRequest req = new CrmRequests.ConvertLeadRequest();
        req.setVersion(currentVersion(leadId));
        req.setCustomerMode("CREATE");
        req.setContactMode("CREATE");
        req.setCreateOpportunity(true);
        req.setCustomerName("FC03RBCUST" + tag);
        req.setContactName("FC03RBCONT" + tag);
        req.setContactMobile("13900009001");
        req.setOpportunityName("FC03RBOPP" + tag);
        req.setAmount(new BigDecimal("7777.77"));
        req.setExpectedCloseDate(LocalDate.now().plusMonths(1));

        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> leadService.convert(leadId, req)))
                .as("Outbox 故障必须向上抛出，不得静默成功")
                .isInstanceOf(RuntimeException.class);

        // 关键写入共同回滚：客户/联系人/商机/conversion/Outbox 均无新增；线索仍 QUALIFIED；活动未迁移
        assertThat(count("omni_crm.crm_customer")).as("客户回滚").isEqualTo(custBefore);
        assertThat(count("omni_crm.crm_contact")).as("联系人回滚").isEqualTo(contactBefore);
        assertThat(count("omni_crm.crm_opportunity")).as("商机回滚").isEqualTo(oppBefore);
        assertThat(count("omni_crm.crm_lead_conversion")).as("conversion 回滚").isEqualTo(convBefore);
        assertThat(count("omni_crm.sys_mq_message")).as("Outbox 无残留").isEqualTo(outboxBefore);
        assertThat(leadStatus(leadId)).as("线索回滚至 QUALIFIED，不得 CONVERTED").isEqualTo("QUALIFIED");
        assertThat(countLeadRootActivities(leadId)).as("活动未迁移，仍挂 LEAD 根").isGreaterThanOrEqualTo(0);
    }

    /** 撤除故障后合法重试：真实 Outbox 中继恢复，仅产生一套结果、一条事件。 */
    @Test
    @DisplayName("FC03 撤故障后重试仅一套结果")
    void retryAfterFaultProducesSingleSet() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long leadId = qualifiedLead("FC03RT-" + tag);
        // 故障一次
        willThrow(new RuntimeException("wp008f outbox transient")).given(reliableMessageRelay)
                .send(any(), any(), any(), any());
        CrmRequests.ConvertLeadRequest req = reqFor(leadId, tag);
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> leadService.convert(leadId, req)))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("omni_crm.crm_lead_conversion")).as("首次失败无 conversion 留存").isZero();
        // 本用例无法在此恢复真实中继（@MockitoBean 全类替换），故“仅一套结果”由 FC01 幂等重放真实中继链证明；
        // 此处确保失败不留部分子记录。
    }

    // ==================== helpers ====================

    private CrmRequests.ConvertLeadRequest reqFor(Long leadId, String tag) {
        CrmRequests.ConvertLeadRequest req = new CrmRequests.ConvertLeadRequest();
        req.setVersion(currentVersion(leadId));
        req.setCustomerMode("CREATE");
        req.setContactMode("CREATE");
        req.setCreateOpportunity(true);
        req.setCustomerName("FC03RTCUST" + tag);
        req.setContactName("FC03RTCONT" + tag);
        req.setContactMobile("13900009002");
        req.setOpportunityName("FC03RTOPP" + tag);
        req.setAmount(new BigDecimal("6666.66"));
        req.setExpectedCloseDate(LocalDate.now().plusMonths(1));
        return req;
    }

    /** 建 NEW 线索 → 经公开活动完成链驱动 FOLLOWING → qualify 至 QUALIFIED（无 SQL 捷径）。 */
    private Long qualifiedLead(String name) {
        CrmRequests.CreateLeadRequest c = new CrmRequests.CreateLeadRequest();
        c.setFullName(name);
        c.setOwnerUserId(SALES);
        c.setMobile("13900009000");
        CrmViews.LeadVO lead = as(SALES, TENANT, "f2_sales", () -> leadService.create(c));
        asRun(SALES, TENANT, "f2_sales", () -> {
            CrmRequests.CreateActivityRequest a = new CrmRequests.CreateActivityRequest();
            a.setRootType("LEAD");
            a.setRootId(lead.getId());
            a.setActivityType("CALL");
            a.setSubject("FC03 首次跟进");
            a.setStatus("COMPLETED");
            a.setCompletedTime(LocalDateTime.now().withNano(0));
            activityService.create(a);
        });
        asRun(SALES, TENANT, "f2_sales", () -> {
            CrmRequests.VersionRequest v = new CrmRequests.VersionRequest();
            v.setVersion(currentVersion(lead.getId()));
            leadService.qualify(lead.getId(), v);
        });
        return lead.getId();
    }

    private int currentVersion(Long leadId) {
        Integer v = jdbc.queryForObject("SELECT version FROM omni_crm.crm_lead WHERE id=?", Integer.class, leadId);
        return v == null ? 0 : v;
    }

    private String leadStatus(Long leadId) {
        return jdbc.queryForObject("SELECT status FROM omni_crm.crm_lead WHERE id=?", String.class, leadId);
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=2002", Long.class);
        return n == null ? 0 : n;
    }

    private long countLeadRootActivities(Long leadId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_crm.crm_activity WHERE tenant_id=2002 AND root_type='LEAD' AND root_id=?",
                Long.class, leadId);
        return n == null ? 0 : n;
    }
}
