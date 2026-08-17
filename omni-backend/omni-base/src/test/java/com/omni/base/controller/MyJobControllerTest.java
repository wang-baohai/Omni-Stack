package com.omni.base.controller;

import com.omni.base.entity.SysUserJob;
import com.omni.base.service.UserJobLogService;
import com.omni.base.service.UserJobService;
import com.omni.base.service.UserJobTypeService;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 我的任务逐行归属校验测试。 */
@ExtendWith(MockitoExtension.class)
class MyJobControllerTest {

    @Mock private UserJobService userJobService;
    @Mock private UserJobLogService userJobLogService;
    @Mock private UserJobTypeService userJobTypeService;
    private MyJobController controller;

    /** 初始化认证上下文。 */
    @BeforeEach
    void setUp() {
        controller = new MyJobController(userJobService, userJobLogService, userJobTypeService);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", java.util.List.of()));
    }

    /** 清理认证上下文。 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 同名用户也不能跨租户触发任务。 */
    @Test
    void shouldRejectSameUsernameFromDifferentTenant() {
        SysUserJob job = new SysUserJob();
        job.setId(9L);
        job.setTenantId(2L);
        job.setCreateBy("alice");
        when(userJobService.getJobById(9L)).thenReturn(job);

        assertThatThrownBy(() -> controller.trigger(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);

        verify(userJobService, never()).triggerNow(9L);
    }

    /** 同租户但非创建人不能删除任务。 */
    @Test
    void shouldRejectDifferentOwner() {
        SysUserJob job = new SysUserJob();
        job.setId(10L);
        job.setTenantId(1L);
        job.setCreateBy("bob");
        when(userJobService.getJobById(10L)).thenReturn(job);

        assertThatThrownBy(() -> controller.delete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);

        verify(userJobService, never()).deleteJob(10L);
    }
}
