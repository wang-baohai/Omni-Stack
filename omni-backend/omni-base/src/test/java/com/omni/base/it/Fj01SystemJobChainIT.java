package com.omni.base.it;

import com.omni.base.dto.RegisterSystemJobRequest;
import com.omni.common.job.SystemJobRegistry;
import com.omni.base.service.SystemJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FJ01：系统任务双注解发现 → 真实注册 → 启用 → 计划/手动触发 → 停止 → 注销 全链（生产 handler + F 合成数据）。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FJ01 系统任务真实全链")
class Fj01SystemJobChainIT extends AbstractFjIT {

    @Autowired
    private SystemJobService systemJobService;
    @Autowired
    private SystemJobRegistry registry;
    @Autowired
    private JdbcTemplate jdbc;

    /** 双注解可见 + operLogArchiveHandler 全生命周期真实链。 */
    @Test
    @DisplayName("FJ01-1 注册/触发/日志/注销")
    void fullChainWithRealXxl() throws Exception {
        // 1) 双注解发现：注册表必须能看到生产 handler
        assertThat(registry.getAll()).containsKeys("operLogArchiveHandler", "mqRelayHandler");

        // 2) F 合成可归档日志行（隔离数据，只作用于 F；列名对齐正式 schema）
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO sys_oper_log (event_id,tenant_id,oper_username,oper_time,module,oper_type,"
                    + "request_method,request_url,response_status) VALUES (CONCAT('wp008f-', UUID()),1,'f1_sales',"
                    + " DATE_SUB(NOW(), INTERVAL 400 DAY),'WP008F-TEST','OTHER','GET','/api/wp008f/diag',200)");
        }
        Long archBefore = jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log_archive", Long.class);

        // 3) 正式注册（cron 指向安全未来，再手动 trigger 验证执行链）
        // RF05：register() 返回 void，须按 before/after 差集绑定“本次新建”的精确 XXL ID，不得用 LIMIT 1 命中既有同名任务。
        java.util.Set<Integer> idsBefore = new java.util.HashSet<>(handlerJobIds());
        RegisterSystemJobRequest req = new RegisterSystemJobRequest();
        req.setHandlerName("operLogArchiveHandler");
        req.setCron("0 0 4 1 JAN ?");
        req.setParams("{\"retentionDays\":180}");
        systemJobService.register(req);
        java.util.List<Integer> idsAfter = handlerJobIds();
        Integer xxlJobId = idsAfter.stream().filter(id -> !idsBefore.contains(id)).findFirst()
                .orElseGet(() -> idsAfter.size() == 1 ? idsAfter.get(0) : null);
        assertThat(xxlJobId).as("必须绑定本次注册返回/新建的精确 XXL ID，非任意同名行").isNotNull();

        // 4) 启用（start）→ 状态 RUNNING（均作用于同一精确 ID）
        systemJobService.start(xxlJobId);
        assertThat(statusOf()).isEqualTo("RUNNING");

        // 5) 手动 trigger → 真实 dispatch/handle → 归档表增长
        systemJobService.trigger(xxlJobId, "{\"retentionDays\":180}");
        long deadline = System.currentTimeMillis() + 60_000;
        Long archAfter = archBefore;
        while (System.currentTimeMillis() < deadline) {
            archAfter = jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log_archive", Long.class);
            if (archAfter > archBefore) {
                break;
            }
            Thread.sleep(2000);
        }
        assertThat(archAfter).as("生产 handler 真实归档了 F 合成行").isGreaterThan(archBefore);

        // 6) xxl_job_log 有真实调度/执行记录
        Integer logs = adminJdbc().queryForObject(
                "SELECT COUNT(*) FROM xxl_job.xxl_job_log WHERE job_id=?", Integer.class, xxlJobId);
        assertThat(logs).as("F admin DB 中真实派发日志").isPositive();

        // 7) 停止 → 注销 → admin 不再有该任务
        systemJobService.stop(xxlJobId);
        assertThat(statusOf()).isEqualTo("STOPPED");
        systemJobService.unregister(xxlJobId);
        Integer remaining = adminJdbc().queryForObject(
                "SELECT COUNT(*) FROM xxl_job.xxl_job_info WHERE id=?", Integer.class, xxlJobId);
        assertThat(remaining).isZero();
    }

    /** 当前 omni-base 执行器组下 operLogArchiveHandler 的全部 XXL 任务 ID（用于 before/after 差集绑定精确 ID）。 */
    private java.util.List<Integer> handlerJobIds() {
        return adminJdbc().queryForList(
                "SELECT id FROM xxl_job.xxl_job_info WHERE executor_handler='operLogArchiveHandler'"
                        + " AND job_group=(SELECT id FROM xxl_job.xxl_job_group WHERE app_name='omni-base')", Integer.class);
    }

    private String statusOf() {
        return systemJobService.listAll().stream()
                .filter(v -> "operLogArchiveHandler".equals(v.getHandlerName()))
                .map(v -> String.valueOf(v.getStatus()))
                .findFirst().orElse("ABSENT");
    }

    /** listAll 不注入共享 M：仅 F admin。 */
    @Test
    @DisplayName("FJ01-2 执行器组为F独立注册")
    void groupIsFScoped() {
        List<String> groups = adminJdbc().queryForList(
                "SELECT app_name FROM xxl_job.xxl_job_group WHERE app_name='omni-base'", String.class);
        assertThat(groups).containsExactly("omni-base");
        // F admin 内 omni-base 组注册地址必须是测试执行器（host.docker.internal:19901），不是 M 的 omni-base:9901
        String addr = adminJdbc().queryForObject(
                "SELECT CONCAT(IFNULL(address_list,'')) FROM xxl_job.xxl_job_group WHERE app_name='omni-base'",
                String.class);
        assertThat(addr).doesNotContain("omni-base:9901");
    }
}
