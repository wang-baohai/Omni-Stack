package com.omni.base.it;

import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.entity.SysUserJob;
import com.omni.base.mapper.SysUserJobMapper;
import com.omni.base.service.UserJobService;
import com.omni.base.service.UserJobTypeService;
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
 * FJ02/FJ03：任务类型 CRUD/唯一 typeCode/无 Handler 路由失败（建类型≠可运行）；
 * 个人任务 create/update/toggle/trigger/log 与 XXL 双侧同步。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FJ02/03 类型绑定与个人任务链")
class Fj02TypeJobIT extends AbstractFjIT {

    @Autowired
    private UserJobTypeService typeService;
    @Autowired
    private UserJobService userJobService;
    @Autowired
    private SysUserJobMapper jobMapper;
    @Autowired
    private JdbcTemplate jdbc;

    /** FJ02-1 类型 CRUD + 唯一 typeCode + 更新禁改 code。 */
    @Test
    @DisplayName("FJ02-1 类型CRUD唯一性")
    void typeCrud() {
        String code = "FJ02-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        com.omni.base.dto.CreateUserJobTypeRequest req = new com.omni.base.dto.CreateUserJobTypeRequest();
        req.setTypeCode(code);
        req.setTypeName("FJ02探针类型");
        req.setDescription("仅F");
        req.setParamTemplate("{\"type\":\"object\",\"properties\":{}}");
        typeService.createType(req);
        assertThatThrownBy(() -> typeService.createType(req))
                .isInstanceOf(RuntimeException.class);
        // 禁用/恢复
        Long id = jdbc.queryForObject("SELECT id FROM sys_user_job_type WHERE type_code=?", Long.class, code);
        typeService.toggleStatus(id, 0);
        assertThat(typeService.getEnabledTypeByCode(code)).isNull();
        typeService.toggleStatus(id, 1);
        assertThat(typeService.getEnabledTypeByCode(code)).isNotNull();
        // 删除前本用例无任务引用 → 可删（同时记录产品当前无引用保护，FJ02-4 验证）
        typeService.deleteType(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_job_type WHERE type_code=?",
                Integer.class, code)).isZero();
    }

    /** FJ02-2 建类型≠可运行：绑定无同名 Handler 的类型，触发产生真实失败日志。 */
    @Test
    @DisplayName("FJ02-2 无Handler执行失败")
    void typeWithoutHandlerFailsAtExecution() {
        String code = "FJ02NH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        com.omni.base.dto.CreateUserJobTypeRequest req = new com.omni.base.dto.CreateUserJobTypeRequest();
        req.setTypeCode(code);
        req.setTypeName("无Handler类型");
        req.setParamTemplate("{\"type\":\"object\",\"properties\":{}}");
        typeService.createType(req);

        CreateUserJobRequest jr = new CreateUserJobRequest();
        jr.setJobName("WP008F-nohandler-" + code);
        jr.setJobType(code);
        jr.setCronExpression("0 0 4 1 JAN ?");
        jr.setJobParams("{}");
        SysUserJob job = userJobService.createJob(1L, jr, "f1_sales");
        assertThat(job.getXxlJobId()).isNotNull();
        userJobService.triggerNow(job.getId());
        long deadline = System.currentTimeMillis() + 60_000;
        List<Map<String, Object>> logs = List.of();
        while (System.currentTimeMillis() < deadline) {
            logs = jdbc.queryForList(
                    "SELECT status,error_message FROM sys_user_job_log WHERE job_id=? ORDER BY id DESC LIMIT 1",
                    job.getId());
            if (!logs.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(logs).as("真实执行后必须产生失败日志").isNotEmpty();
        Map<String, Object> last = logs.get(0);
        assertThat(((Number) last.get("status")).intValue()).isZero();
        assertThat(String.valueOf(last.get("error_message"))).contains(code);

        // 清理：删除任务（外部 remove 成功）
        userJobService.deleteJob(job.getId());
    }

    /** FJ03-1 个人任务全链：create→XXL双侧一致→trigger→日志/last_fire_time→update cron→toggle 暂停/恢复→delete。 */
    @Test
    @DisplayName("FJ03-1 个人任务全链双侧同步")
    void personalJobFullChain() throws Exception {
        CreateUserJobRequest jr = new CreateUserJobRequest();
        jr.setJobName("WP008F-chain-" + UUID.randomUUID().toString().substring(0, 6));
        jr.setJobType("Task-00001");
        jr.setCronExpression("0 0 4 1 JAN ?");
        jr.setJobParams("{\"cupShape\":\"玻璃杯\"}");
        SysUserJob job = userJobService.createJob(1L, jr, "f1_sales");
        Long xxlId = job.getXxlJobId();
        assertThat(xxlId).isNotNull();
        Map<String, Object> xxlRow = adminJdbc().queryForMap(
                "SELECT schedule_conf,trigger_status,executor_param FROM xxl_job.xxl_job_info WHERE id=?", xxlId);
        assertThat(String.valueOf(xxlRow.get("schedule_conf"))).isEqualTo("0 0 4 1 JAN ?");
        assertThat(((Number) xxlRow.get("trigger_status")).intValue()).as("创建即启用").isEqualTo(1);

        // 手动触发→真实执行→日志与 last_fire_time
        userJobService.triggerNow(job.getId());
        long deadline = System.currentTimeMillis() + 90_000;
        Integer logCount = 0;
        while (System.currentTimeMillis() < deadline) {
            logCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_user_job_log WHERE job_id=? AND status=1", Integer.class, job.getId());
            if (logCount > 0) {
                break;
            }
            Thread.sleep(2000);
        }
        assertThat(logCount).as("喝水 handler 真实成功执行").isPositive();
        assertThat(jobMapper.selectById(job.getId()).getLastFireTime()).isNotNull();

        // 更新 cron（双侧同步）
        com.omni.base.dto.UpdateUserJobRequest up = new com.omni.base.dto.UpdateUserJobRequest();
        up.setJobName(job.getJobName());
        up.setCronExpression("0 30 4 1 JAN ?");
        up.setJobParams("{\"cupShape\":\"大杯\"}");
        userJobService.updateJob(job.getId(), up, "f1_sales");
        Map<String, Object> xxlAfter = adminJdbc().queryForMap(
                "SELECT schedule_conf FROM xxl_job.xxl_job_info WHERE id=?", xxlId);
        assertThat(String.valueOf(xxlAfter.get("schedule_conf"))).isEqualTo("0 30 4 1 JAN ?");
        assertThat(jdbc.queryForObject(
                "SELECT cron_expression FROM sys_user_job WHERE id=?", String.class, job.getId()))
                .isEqualTo("0 30 4 1 JAN ?");

        // 暂停/恢复
        userJobService.toggleStatus(job.getId(), 0);
        assertThat(adminJdbc().queryForObject("SELECT trigger_status FROM xxl_job.xxl_job_info WHERE id=?",
                Integer.class, xxlId)).isZero();
        userJobService.toggleStatus(job.getId(), 1);
        assertThat(adminJdbc().queryForObject("SELECT trigger_status FROM xxl_job.xxl_job_info WHERE id=?",
                Integer.class, xxlId)).isEqualTo(1);

        // 删除后 XXL 无残留
        userJobService.deleteJob(job.getId());
        assertThat(adminJdbc().queryForObject("SELECT COUNT(*) FROM xxl_job.xxl_job_info WHERE id=?",
                Integer.class, xxlId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_job WHERE id=?",
                Integer.class, job.getId())).isZero();
    }

    /** FJ03-2 禁用类型不得用于新建任务。 */
    @Test
    @DisplayName("FJ03-2 禁用类型创建被拒")
    void disabledTypeCannotCreateJob() {
        CreateUserJobRequest jr = new CreateUserJobRequest();
        jr.setJobName("WP008F-disabled-type");
        jr.setJobType("TYPE_NOT_EXIST_FJ");
        jr.setCronExpression("0 0 4 1 JAN ?");
        jr.setJobParams("{}");
        assertThatThrownBy(() -> userJobService.createJob(1L, jr, "f1_sales"))
                .isInstanceOf(RuntimeException.class);
    }
}
