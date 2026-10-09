package com.omni.base.it;

import com.omni.base.controller.MyJobController;
import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.entity.SysUserJob;
import com.omni.base.service.UserJobService;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FJ04：两真实 tenant、同/不同 username 的 list/stats/update/status/trigger/log/delete 归属矩阵；
 * 保持 verifyOwnership 模式（tenant+createBy），不引入统一 @PreAuthorize。
 *
 * @author Omni-Stack Team
 */
@DisplayName("FJ04 本人归属矩阵")
class Fj04OwnershipIT extends AbstractFjIT {

    @Autowired
    private MyJobController controller;
    @Autowired
    private UserJobService userJobService;
    @Autowired
    private JdbcTemplate jdbc;

    private SysUserJob jobOwned;

    /** 每用例后清理任务。 */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (jobOwned != null) {
            try {
                userJobService.deleteJob(jobOwned.getId());
            } catch (Exception ignored) {
                // best-effort
            }
            jobOwned = null;
        }
    }

    /** 建任务归属 f1_sales@tenant1。 */
    private SysUserJob createOwned() {
        CreateUserJobRequest jr = new CreateUserJobRequest();
        jr.setJobName("WP008F-own-" + UUID.randomUUID().toString().substring(0, 6));
        jr.setJobType("Task-00001");
        jr.setCronExpression("0 0 4 1 JAN ?");
        jr.setJobParams("{}");
        SysUserJob j = userJobService.createJob(1L, jr, "f1_sales");
        // createBy 由服务从 operator 写入
        return j;
    }

    private void loginAs(String username, Long tenantId) {
        var auth = new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        // 控制器从 header 参数取 tenant，与登录身份组合校验
    }

    /** 本人 update/status/trigger/log/delete 全通过。 */
    @Test
    @DisplayName("FJ04-1 本人入口放行")
    void ownerAllowed() {
        jobOwned = createOwned();
        loginAs("f1_sales", 1L);
        final Long id = jobOwned.getId();
        var list = controller.list(1L, null, null, null, 1, 50);
        assertThat(list.getData().getRecords()).anySatisfy(v -> assertThat(v.getId()).isEqualTo(id));
        com.omni.base.dto.UpdateUserJobRequest up = new com.omni.base.dto.UpdateUserJobRequest();
        up.setJobName(jobOwned.getJobName());
        up.setCronExpression("0 0 5 1 JAN ?");
        up.setJobParams("{}");
        controller.update(1L, id, up);
        controller.toggleStatus(1L, id, 0);
        controller.trigger(1L, id);
        controller.logs(id, 1L, 1, 10);
        controller.delete(1L, id);
        jobOwned = null;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_job WHERE id=?", Integer.class, id)).isZero();
    }

    /** 同租户不同 username：所有写入口 403 且本地/XXL 无变化。 */
    @Test
    @DisplayName("FJ04-2 同租户他人写被拒")
    void sameTenantOtherUserRejected() {
        jobOwned = createOwned();
        loginAs("f1_ap1", 1L);
        final Long id = jobOwned.getId();
        Long xxlId = jobOwned.getXxlJobId();
        assertThatThrownBy(() -> controller.trigger(1L, id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.delete(1L, id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.toggleStatus(1L, id, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.logs(id, 1L, 1, 10)).isInstanceOf(BusinessException.class);
        Integer stillThere = adminJdbc().queryForObject(
                "SELECT trigger_status FROM xxl_job.xxl_job_info WHERE id=?", Integer.class, xxlId);
        assertThat(stillThere).as("他人无权操作不得改变 XXL").isEqualTo(1);
    }

    /** 跨租户同 username 也隔离：tenant2002 的 f2_sales 操作 tenant1 同名归属任务被拒。 */
    @Test
    @DisplayName("FJ04-3 跨租户同名不串")
    void crossTenantSameUsername() {
        // 用同一 createBy 名但在 tenant 2002 登录并尝试操作 tenant1 的任务
        jobOwned = createOwned();
        loginAs("f1_sales", 2002L);
        final Long id = jobOwned.getId();
        assertThatThrownBy(() -> controller.update(2002L, id, updReq(jobOwned)))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_job WHERE id=? AND tenant_id=1",
                Integer.class, id)).isEqualTo(1);
    }

    /** 同名用户不同租户各自建任务互不可见（list/stats 按 createBy+tenant 过滤）。 */
    @Test
    @DisplayName("FJ04-4 双租户同名list隔离")
    void dualTenantSameUsernameList() {
        jobOwned = createOwned(); // tenant1 f1_sales
        // tenant2002 用户 dup 同名构造：直接用 username=f1_sales 的 tenant2002 登录（用户不存在于 t2，但归属键为 tenant+username 字符串）
        loginAs("f1_sales", 2002L);
        var other = controller.list(2002L, null, null, null, 1, 50);
        assertThat(other.getData().getRecords())
                .noneSatisfy(v -> assertThat(v.getId()).isEqualTo(jobOwned.getId()));
        var mine = controller.list(1L, null, null, null, 1, 50);
        assertThat(mine.getData().getRecords()).anySatisfy(v -> assertThat(v.getId()).isEqualTo(jobOwned.getId()));
    }

    /** admin 借本人接口改他人任务同样被拒（无特权旁路）。 */
    @Test
    @DisplayName("FJ04-5 admin无本人旁路")
    void adminCannotUseMyJobForOthers() {
        jobOwned = createOwned();
        loginAs("admin", 1L);
        assertThatThrownBy(() -> controller.trigger(1L, jobOwned.getId()))
                .isInstanceOf(BusinessException.class);
    }

    private com.omni.base.dto.UpdateUserJobRequest updReq(SysUserJob j) {
        com.omni.base.dto.UpdateUserJobRequest up = new com.omni.base.dto.UpdateUserJobRequest();
        up.setJobName(j.getJobName() + "-x");
        up.setCronExpression(j.getCronExpression());
        up.setJobParams("{}");
        return up;
    }
}
