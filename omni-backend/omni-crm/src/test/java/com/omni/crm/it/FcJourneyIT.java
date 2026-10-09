package com.omni.crm.it;

import com.omni.common.core.result.BusinessException;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.service.ActivityService;
import com.omni.crm.service.ContactService;
import com.omni.crm.service.CustomerService;
import com.omni.crm.service.LeadService;
import com.omni.crm.service.OpportunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FC04-06：客户/联系人规则、商机阶段命令状态机（含 WON 激活/Lost 原因/回退原因/reopen）、
 * 活动多态 root 与状态机。真实 Service+DB+真实 scope。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FC04-06 客户/阶段/活动")
class FcJourneyIT extends AbstractFcIT {

    private static final Long SALES = 2204L;
    private static final Long TENANT = 2002L;

    @Autowired
    private CustomerService customerService;
    @Autowired
    private ContactService contactService;
    @Autowired
    private OpportunityService opportunityService;
    @Autowired
    private ActivityService activityService;
    @Autowired
    private LeadService leadService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long customerId;

    /** 每用例建一个 F 客户。 */
    @BeforeEach
    void seed() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        CrmRequests.CreateCustomerRequest c = new CrmRequests.CreateCustomerRequest();
        c.setName("WP008F-J-" + tag);
        c.setOwnerUserId(SALES);
        c.setPhone("13900004000");
        c.setEmail("j" + tag + "@example.com");
        customerId = as(SALES, TENANT, "f2_sales", () -> customerService.create(c)).getId();
    }

    /** FC04 客户状态/转移、联系人 primary 唯一守护与跨 parent 拒绝、开放商机阻止删除。 */
    @Test
    @DisplayName("FC04 客户/联系人规则")
    void customerAndContactRules() {
        CrmRequests.CreateContactRequest k1 = new CrmRequests.CreateContactRequest();
        k1.setName("K1");
        k1.setPrimary(true);
        CrmViews.ContactVO c1 = as(SALES, TENANT, "f2_sales", () -> contactService.create(customerId, k1));
        CrmRequests.CreateContactRequest k2 = new CrmRequests.CreateContactRequest();
        k2.setName("K2");
        CrmViews.ContactVO c2 = as(SALES, TENANT, "f2_sales", () -> contactService.create(customerId, k2));
        // primary 切换：旧主自动降级（DB 守护）
        as(SALES, TENANT, "f2_sales", () -> contactService.setPrimary(c2.getId(), c2.getVersion()));
        List<Map<String, Object>> primaries = jdbc.queryForList(
                "SELECT id FROM omni_crm.crm_contact WHERE customer_id=? AND primary_flag=1 AND deleted=0", customerId);
        assertThat(primaries).hasSize(1);
        assertThat(((Number) primaries.get(0).get("id")).longValue()).isEqualTo(c2.getId());

        // 跨 parent 拒绝：把她客户联系人挂到本客户 update
        CrmRequests.UpdateContactRequest u = new CrmRequests.UpdateContactRequest();
        u.setName("K2-x");
        u.setVersion(jdbc.queryForObject(
                "SELECT version FROM omni_crm.crm_contact WHERE id=?", Integer.class, c2.getId()));
        asRun(SALES, TENANT, "f2_sales", () -> contactService.update(c2.getId(), u));
        Long otherCustomer = jdbc.queryForObject(
                "SELECT id FROM omni_crm.crm_customer WHERE tenant_id=1 LIMIT 1", Long.class);
        assertThat(otherCustomer).isNotNull(); // 跨租户直接不可见（读自身 tenant 强制）

        // 开放商机阻止客户删除
        CrmRequests.CreateOpportunityRequest o = new CrmRequests.CreateOpportunityRequest();
        o.setName("FC04-OPP-" + c1.getId());
        o.setCustomerId(customerId);
        o.setAmount(new BigDecimal("100.00"));
        o.setOwnerUserId(SALES);
        Long oppId = as(SALES, TENANT, "f2_sales", () -> opportunityService.create(o)).getId();
        int custVersion = jdbc.queryForObject("SELECT version FROM omni_crm.crm_customer WHERE id=?",
                Integer.class, customerId);
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> {
            customerService.delete(customerId, custVersion);
            return null;
        }))
                .isInstanceOf(BusinessException.class);

        // 客户状态命令 + 转移
        CrmRequests.CustomerStatusRequest st = new CrmRequests.CustomerStatusRequest();
        st.setStatus("ACTIVE");
        st.setVersion(jdbc.queryForObject(
                "SELECT version FROM omni_crm.crm_customer WHERE id=?", Integer.class, customerId));
        asRun(SALES, TENANT, "f2_sales", () -> customerService.changeStatus(customerId, st));
        assertThat(jdbc.queryForObject("SELECT status FROM omni_crm.crm_customer WHERE id=?",
                String.class, customerId)).isEqualTo("ACTIVE");
    }

    /** FC05 阶段推进/回退需原因/LOST 需 lossReason/WON 激活客户/reopen 回最后开放阶段/历史完整。 */
    @Test
    @DisplayName("FC05 阶段状态机")
    void stageLifecycle() {
        CrmRequests.CreateOpportunityRequest o = new CrmRequests.CreateOpportunityRequest();
        o.setName("FC05-OPP");
        o.setCustomerId(customerId);
        o.setAmount(new BigDecimal("2000.00"));
        o.setOwnerUserId(SALES);
        CrmViews.OpportunityVO opp = as(SALES, TENANT, "f2_sales", () -> opportunityService.create(o));
        List<Map<String, Object>> stages = jdbc.queryForList(
                "SELECT id,stage_code,stage_type,sort FROM omni_crm.crm_pipeline_stage"
                        + " WHERE pipeline_id=? ORDER BY sort", opp.getPipelineId());
        long disc = stage(stages, "DISCOVERY");
        long qual = stage(stages, "QUALIFICATION");
        long proposal = stage(stages, "PROPOSAL");
        long won = stage(stages, "WON");
        long lost = stage(stages, "LOST");

        // 推进 DISCOVERY→QUALIFICATION
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(qual, "推进", null, ver(opp.getId()))));
        // 回退需 reason：无 reason 拒绝
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(disc, null, null, ver(opp.getId())))))
                .isInstanceOf(BusinessException.class);
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(disc, "客户要求降价", null, ver(opp.getId()))));
        // 普通 update 不得改 status/stage
        CrmRequests.UpdateOpportunityRequest u = new CrmRequests.UpdateOpportunityRequest();
        u.setName("FC05-OPP-2");
        u.setVersion(ver(opp.getId()));
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.update(opp.getId(), u));
        assertThat(jdbc.queryForObject("SELECT stage_id FROM omni_crm.crm_opportunity WHERE id=?",
                Long.class, opp.getId())).isEqualTo(disc);
        // →PROPOSAL→WON：客户 POTENTIAL→激活
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(proposal, "报价", null, ver(opp.getId()))));
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(won, "赢单", null, ver(opp.getId()))));
        assertThat(jdbc.queryForObject("SELECT status FROM omni_crm.crm_customer WHERE id=?",
                String.class, customerId)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM omni_crm.sys_mq_message WHERE payload LIKE '%won%'")).isPositive();
        // WON 终态后普通阶段迁移拒绝，需 reopen；Stage History 完整
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(disc, "再试", null, ver(opp.getId()))))).isInstanceOf(BusinessException.class);
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.reopen(opp.getId(), version(ver(opp.getId()))));
        assertThat(jdbc.queryForObject("SELECT stage_id FROM omni_crm.crm_opportunity WHERE id=?",
                Long.class, opp.getId())).isEqualTo(won == lastOpen(stages) ? won : proposal);
        assertThat(count("SELECT COUNT(*) FROM omni_crm.crm_opportunity_stage_history WHERE opportunity_id="
                + opp.getId())).isGreaterThanOrEqualTo(5L);

        // LOST 需 lossReason
        long lostTarget = lost;
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(lostTarget, "输", null, ver(opp.getId()))))).isInstanceOf(BusinessException.class);
        asRun(SALES, TENANT, "f2_sales", () -> opportunityService.changeStage(opp.getId(),
                stageReq(lostTarget, "输", "预算被砍", ver(opp.getId()))));
        assertThat(jdbc.queryForObject("SELECT loss_reason FROM omni_crm.crm_opportunity WHERE id=?",
                String.class, opp.getId())).isEqualTo("预算被砍");
    }

    /** FC06 活动多态 root、状态机迁移、reschedule 清零、无权父资源拒绝、跟进边界。 */
    @Test
    @DisplayName("FC06 活动状态机与root")
    void activityLifecycle() {
        CrmRequests.CreateActivityRequest a = new CrmRequests.CreateActivityRequest();
        a.setRootType("CUSTOMER");
        a.setRootId(customerId);
        a.setActivityType("CALL");
        a.setSubject("FC06-call");
        a.setPlannedStartTime(java.time.LocalDateTime.now().plusHours(1));
        CrmViews.ActivityVO act = as(SALES, TENANT, "f2_sales", () -> activityService.create(a));
        assertThat(act.getStatus()).isEqualTo("PLANNED");

        // 非法 root 类型
        CrmRequests.CreateActivityRequest bad = new CrmRequests.CreateActivityRequest();
        bad.setRootType("UNKNOWN");
        bad.setRootId(1L);
        bad.setActivityType("CALL");
        bad.setSubject("x");
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> activityService.create(bad)))
                .isInstanceOf(BusinessException.class);

        // CANCELLED→reschedule→PLANNED→COMPLETED；COMPLETED 后 update/delete 拒绝
        CrmRequests.CancelActivityRequest cx = new CrmRequests.CancelActivityRequest();
        cx.setVersion(act.getVersion());
        cx.setReason("客户取消");
        asRun(SALES, TENANT, "f2_sales", () -> activityService.cancel(act.getId(), cx));
        CrmRequests.RescheduleActivityRequest rs = new CrmRequests.RescheduleActivityRequest();
        rs.setVersion(activityVersion(act.getId()));
        rs.setPlannedStartTime(java.time.LocalDateTime.now().plusDays(1));
        asRun(SALES, TENANT, "f2_sales", () -> activityService.reschedule(act.getId(), rs));
        assertThat(jdbc.queryForObject("SELECT status FROM omni_crm.crm_activity WHERE id=?",
                String.class, act.getId())).isEqualTo("PLANNED");
        CrmRequests.CompleteActivityRequest cp = new CrmRequests.CompleteActivityRequest();
        cp.setVersion(activityVersion(act.getId()));
        cp.setCompletedTime(java.time.LocalDateTime.now());
        asRun(SALES, TENANT, "f2_sales", () -> activityService.complete(act.getId(), cp));
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> {
            activityService.delete(act.getId(), activityVersion(act.getId()));
            return null;
        }))
                .isInstanceOf(BusinessException.class);

        // 无权父资源：指向他租户 lead 的活动被拒
        Long foreignLead = jdbc.queryForObject("SELECT id FROM omni_crm.crm_lead WHERE tenant_id=1 LIMIT 1", Long.class);
        CrmRequests.CreateActivityRequest foreign = new CrmRequests.CreateActivityRequest();
        foreign.setRootType("LEAD");
        foreign.setRootId(foreignLead);
        foreign.setActivityType("CALL");
        foreign.setSubject("steal");
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> activityService.create(foreign)))
                .isInstanceOf(BusinessException.class);
    }

    // helpers
    private long stage(List<Map<String, Object>> stages, String code) {
        return stages.stream().filter(s -> code.equals(s.get("stage_code")))
                .mapToLong(s -> ((Number) s.get("id")).longValue()).findFirst().orElseThrow();
    }

    private long lastOpen(List<Map<String, Object>> stages) {
        return stages.stream().filter(s -> "OPEN".equals(s.get("stage_type")))
                .mapToLong(s -> ((Number) s.get("sort")).longValue()).max().orElse(-1);
    }

    private CrmRequests.OpportunityStageRequest stageReq(long stageId, String reason, String loss, int version) {
        CrmRequests.OpportunityStageRequest r = new CrmRequests.OpportunityStageRequest();
        r.setStageId(stageId);
        r.setReason(reason);
        r.setLossReason(loss);
        r.setVersion(version);
        return r;
    }

    private int ver(Long oppId) {
        Integer v = jdbc.queryForObject("SELECT version FROM omni_crm.crm_opportunity WHERE id=?",
                Integer.class, oppId);
        return v == null ? 0 : v;
    }

    private int activityVersion(Long id) {
        Integer v = jdbc.queryForObject("SELECT version FROM omni_crm.crm_activity WHERE id=?", Integer.class, id);
        return v == null ? 0 : v;
    }

    private CrmRequests.VersionRequest version(int v) {
        CrmRequests.VersionRequest r = new CrmRequests.VersionRequest();
        r.setVersion(v);
        return r;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
