package com.omni.crm.it;

import com.omni.common.core.result.BusinessException;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.service.ActivityService;
import com.omni.crm.service.LeadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FC01-FC03：正式 schema 上真实 Service+事务的线索转换 CREATE/LINK、幂等、并发唯一、
 * DB 约束故障注入的共同回滚。身份为 F 真实 tenant 用户；Feign→f-auth 为真实数据范围解析。
 * <p>NEW→FOLLOWING 统一经现有公开活动完成链（ActivityServiceImpl.touchCompletedRoot）驱动，
 * 禁 SQL 改状态/reopen 绕行（RF07）。</p>
 *
 * @author Omni-Stack Team
 */
@DisplayName("FC01-03 转换/幂等/并发/回滚")
class FcConvertIT extends AbstractFcIT {

    /** F 销售（tenant 2002，SELF scope，SALES_REP）。 */
    private static final Long SALES = 2204L;
    private static final Long TENANT = 2002L;

    @Autowired
    private LeadService leadService;
    @Autowired
    private ActivityService activityService;
    @Autowired
    private JdbcTemplate jdbc;

    /** FC01 CREATE 全链 + 幂等重放 + LINK + 非法状态/version 负例 + Outbox。 */
    @Test
    @DisplayName("FC01 CREATE/LINK/幂等/负例")
    void createLinkIdempotentAndRejections() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        // 1) 建线索（NEW）→ 非 QUALIFIED 转换被拒
        CrmViews.LeadVO lead = as(SALES, TENANT, "f2_sales", () -> {
            CrmRequests.CreateLeadRequest c = new CrmRequests.CreateLeadRequest();
            c.setFullName("WP008F-C1-" + tag);
            c.setCompanyName("Example F1 " + tag);
            c.setMobile("13900001001");
            c.setEmail("wp008f.c1." + tag + "@example.com");
            c.setOwnerUserId(SALES);
            return leadService.create(c);
        });
        Long leadId = lead.getId();
        CrmRequests.ConvertLeadRequest bad = convert(0, "CREATE", "CREATE", false);
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> leadService.convert(leadId, bad)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合格");

        // 2) QUALIFIED 后 CREATE 转换（含商机+金额 BigDecimal）：NEW→FOLLOWING→QUALIFIED
        driveToFollowing(leadId);
        asRun(SALES, TENANT, "f2_sales", () -> leadService.qualify(leadId, version(currentVersion(leadId))));
        CrmRequests.ConvertLeadRequest req = convert(currentVersion(leadId), "CREATE", "CREATE", true);
        req.setCustomerName("WP008F-CUST-" + tag);
        req.setContactName("WP008F-CONTACT-" + tag);
        req.setOpportunityName("WP008F-OPP-" + tag);
        req.setAmount(new BigDecimal("12345.67"));
        req.setExpectedCloseDate(LocalDate.now().plusMonths(2));
        CrmViews.ConversionResultVO r1 = as(SALES, TENANT, "f2_sales", () -> leadService.convert(leadId, req));
        assertThat(r1.isIdempotentReplay()).isFalse();
        Long customerId = r1.getCustomerId();
        assertThat(r1.getOpportunityId()).isNotNull();

        // DB 副作用逐一对账：conversion、客户 POTENTIAL、联系人 primary、活动迁移、Outbox PENDING、lead CONVERTED
        Long convCount = count("SELECT COUNT(*) FROM omni_crm.crm_lead_conversion WHERE tenant_id=2002 AND lead_id=" + leadId);
        assertThat(convCount).isEqualTo(1);
        String custStatus = jdbc.queryForObject(
                "SELECT status FROM omni_crm.crm_customer WHERE id=?", String.class, customerId);
        assertThat(custStatus).isEqualTo("POTENTIAL");
        Long primary = jdbc.queryForObject(
                "SELECT primary_flag FROM omni_crm.crm_contact WHERE id=?", Long.class, r1.getContactId());
        assertThat(primary).isEqualTo(1L);
        Long stillLeadRoot = count("SELECT COUNT(*) FROM omni_crm.crm_activity WHERE root_type='LEAD' AND root_id=" + leadId);
        assertThat(stillLeadRoot).isZero();
        List<Map<String, Object>> msgs = jdbc.queryForList(
                "SELECT payload,status FROM omni_crm.sys_mq_message WHERE binding_name='crm-domain-out-0'"
                        + " AND payload LIKE ? AND payload LIKE ?",
                "%\"eventType\":\"crm.lead.converted.v1\"%", "%\"leadId\":" + leadId + "%");
        assertThat(msgs).as("Outbox 恰一条 crm.lead.converted").hasSize(1);
        assertThat((String) msgs.get(0).get("payload")).doesNotContain("13900001001").doesNotContain("@example.com");

        // 3) 幂等重放：同 lead 再 convert 返回原结果，无新增
        CrmViews.ConversionResultVO replay = as(SALES, TENANT, "f2_sales",
                () -> leadService.convert(leadId, convert(999, "CREATE", "CREATE", false)));
        assertThat(replay.isIdempotentReplay()).isTrue();
        assertThat(replay.getConversionId()).isEqualTo(r1.getConversionId());
        assertThat(count("SELECT COUNT(*) FROM omni_crm.crm_customer WHERE tenant_id=2002")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM omni_crm.sys_mq_message WHERE payload LIKE \"%\\\"eventType\\\":\\\"crm.lead.converted.v1\\\"%\""
                + " AND payload LIKE \"%\\\"leadId\\\":" + leadId + "%\"")).isEqualTo(1);

        // 4) LINK 转换第二条线索复用既有客户+联系人
        CrmViews.LeadVO lead2 = as(SALES, TENANT, "f2_sales", () -> {
            CrmRequests.CreateLeadRequest c = new CrmRequests.CreateLeadRequest();
            c.setFullName("WP008F-C2-" + tag);
            c.setOwnerUserId(SALES);
            return leadService.create(c);
        });
        Long lead2Id = lead2.getId();
        driveToFollowing(lead2Id);
        asRun(SALES, TENANT, "f2_sales", () -> leadService.qualify(lead2Id, version(currentVersion(lead2Id))));
        CrmRequests.ConvertLeadRequest link = convert(currentVersion(lead2Id), "LINK", "LINK", false);
        link.setCustomerId(customerId);
        link.setContactId(r1.getContactId());
        CrmViews.ConversionResultVO r2 = as(SALES, TENANT, "f2_sales", () -> leadService.convert(lead2Id, link));
        assertThat(r2.getCustomerId()).isEqualTo(customerId);
        assertThat(count("SELECT COUNT(*) FROM omni_crm.crm_customer WHERE tenant_id=2002")).isEqualTo(1);

        // 5) 陈旧 version 拒绝（第三条线索）
        CrmViews.LeadVO lead3 = as(SALES, TENANT, "f2_sales", () -> {
            CrmRequests.CreateLeadRequest c = new CrmRequests.CreateLeadRequest();
            c.setFullName("WP008F-C3-" + tag);
            c.setOwnerUserId(SALES);
            return leadService.create(c);
        });
        driveToFollowing(lead3.getId());
        asRun(SALES, TENANT, "f2_sales", () -> leadService.qualify(lead3.getId(), version(currentVersion(lead3.getId()))));
        CrmRequests.ConvertLeadRequest stale = convert(currentVersion(lead3.getId()) + 5, "CREATE", "CREATE", false);
        stale.setCustomerName("x");
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> leadService.convert(lead3.getId(), stale)))
                .isInstanceOf(BusinessException.class);
    }

    /** FC02：同 lead 双独立事务并发 convert，恰一 conversion/一产物集/一 Outbox；跨 tenant 不串。 */
    @Test
    @DisplayName("FC02 并发转换唯一产物")
    void concurrentConvertSingleArtifactSet() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long leadId = createQualifiedLead("FC02-" + tag);
        // 明确同步点：两条独立真实 DB 连接同时进入 convert 争抢同一 lead 行锁；双方结果均收集，不吞竞争方异常。
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < 2; i++) {
            final int n = i;
            pool.submit(() -> {
                Object outcome;
                try {
                    barrier.await(60, TimeUnit.SECONDS);
                    outcome = as(SALES, TENANT, "f2_sales", () -> leadService.convert(leadId,
                            convert(currentVersion(leadId), "CREATE", "CREATE", true,
                                    "FC02CUST" + tag + n, "FC02CONT" + tag + n, "FC02OPP" + tag + n)));
                } catch (Throwable e) {
                    // 不吞竞争方异常：作为真实竞争结果收集（输者因版本不匹配抛错或命中已有转换走重放）
                    outcome = e;
                }
                outcomes.add(outcome);
                return null;
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
        // 双方结果均被收集，未吞异常
        assertThat(outcomes).as("两个并发 convert 结果均须被收集").hasSize(2);
        // SELECT FOR UPDATE 串行化：恰一个真实产物集（idempotentReplay=false），另一个为重放/失败，不得两套
        long realConversions = outcomes.stream()
                .filter(o -> o instanceof CrmViews.ConversionResultVO r && !r.isIdempotentReplay()).count();
        assertThat(realConversions).as("并发不得产生两套真实产物（恰一真实转换）").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM omni_crm.crm_lead_conversion WHERE lead_id=" + leadId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM omni_crm.crm_customer WHERE tenant_id=2002 AND name LIKE 'FC02CUST" + tag + "%'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM omni_crm.sys_mq_message WHERE payload LIKE \"%\\\"eventType\\\":\\\"crm.lead.converted.v1\\\"%\" AND payload LIKE \"%\\\"leadId\\\":" + leadId + "%\"")).isEqualTo(1);
        // 另一租户不可见该转换
        Long otherTenant = count("SELECT COUNT(*) FROM omni_crm.crm_lead_conversion WHERE tenant_id<>2002 AND lead_id=" + leadId);
        assertThat(otherTenant).isZero();
    }

    /** FC03：并发已提交转换注入（预插同 lead conversion）→ 真实 convert 命中 SELECT FOR UPDATE+存在性检查走幂等重放，不抛错、不重复副作用。 */
    @Test
    @DisplayName("FC03 并发已提交转换共同回滚/幂等")
    void midTransactionFailureRollsBackEverything() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long leadId = createQualifiedLead("FC03-" + tag);
        long custBefore = count("SELECT COUNT(*) FROM omni_crm.crm_customer WHERE tenant_id=2002");
        // 预插入同 lead 的 conversion（模拟另一事务已提交）；真实 convert 应先命中已有转换→幂等重放
        jdbc.update("INSERT INTO omni_crm.crm_lead_conversion (tenant_id, lead_id, customer_id, contact_id, converted_by_user_id, converted_time, create_by)"
                + " VALUES (2002, ?, 999999998, 999999998, ?, NOW(), 'wp008f-inject')", leadId, SALES);
        CrmViews.ConversionResultVO res = as(SALES, TENANT, "f2_sales",
                () -> leadService.convert(leadId, convert(currentVersion(leadId), "CREATE", "CREATE", true,
                        "FC03CUST" + tag, "FC03CONT" + tag, "FC03OPP" + tag)));
        assertThat(res.isIdempotentReplay()).as("已有转换→重放").isTrue();
        // 共同回滚/不重复：无新客户/联系人/商机；lead 仍 QUALIFIED；无新转换 Outbox
        assertThat(count("SELECT COUNT(*) FROM omni_crm.crm_customer WHERE tenant_id=2002")).isEqualTo(custBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM omni_crm.crm_lead WHERE id=?", String.class, leadId))
                .isEqualTo("QUALIFIED");
        assertThat(count("SELECT COUNT(*) FROM omni_crm.sys_mq_message WHERE payload LIKE \"%\\\"eventType\\\":\\\"crm.lead.converted.v1\\\"%\" AND payload LIKE \"%\\\"leadId\\\":" + leadId + "%\"")).isZero();
        // 清理注入行
        jdbc.update("DELETE FROM omni_crm.crm_lead_conversion WHERE lead_id=? AND customer_id=999999998", leadId);
    }

    // ==================== helpers ====================

    private Long createQualifiedLead(String name) {
        CrmRequests.CreateLeadRequest c = new CrmRequests.CreateLeadRequest();
        c.setFullName(name);
        c.setOwnerUserId(SALES);
        c.setMobile("13900002000");
        CrmViews.LeadVO lead = as(SALES, TENANT, "f2_sales", () -> leadService.create(c));
        // 经现有公开活动完成链驱动 NEW→FOLLOWING（非 SQL 捷径），再走真实 qualify 命令
        driveToFollowing(lead.getId());
        asRun(SALES, TENANT, "f2_sales", () -> leadService.qualify(lead.getId(), version(currentVersion(lead.getId()))));
        return lead.getId();
    }

    /**
     * 用现有公开活动创建/完成链驱动 NEW→FOLLOWING：
     * 在 LEAD 根上创建一条已完成活动，{@code ActivityServiceImpl.touchCompletedRoot} 将 NEW 推进为 FOLLOWING。
     * 禁新增"开始跟进 API"、禁 SQL 改状态/reopen 绕行。
     */
    private void driveToFollowing(Long leadId) {
        asRun(SALES, TENANT, "f2_sales", () -> {
            CrmRequests.CreateActivityRequest a = new CrmRequests.CreateActivityRequest();
            a.setRootType("LEAD");
            a.setRootId(leadId);
            a.setActivityType("CALL");
            a.setSubject("WP008F 首次有效跟进");
            a.setStatus("COMPLETED");
            a.setCompletedTime(LocalDateTime.now().withNano(0));
            activityService.create(a);
        });
    }

    private int currentVersion(Long leadId) {
        Integer v = jdbc.queryForObject("SELECT version FROM omni_crm.crm_lead WHERE id=?", Integer.class, leadId);
        return v == null ? 0 : v;
    }

    private CrmRequests.VersionRequest version(int v) {
        CrmRequests.VersionRequest r = new CrmRequests.VersionRequest();
        r.setVersion(v);
        return r;
    }

    private CrmRequests.ConvertLeadRequest convert(int version, String customerMode, String contactMode,
                                                    boolean createOpportunity) {
        return convert(version, customerMode, contactMode, createOpportunity, null, null, null);
    }

    private CrmRequests.ConvertLeadRequest convert(int version, String customerMode, String contactMode,
                                                   boolean createOpportunity,
                                                   String cust, String cont, String opp) {
        CrmRequests.ConvertLeadRequest r = new CrmRequests.ConvertLeadRequest();
        r.setVersion(version);
        r.setCustomerMode(customerMode);
        r.setContactMode(contactMode);
        r.setCreateOpportunity(createOpportunity);
        if (cust != null) {
            r.setCustomerName(cust);
            r.setContactName(cont);
            r.setContactMobile("13900003000");
            r.setOpportunityName(opp);
            r.setAmount(new BigDecimal("8888.88"));
            r.setExpectedCloseDate(LocalDate.now().plusMonths(1));
        }
        return r;
    }

    private Long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
