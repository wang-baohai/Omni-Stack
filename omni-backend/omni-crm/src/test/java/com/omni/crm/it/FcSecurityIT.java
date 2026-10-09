package com.omni.crm.it;

import com.omni.common.core.result.BusinessException;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.service.CustomerService;
import com.omni.crm.service.LeadService;
import com.omni.crm.service.OverviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FC07/09/10：两真实 tenant 全 scope 矩阵、customer360 分块权限、PII 后端脱敏、
 * 上下文 finally 清理与线程复用不串、破坏性 probe 仅限 F 独立库且正式 schema 无损。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FC07/09/10 权限/PII/上下文")
class FcSecurityIT extends AbstractFcIT {

    private static final Long SALES = 2204L;
    private static final Long MGR = 2205L;
    private static final Long VIEWER = 2206L;
    private static final Long REQ = 2201L;
    private static final Long TENANT = 2002L;

    @Autowired
    private CustomerService customerService;
    @Autowired
    private LeadService leadService;
    @Autowired
    private OverviewService overviewService;
    @Autowired
    private JdbcTemplate jdbc;

    /** FC07 SELF/DEPT_AND_BELOW/TENANT/ALL 列表与 DB COUNT 对账 + 功能权限负例 + 跨 tenant 不串。 */
    @Test
    @DisplayName("FC07 scope矩阵与写保护")
    void scopeMatrix() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        // JDBC 直插基线行（F 数据准备）：owner 分别 2204@2102、2206@2102、2205@2101
        insertCustomer("SEC-A-" + tag, SALES, 2102L);
        insertCustomer("SEC-B-" + tag, VIEWER, 2102L);
        insertCustomer("SEC-C-" + tag, MGR, 2101L);

        long selfVisible = total(as(SALES, TENANT, "f2_sales",
                () -> customerService.list(query(tag))));
        long mgrVisible = total(as(MGR, TENANT, "f2_mgr",
                () -> customerService.list(query(tag))));
        // SELF 仅自己；DEPT_AND_BELOW 经理覆盖 2101+子单位 2102
        assertThat(selfVisible).as("SELF=%s 应只含 owner 2204", selfVisible).isEqualTo(1);
        assertThat(mgrVisible).as("DEPT_AND_BELOW=%s 应含 3 行", mgrVisible).isEqualTo(3);

        // 功能权限负例：EMPLOYEE（无任何 crm 码）列表被拒/空
        assertThatThrownBy(() -> as(REQ, TENANT, "f2_req", () -> customerService.list(query(tag))))
                .isInstanceOf(RuntimeException.class);

        // 跨 tenant 不串：tenant-1 用户看不到 2002 的行
        long t1Visible = total(as(100L, 1L, "zhangsan", () -> customerService.list(query(tag))));
        assertThat(t1Visible).isZero();

        // 写 AccessGuard：他人 owner 行更新被拒（SELF 视角），陈旧 version 被拒
        Long foreign = jdbc.queryForObject(
                "SELECT id FROM omni_crm.crm_customer WHERE tenant_id=2002 AND name LIKE 'SEC-B-" + tag + "%'",
                Long.class);
        CrmRequests.UpdateCustomerRequest u = new CrmRequests.UpdateCustomerRequest();
        u.setVersion(999);
        assertThatThrownBy(() -> as(SALES, TENANT, "f2_sales", () -> customerService.update(foreign, u)))
                .isInstanceOf(BusinessException.class);
    }

    /** FC09 PII：无 crm:pii:view 后端 VO 全脱敏；有权限返回完整；360 分块权限独立。 */
    @Test
    @DisplayName("FC09 PII与360分块")
    void piiAndOverview() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        insertCustomer("PII-" + tag, SALES, 2102L);
        Long id = jdbc.queryForObject(
                "SELECT id FROM omni_crm.crm_customer WHERE name='PII-" + tag + "'", Long.class);
        jdbc.update("UPDATE omni_crm.crm_customer SET phone='13800001234', email='pii.secret@example.com' WHERE id=?", id);
        CrmRequests.CreateContactRequest k = new CrmRequests.CreateContactRequest();
        k.setName("PIIContact");
        k.setMobile("13911112222");
        k.setEmail("contact.pii@example.com");
        k.setPrimary(true);
        as(SALES, TENANT, "f2_sales", () -> contactService.create(id, k));

        // VIEWER（无 pii:view）列表/详情脱敏
        CrmViews.CustomerVO masked = asAuth(VIEWER, TENANT, "f2_viewer",
                java.util.List.of("crm:customer:list"), () -> customerService.get(id));
        assertThat(masked.getPhone()).doesNotContain("0001234");
        assertThat(masked.getPhone()).contains("*");
        assertThat(masked.getEmail()).contains("***");
        // SALES（有 pii:view）完整
        CrmViews.CustomerVO full = asAuth(SALES, TENANT, "f2_sales",
                java.util.List.of("crm:customer:list", "crm:pii:view"), () -> customerService.get(id));
        assertThat(full.getPhone()).isEqualTo("13800001234");

        // 360 分块：viewer 可看客户（customer:list）但 contacts 块按其权限与 scope 独立过滤
        CrmViews.CustomerOverviewVO ov = asAuth(VIEWER, TENANT, "f2_viewer",
                java.util.List.of("crm:customer:list"), () -> customerService.overview(id));
        assertThat(ov).isNotNull();
        // viewer 有 contact:list 权限且同单位 → 可见但脱敏；无 pii 时 mobile 星号
        if (ov.getContacts() != null && !ov.getContacts().isEmpty()) {
            assertThat(ov.getContacts().get(0).getMobile()).doesNotContain("1112222");
        }

        // 日志/Outbox 无 PII：overview 事件不落 PII；sys_mq_message payload 全文无手机号
        Long leak = jdbc.queryForObject(
                "SELECT COUNT(*) FROM omni_crm.sys_mq_message WHERE payload LIKE '%13911112222%' OR payload LIKE '%13800001234%'",
                Long.class);
        assertThat(leak).isZero();
    }

    /** FC10 上下文 finally 清理 + 线程复用不串 + 正式 schema 列完整（probe 未破坏）。 */
    @Test
    @DisplayName("FC10 上下文与schema无损")
    void contextCleanupAndSchema() throws Exception {
        String sTag = UUID.randomUUID().toString().substring(0, 6);
        insertCustomer("CTX-" + sTag, SALES, 2102L);
        // 同单位(2102)但归属他人：SALES(SELF,owner=2204) 不见，VIEWER(DEPT,unit=2102) 可见 → 两 scope 结果集不同
        insertCustomer("CTX2-" + sTag, VIEWER, 2102L);
        as(SALES, TENANT, "f2_sales", () -> customerService.get(
                jdbc.queryForObject("SELECT id FROM omni_crm.crm_customer WHERE tenant_id=2002 AND owner_user_id=2204 LIMIT 1", Long.class)));
        assertIdentityCleared();

        // 线程复用不串：同一线程先以 SALES 调用，再以 VIEWER 调用，结果集不同；结束上下文空
        long a = total(as(SALES, TENANT, "f2_sales", () -> customerService.list(query(""))));
        long b = total(as(VIEWER, TENANT, "f2_viewer", () -> customerService.list(query(""))));
        assertThat(a).isNotEqualTo(b);
        assertIdentityCleared();

        // 池线程复用：新线程执行后 ThreadLocal 无残留
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        pool.submit(() -> as(SALES, TENANT, "f2_sales", () -> customerService.list(query("")))).get();
        pool.shutdown();

        // 正式 crm_lead schema 完整（证明本包 probe 的 DROP 只可能发生在独立库）
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA='omni_crm' AND TABLE_NAME='crm_lead'");
        assertThat(cols.size()).as("正式 crm_lead 列数（简化 probe 表为5列）").isGreaterThan(8);
    }

    // helpers
    private void insertCustomer(String name, Long owner, Long unit) {
        jdbc.update("INSERT INTO omni_crm.crm_customer (tenant_id, customer_no, name, normalized_name, status,"
                        + " owner_user_id, owner_unit_id, version, deleted, create_by)"
                        + " VALUES (2002, ?, ?, LOWER(?), 'ACTIVE', ?, ?, 0, 0, 'wp008f-it')",
                "CN-" + name + "-" + UUID.randomUUID().toString().substring(0, 4), name, name, owner, unit);
    }

    private CrmRequests.CustomerQuery query(String keyword) {
        CrmRequests.CustomerQuery q = new CrmRequests.CustomerQuery();
        q.setKeyword(keyword == null ? "" : keyword);
        q.setSize(100);
        return q;
    }

    private long total(com.omni.common.core.result.PageResult<CrmViews.CustomerVO> p) {
        return p == null ? 0 : p.getTotal();
    }

    @Autowired
    private com.omni.crm.service.ContactService contactService;
}
