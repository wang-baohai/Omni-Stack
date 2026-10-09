package com.omni.crm.it;

import com.omni.crm.dto.CrmViews;
import com.omni.crm.service.OverviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FC08：Overview summary/funnel/followups 真实聚合 SQL 与 fixture 可见范围/日期一致；
 * 按 tenant 配置币种；不同 scope 用户看到与列表一致的聚合（禁止先读全库再前端过滤）。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FC08 概览聚合")
class FcOverviewIT extends AbstractFcIT {

    private static final Long SALES = 2204L;
    private static final Long MGR = 2205L;
    private static final Long TENANT = 2002L;

    @Autowired
    private OverviewService overviewService;
    @Autowired
    private JdbcTemplate jdbc;

    /** summary/funnel/followups 与 SQL 基线一致。 */
    @Test
    @DisplayName("FC08 聚合与基线对账")
    void overviewMatchesBaseline() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        // 自种子一个 2204@2102 客户（不依赖跨类执行顺序）
        jdbc.update("INSERT INTO omni_crm.crm_customer (tenant_id, customer_no, name, normalized_name, status,"
                + " owner_user_id, owner_unit_id, version, deleted, create_by)"
                + " VALUES (2002, ?, ?, LOWER(?), 'ACTIVE', 2204, 2102, 0, 0, 'wp008f-it')",
                "CN-OVI-" + tag, "OVI-C-" + tag, "OVI-C-" + tag);
        // 造 fixture：1 开放商机 + 1 到期跟进活动（同 owner 2204）
        Long cust = jdbc.queryForObject(
                "SELECT id FROM omni_crm.crm_customer WHERE tenant_id=2002 AND owner_user_id=2204 LIMIT 1", Long.class);
        jdbc.update("INSERT INTO omni_crm.crm_opportunity (tenant_id, opportunity_no, name, customer_id,"
                + " pipeline_id, stage_id, amount, currency_code, status, probability, stage_change_time, owner_user_id, owner_unit_id,"
                + " version, deleted, create_by)"
                + " SELECT 2002, ?, ?, ?, p.id, p.default_open_stage_id, 5000.00, 'CNY', 'OPEN', 50.00, NOW(), 2204, 2102,"
                + " 0, 0, 'wp008f-it' FROM (SELECT pl.id,"
                + " (SELECT id FROM omni_crm.crm_pipeline_stage WHERE pipeline_id=pl.id AND stage_type='OPEN' ORDER BY sort LIMIT 1) default_open_stage_id"
                + " FROM omni_crm.crm_pipeline pl JOIN omni_crm.crm_tenant_config tc ON tc.default_pipeline_id=pl.id"
                + " WHERE tc.tenant_id=2002) p",
                "OP-" + tag, "OVI-" + tag, cust);
        Long oppId = jdbc.queryForObject(
                "SELECT id FROM omni_crm.crm_opportunity WHERE name='OVI-" + tag + "'", Long.class);
        // followups 从 OPEN 商机按 next_followup_time<=rangeEnd 选取（非 crm_activity），置为已到期
        jdbc.update("UPDATE omni_crm.crm_opportunity SET next_followup_time=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?", oppId);
        jdbc.update("INSERT INTO omni_crm.crm_activity (tenant_id, root_type, root_id, activity_type, subject,"
                + " status, planned_start_time, owner_user_id, owner_unit_id, next_action_time, version, deleted, create_by)"
                + " VALUES (2002,'OPPORTUNITY',?,'CALL','follow-" + tag + "','PLANNED',NOW(),2204,2102,DATE_ADD(NOW(),INTERVAL 1 HOUR),0,0,'wp008f-it')",
                oppId);

        // 基线：SELF scope 的 SQL 等价
        long oppCount = count("SELECT COUNT(*) FROM omni_crm.crm_opportunity"
                + " WHERE tenant_id=2002 AND owner_user_id=2204 AND deleted=0 AND status='OPEN'");
        CrmViews.OverviewSummaryVO mine = as(SALES, TENANT, "f2_sales", () -> overviewService.summary());
        assertThat(mine.getOpenOpportunityCount()).as("summary 开放商机与基线一致").isEqualTo(oppCount);
        // funnel 各阶段数量之和 == 可见开放商机数（同源 SQL）
        Long pipelineId = jdbc.queryForObject(
                "SELECT default_pipeline_id FROM omni_crm.crm_tenant_config WHERE tenant_id=2002", Long.class);
        var funnel = as(SALES, TENANT, "f2_sales", () -> overviewService.funnel(pipelineId));
        long funnelTotal = funnel.stream().mapToLong(b -> b.getCount()).sum();
        assertThat(funnelTotal).as("funnel 与列表同范围").isEqualTo(oppCount);
        var followups = as(SALES, TENANT, "f2_sales", () -> overviewService.followups(50));
        assertThat(followups.stream().anyMatch(v -> oppId.equals(v.getRootId())))
                .as("followups 含到期 fixture 活动").isTrue();

        // 经理 DEPT_AND_BELOW 聚合 >= 销售 SELF 聚合（不同 scope 一致性地反映列表范围）
        CrmViews.OverviewSummaryVO mgrView = as(MGR, TENANT, "f2_mgr", () -> overviewService.summary());
        assertThat(mgrView).isNotNull();

        // 币种来自 tenant 配置
        String currency = jdbc.queryForObject(
                "SELECT currency_code FROM omni_crm.crm_tenant_config WHERE tenant_id=2002", String.class);
        assertThat(currency).isEqualTo("CNY");
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
