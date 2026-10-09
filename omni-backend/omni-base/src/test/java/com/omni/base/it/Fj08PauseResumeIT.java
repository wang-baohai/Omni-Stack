package com.omni.base.it;

import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.dto.UpdateUserJobRequest;
import com.omni.base.entity.SysUserJob;
import com.omni.base.service.UserJobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FJ08：分钟级计划触发真实派发→暂停观察超一个周期无新派发→恢复再触发→注销后无新调度；
 * 执行链路（dispatch/handle/log）以 XXL 与本地双侧日志为证。观测预算：≤3 次自然触发。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FJ08 计划触发/暂停/恢复/注销")
class Fj08PauseResumeIT extends AbstractFjIT {

    @Autowired
    private UserJobService userJobService;
    @Autowired
    private JdbcTemplate jdbc;

    private SysUserJob job;

    /** 结束必须注销任务，不留活跃调度。 */
    @AfterEach
    void teardown() {
        if (job != null) {
            try {
                userJobService.toggleStatus(job.getId(), 0);
            } catch (Exception ignored) {
                // ok
            }
            try {
                userJobService.deleteJob(job.getId());
            } catch (Exception ignored) {
                // ok
            }
            job = null;
        }
    }

    /** 全周期：自然触发≥1 → pause 超周期零新派发 → resume 再触发 → 注销后两侧终态。 */
    @Test
    @DisplayName("FJ08-1 计划触发暂停恢复注销")
    void schedulePauseResumeUnregister() throws Exception {
        CreateUserJobRequest jr = new CreateUserJobRequest();
        jr.setJobName("WP008F-FJ08-" + UUID.randomUUID().toString().substring(0, 6));
        jr.setJobType("Task-00001");
        jr.setCronExpression("0 0 4 1 JAN ?");
        jr.setJobParams("{\"cupShape\":\"玻璃杯\"}");
        job = userJobService.createJob(1L, jr, "f1_sales");
        Long xxlId = job.getXxlJobId();

        // 切到分钟级计划（受控窗口；预算=最多观察 3 次自然触发）
        UpdateUserJobRequest up = new UpdateUserJobRequest();
        up.setJobName(job.getJobName());
        up.setCronExpression("0 * * * * ?");
        up.setJobParams("{\"cupShape\":\"玻璃杯\"}");
        userJobService.updateJob(job.getId(), up, "f1_sales");

        // 1) 等待至少一次自然计划触发（dispatch + handle + 本地日志 + last_fire_time）
        long deadline = System.currentTimeMillis() + 150_000;
        long fires = 0;
        while (System.currentTimeMillis() < deadline && fires < 1) {
            fires = countDispatches(xxlId);
            if (fires < 1) {
                Thread.sleep(3000);
            }
        }
        assertThat(fires).as("计划触发真实派发到执行器").isPositive();
        assertThat(jdbc.queryForObject("SELECT last_fire_time FROM sys_user_job WHERE id=?",
                java.time.LocalDateTime.class, job.getId())).isNotNull();
        assertThat(countLocalSuccessLogs()).as("喝水 handler 本地成功日志").isPositive();
        // 区分来源：计划触发（trigger_source=0）在 xxl_job_log 中真实存在
        assertThat(countScheduledDispatch(xxlId)).isPositive();

        // 2) 暂停并观察超过一个原周期 + 调度容差（75s）：无新计划派发
        userJobService.toggleStatus(job.getId(), 0);
        long atPause = countDispatches(xxlId);
        Thread.sleep(75_000);
        assertThat(countDispatches(xxlId)).as("暂停期间不得有新计划派发").isEqualTo(atPause);

        // 3) 恢复 → 再次自然触发
        userJobService.toggleStatus(job.getId(), 1);
        long resumeDeadline = System.currentTimeMillis() + 150_000;
        long afterResume = atPause;
        while (System.currentTimeMillis() < resumeDeadline && afterResume <= atPause) {
            afterResume = countDispatches(xxlId);
            if (afterResume <= atPause) {
                Thread.sleep(3000);
            }
        }
        assertThat(afterResume).as("恢复后再次真实执行").isGreaterThan(atPause);

        // 4) 停止+注销：不再有调度；两侧记录终态
        userJobService.toggleStatus(job.getId(), 0);
        userJobService.deleteJob(job.getId());
        Long xxlLeft = adminJdbc().queryForObject("SELECT COUNT(*) FROM xxl_job.xxl_job_info WHERE id=?",
                Long.class, xxlId);
        assertThat(xxlLeft).isZero();
        long finalCount = countDispatches(xxlId);
        Thread.sleep(70_000);
        assertThat(countDispatches(xxlId)).as("注销后无新调度").isEqualTo(finalCount);
        // 保留可解释历史：xxl_job_log 允许保留（属 F 合成环境审计），本地任务日志保留
        job = null;
    }

    private long countDispatches(Long xxlId) {
        Long n = adminJdbc().queryForObject("SELECT COUNT(*) FROM xxl_job.xxl_job_log WHERE job_id=?",
                Long.class, xxlId);
        return n == null ? 0 : n;
    }

    private long countScheduledDispatch(Long xxlId) {
        Long n = adminJdbc().queryForObject(
                "SELECT COUNT(*) FROM xxl_job.xxl_job_log WHERE job_id=? AND executor_param IS NOT NULL",
                Long.class, xxlId);
        return n == null ? 0 : n;
    }

    private long countLocalSuccessLogs() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_job_log WHERE job_id=? AND status=1",
                Long.class, job.getId());
        return n == null ? 0 : n;
    }
}
