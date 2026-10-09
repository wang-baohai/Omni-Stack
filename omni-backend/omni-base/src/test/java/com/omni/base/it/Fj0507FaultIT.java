package com.omni.base.it;

import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.entity.SysUserJob;
import com.omni.base.service.UserJobService;
import com.omni.base.service.impl.UserJobServiceImpl;
import com.omni.common.job.XxlJobAdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FJ05-07：注册前明确失败、注册后丢响应、update/toggle/delete 外部拒绝/不可达/业务错误的
 * 一致性与恢复（真实 XXL Admin + 测试域故障代理；先复现现状缺陷，修复后回归）。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FJ05-07 外部故障一致性")
class Fj0507FaultIT extends AbstractFjIT {

    @Autowired
    private UserJobService userJobService;
    @Autowired
    private JdbcTemplate jdbc;

    private UserJobServiceImpl serviceImpl;
    private XxlJobAdminClient originalClient;
    private FaultProxy proxy;
    private final SysUserJob[] created = new SysUserJob[1];
    /** 本用例最新创建的任务名，用于结束后按精确名清理自有残留（避免跨用例污染与未解释孤儿）。 */
    private String lastJobName;

    /** 注入指向故障代理的真实客户端（客户端本身是生产类，非 mock）。 */
    private void routeThroughProxy(FaultProxy.Mode mode) throws Exception {
        serviceImpl = (UserJobServiceImpl) userJobService;
        if (originalClient == null) {
            originalClient = (XxlJobAdminClient) ReflectionTestUtils.getField(serviceImpl, "xxlJobAdminClient");
        }
        proxy = new FaultProxy("127.0.0.1", 38080);
        proxy.setMode(mode);
        XxlJobAdminClient proxied = new XxlJobAdminClient(
                "http://127.0.0.1:" + proxy.port() + "/xxl-job-admin",
                System.getenv("WP008_F_XXL_USER"), System.getenv("WP008_F_XXL_PASS"));
        ReflectionTestUtils.setField(serviceImpl, "xxlJobAdminClient", proxied);
    }

    /** 每用例后恢复真实客户端并关闭代理。 */
    @AfterEach
    void restore() {
        if (serviceImpl != null && originalClient != null) {
            ReflectionTestUtils.setField(serviceImpl, "xxlJobAdminClient", originalClient);
        }
        if (proxy != null) {
            try {
                proxy.close();
            } catch (Exception ignored) {
                // ok
            }
        }
        if (created[0] != null) {
            try {
                userJobService.deleteJob(created[0].getId());
            } catch (Exception ignored) {
                // best-effort
            }
            created[0] = null;
        }
        // 按精确名清理本用例自有残留（本地行 + 远端孤儿），确保本包无未解释孤儿且不跨用例污染
        cleanResidue();
    }

    /** 以精确任务名清理本地行与远端 xxl_job_info 孤儿（F 隔离测试数据）。 */
    private void cleanResidue() {
        if (lastJobName == null) {
            return;
        }
        try {
            jdbc.update("DELETE FROM sys_user_job WHERE job_name=?", lastJobName);
        } catch (Exception ignored) {
            // best-effort
        }
        try {
            adminJdbc().update("DELETE FROM xxl_job.xxl_job_info WHERE job_desc=?", lastJobName);
        } catch (Exception ignored) {
            // best-effort
        }
        lastJobName = null;
    }

    /** FJ05-1 注册前明确失败（admin 不可达）：本地不留孤儿、返回明确错误。 */
    @Test
    @DisplayName("FJ05-1 注册前失败无孤儿")
    void registerFailsBeforeCommit() throws Exception {
        routeThroughProxy(FaultProxy.Mode.UNREACHABLE);
        CreateUserJobRequest jr = req("FJ05-1");
        assertThatThrownBy(() -> userJobService.createJob(1L, jr, "f1_sales"))
                .isInstanceOf(RuntimeException.class);
        assertThat(localRowsNamed(jr.getJobName())).as("本地 sys_user_job 不得为本任务留孤儿").isZero();
    }

    /** FJ05-0 无故障透传对照：代理 PASS_THROUGH 时 createJob 真实命中 /jobinfo/insert 且成功，先证明代理不破坏正常链。 */
    @Test
    @DisplayName("FJ05-0 无故障透传对照")
    void passThroughControlReachesInsert() throws Exception {
        routeThroughProxy(FaultProxy.Mode.PASS_THROUGH);
        CreateUserJobRequest jr = req("FJ05-0");
        SysUserJob job = userJobService.createJob(1L, jr, "f1_sales");
        created[0] = job;
        assertThat(job.getXxlJobId()).as("透传对照应真实注册成功并回填 XXL ID").isNotNull();
        assertThat(proxy.seenPaths()).as("RF04 对照：透传必须真实到达 /jobinfo/insert").contains("/xxl-job-admin/jobinfo/insert");
    }

    /** FJ05-2 注册已提交后丢响应：故障必须命中 /jobinfo/insert（非登录 EOF），结果可解释/可恢复，不静默产生 XXL 孤儿。 */
    @Test
    @DisplayName("FJ05-2 提交后丢响应定点恢复")
    void lostAfterRegisterRecovers() throws Exception {
        routeThroughProxy(FaultProxy.Mode.DROP_INSERT_ONCE);
        CreateUserJobRequest jr = req("FJ05-2");
        try {
            userJobService.createJob(1L, jr, "f1_sales");
        } catch (RuntimeException expected) {
            // 调用方看到失败
        }
        // RF04 关键断言：故障确实打到目标写接口 /jobinfo/insert（远端已提交），而非登录阶段 EOF
        assertThat(proxy.seenPaths()).as("RF04：丢响应用例必须命中 /jobinfo/insert，认证失败不能代替注册失败")
                .contains("/xxl-job-admin/jobinfo/insert");
        assertThat(proxy.lastResponseLength()).as("远端 insert 已提交并返回响应（长度>0），代理仅对客户端丢弃").isGreaterThan(0);
        // 查询远端：修复后实现应在回滚前按可信关联反查并定点清理，或保留可追踪关联；此处验证二者之一成立
        List<Map<String, Object>> remote = adminJdbc().queryForList(
                "SELECT id,job_desc FROM xxl_job.xxl_job_info WHERE job_group=(SELECT id FROM xxl_job.xxl_job_group WHERE app_name='omni-base') AND job_desc LIKE ?",
                jr.getJobName() + "%");
        long local = localRowsNamed(jr.getJobName());
        boolean consistent = (remote.isEmpty() && local == 0)          // 已清理远端 → 无孤儿
                || (!remote.isEmpty() && local == 1);                   // 或保留关联（本地行存在可恢复）
        assertThat(consistent)
                .as("丢响应后不得出现『本地无记录但远端有孤儿』: remote=%s local=%s", remote, local)
                .isTrue();
    }

    /** FJ06-1 update 外部业务错误（HTTP200+code500）：不得返回“两边一致成功”；本地 cron 不得先于远端静默提交。 */
    @Test
    @DisplayName("FJ06-1 update外部业务错误不发散")
    void updateBusinessErrorNotSilentlyDivergent() throws Exception {
        SysUserJob job = userJobService.createJob(1L, req("FJ06-1"), "f1_sales");
        created[0] = job;
        routeThroughProxy(FaultProxy.Mode.BUSINESS_ERROR);
        com.omni.base.dto.UpdateUserJobRequest up = new com.omni.base.dto.UpdateUserJobRequest();
        up.setJobName(job.getJobName());
        up.setCronExpression("0 15 4 1 JAN ?");
        up.setJobParams("{}");
        assertThatThrownBy(() -> userJobService.updateJob(job.getId(), up, "f1_sales"))
                .as("外部拒绝时不得静默报成功")
                .isInstanceOf(RuntimeException.class);
        String localCron = jdbc.queryForObject("SELECT cron_expression FROM sys_user_job WHERE id=?",
                String.class, job.getId());
        String remoteCron = adminJdbc().queryForObject("SELECT schedule_conf FROM xxl_job.xxl_job_info WHERE id=?",
                String.class, job.getXxlJobId());
        assertThat(localCron).as("本地/远端不得发散: local=%s remote=%s", localCron, remoteCron)
                .isEqualTo(remoteCron);
    }

    /** FJ06-2 toggle 外部不可达：DB status 不得先提交造成与 XXL triggerStatus 发散。 */
    @Test
    @DisplayName("FJ06-2 toggle不可达不发散")
    void toggleUnreachableNoDivergence() throws Exception {
        SysUserJob job = userJobService.createJob(1L, req("FJ06-2"), "f1_sales");
        created[0] = job;
        routeThroughProxy(FaultProxy.Mode.UNREACHABLE);
        assertThatThrownBy(() -> userJobService.toggleStatus(job.getId(), 0))
                .isInstanceOf(RuntimeException.class);
        Integer localStatus = jdbc.queryForObject("SELECT status FROM sys_user_job WHERE id=?",
                Integer.class, job.getId());
        Integer remote = adminJdbc().queryForObject("SELECT trigger_status FROM xxl_job.xxl_job_info WHERE id=?",
                Integer.class, job.getXxlJobId());
        assertThat(localStatus).as("本地=%s 远端=%s 必须一致可解释", localStatus, remote).isEqualTo(remote);
    }

    /** FJ07-1 delete 外部失败：不得丢失 XXL 关联；本地行或保留可恢复，或两侧同时消失。 */
    @Test
    @DisplayName("FJ07-1 delete失败不丢关联")
    void deleteFailureKeepsRecoverableAssociation() throws Exception {
        SysUserJob job = userJobService.createJob(1L, req("FJ07-1"), "f1_sales");
        created[0] = job;
        routeThroughProxy(FaultProxy.Mode.UNREACHABLE);
        assertThatThrownBy(() -> userJobService.deleteJob(job.getId()))
                .isInstanceOf(RuntimeException.class);
        Integer local = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_job WHERE id=?",
                Integer.class, job.getId());
        Integer remote = adminJdbc().queryForObject("SELECT COUNT(*) FROM xxl_job.xxl_job_info WHERE id=?",
                Integer.class, job.getXxlJobId());
        assertThat(local).as("远端未删成功时本地不得先删（丢关联）").isEqualTo(1);
        assertThat(remote).isEqualTo(1);
        // 恢复：接回真实客户端后正式删除，两侧同时消失
        restoreClient();
        userJobService.deleteJob(job.getId());
        created[0] = null;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_job WHERE id=?", Integer.class, job.getId())).isZero();
        assertThat(adminJdbc().queryForObject("SELECT COUNT(*) FROM xxl_job.xxl_job_info WHERE id=?", Integer.class, job.getXxlJobId())).isZero();
    }

    private void restoreClient() {
        if (serviceImpl != null && originalClient != null) {
            ReflectionTestUtils.setField(serviceImpl, "xxlJobAdminClient", originalClient);
        }
    }

    private CreateUserJobRequest req(String tag) {
        CreateUserJobRequest jr = new CreateUserJobRequest();
        jr.setJobName("WP008F-" + tag + "-" + UUID.randomUUID().toString().substring(0, 6));
        jr.setJobType("Task-00001");
        jr.setCronExpression("0 0 4 1 JAN ?");
        jr.setJobParams("{}");
        this.lastJobName = jr.getJobName();
        return jr;
    }

    /** 按精确任务名统计本地 sys_user_job 行数（作用域限定到本用例，避免跨用例污染）。 */
    private long localRowsNamed(String name) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_job WHERE job_name=?", Long.class, name);
        return n == null ? 0 : n;
    }
}
